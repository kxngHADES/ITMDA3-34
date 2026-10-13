import asyncio
import datetime as dt

import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from core.auth import build_caller, get_caller
from core.clock import service_date
from main import app
from routes.v1 import live
from schemas import (
    DEFAULT_QUEUE_CONFIG,
    AppError,
    CallNextRequest,
    ConfirmPriorityRequest,
    ErrorCode,
    JoinQueueRequest,
    PauseQueueRequest,
    Role,
    WalkInRequest,
)
from schemas.db.documents import ClinicDoc, QueueConfigDoc, ServicePointDoc
from services import queue_service as qs

CLINIC = ClinicDoc(id="c1", name="Clinic", address="1 Main Rd", opening_hours=[])
SERVICE_POINT = ServicePointDoc(
    id="sp1",
    clinic_id="c1",
    name="General",
    icon="stethoscope",
    display_prefix="A",
    config=QueueConfigDoc.model_validate(DEFAULT_QUEUE_CONFIG.model_dump()),
)
QUEUE_ID = qs.queue_id_for("c1", "sp1", service_date())


def run(coro):
    return asyncio.run(coro)


@pytest.fixture
def store(monkeypatch):
    # The real service code runs against one in-memory queue document instead of Firestore and Redis.
    holder = {"data": None}

    def create_sync(queue_id, clinic, service_point, day, fn):
        if holder["data"] is None:
            holder["data"] = qs._new_queue_data(queue_id, clinic, service_point, day)
        holder["data"]["queue_id"] = queue_id
        return fn(holder["data"]), holder["data"]

    def mutate_sync(queue_id, fn):
        if holder["data"] is None:
            raise AppError(ErrorCode.NOT_FOUND)
        holder["data"]["queue_id"] = queue_id
        return fn(holder["data"]), holder["data"]

    async def open_service_point(clinic_id, service_point_id):
        return CLINIC, SERVICE_POINT

    async def after_change(queue_id, data):
        return None

    monkeypatch.setattr(qs, "_create_sync", create_sync)
    monkeypatch.setattr(qs, "_mutate_sync", mutate_sync)
    monkeypatch.setattr(qs, "_open_service_point", open_service_point)
    monkeypatch.setattr(qs, "_after_change", after_change)
    monkeypatch.setattr(qs, "clinic_is_open", lambda clinic, now: True)
    monkeypatch.setattr(qs, "_load_queue_sync", lambda queue_id: holder["data"])
    return holder


def join(uid, **fields):
    ticket, _ = run(qs.join_queue(QUEUE_ID, uid, JoinQueueRequest(language="en", **fields)))
    return ticket


def call():
    return run(qs.call_next(QUEUE_ID, "staff1", None))


def no_show(ticket_id):
    return run(qs.mark_no_show(ticket_id))


def waiting_order(store):
    return [e.display_number for e in qs.build_entries(store["data"])]


class TestFifo:
    def test_tickets_are_called_in_the_order_they_joined(self, store):
        first, second, third = join("u1"), join("u2"), join("u3")

        assert [t.display_number for t in (first, second, third)] == ["A-001", "A-002", "A-003"]
        assert [call().id for _ in range(3)] == [first.id, second.id, third.id]

    def test_calling_sets_now_serving_and_counter(self, store):
        join("u1")

        called = run(qs.call_next(QUEUE_ID, "staff1", CallNextRequest(counter_label="Room 3")))

        assert called.status == "called"
        assert called.counter_label == "Room 3"
        assert store["data"]["now_serving"] == "A-001"

    def test_empty_queue(self, store):
        join("u1")
        call()

        with pytest.raises(AppError) as exc:
            call()

        assert exc.value.code is ErrorCode.QUEUE_EMPTY

    def test_positions_and_wait_follow_the_order(self, store):
        join("u1")
        second = join("u2")

        assert second.position == 2
        assert second.people_ahead == 1


class TestWaitTimeEma:
    @pytest.fixture(autouse=True)
    def saved(self, store, monkeypatch):
        self.saves = []
        monkeypatch.setattr(qs, "_save_base_time_sync", lambda sp, seconds, samples: self.saves.append((sp, seconds, samples)))

    def finish(self, store, seconds):
        # Backdates the call so the consultation lasted the given number of seconds.
        ticket = call()
        store["data"]["tickets"][ticket.id]["called_at"] -= dt.timedelta(seconds=seconds)
        return run(qs.complete_ticket(ticket.id))

    def test_cold_start_uses_the_default_base_time(self, store):
        first = join("u1")

        default_minutes = DEFAULT_QUEUE_CONFIG.default_base_time_seconds / 60
        assert first.estimated_wait.cold_start is True
        assert first.estimated_wait.min_minutes == int(default_minutes * 0.8)

    def test_completing_records_the_consultation_and_starts_the_average(self, store):
        join("u1")

        done = self.finish(store, 600)

        assert done.status == "served"
        assert done.consultation_seconds == 600
        assert store["data"]["base_service_seconds"] == 600
        assert self.saves == [("sp1", 600, 1)]

    def test_average_is_an_ema_with_alpha_point_two(self, store):
        for uid in ("u1", "u2"):
            join(uid)
        self.finish(store, 600)
        self.finish(store, 1000)

        assert store["data"]["base_service_seconds"] == pytest.approx(0.2 * 1000 + 0.8 * 600)

    def test_default_is_kept_until_enough_samples_then_the_average_is_used(self, store):
        for i in range(6):
            join(f"u{i}")
        for _ in range(4):
            self.finish(store, 600)
        assert qs.base_time(store["data"])[1] is True

        self.finish(store, 600)

        assert qs.base_time(store["data"]) == (600.0, False)
        # u5 is now first in line: ceil(1 / 1) x 600s = 10 minutes, shown as 8-12
        ticket = run(qs.get_ticket(next(reversed(store["data"]["tickets"])), "u5"))
        assert (ticket.estimated_wait.min_minutes, ticket.estimated_wait.max_minutes) == (8, 12)
        assert ticket.estimated_wait.cold_start is False

    def test_only_a_called_ticket_can_be_completed(self, store):
        waiting = join("u1")

        with pytest.raises(AppError) as exc:
            run(qs.complete_ticket(waiting.id))

        assert exc.value.code is ErrorCode.INVALID_TRANSITION


class TestPriorityLane:
    def test_confirmed_walk_in_goes_ahead_of_standard_tickets(self, store):
        standard = join("u1")
        walk_in = run(
            qs.add_walk_in(QUEUE_ID, "staff1", WalkInRequest(language="en", priority_category="pregnant"))
        )

        assert walk_in.channel == "staff_assisted"
        assert walk_in.priority.staff_confirmed is True
        assert walk_in.position == 1
        assert call().id == walk_in.id
        assert call().id == standard.id

    def test_the_jump_is_bounded_by_the_category_weight(self, store):
        for n in range(6):
            join(f"u{n}")

        elderly = run(qs.add_walk_in(QUEUE_ID, "s", WalkInRequest(language="en", priority_category="elderly")))
        urgent = run(qs.add_walk_in(QUEUE_ID, "s", WalkInRequest(language="en", priority_category="urgent")))

        # Elderly is worth 3 places, so it moves from 7th to 4th. Urgent is worth 6, from 8th to 2nd.
        assert elderly.position == 4
        assert urgent.position == 2
        assert [e.display_number for e in qs.build_entries(store["data"])][:3] == ["A-001", "A-008", "A-002"]

    def test_a_request_alone_does_not_move_the_ticket(self, store):
        first = join("u1")
        requester = join("u2", priority_request="elderly")

        assert requester.priority.staff_confirmed is False
        assert requester.position == 2
        assert call().id == first.id

    def test_confirming_moves_the_ticket_up_and_rejecting_puts_it_back(self, store):
        first = join("u1")
        requester = join("u2", priority_request="elderly")

        confirmed = run(qs.confirm_priority(requester.id, "staff1", ConfirmPriorityRequest(confirmed=True)))
        assert confirmed.position == 1
        assert confirmed.priority.confirmed_by == "staff1"
        assert waiting_order(store) == ["A-002", "A-001"]

        rejected = run(qs.confirm_priority(requester.id, "staff1", ConfirmPriorityRequest(confirmed=False)))
        assert rejected.position == 2
        assert waiting_order(store) == [first.display_number, requester.display_number]

    def test_staff_can_correct_the_category(self, store):
        ticket = join("u1", priority_request="elderly")

        updated = run(
            qs.confirm_priority(ticket.id, "s", ConfirmPriorityRequest(confirmed=True, category="pregnant"))
        )

        assert updated.priority.requested_category == "pregnant"

    def test_confirming_without_any_category_is_rejected(self, store):
        ticket = join("u1")

        with pytest.raises(AppError) as exc:
            run(qs.confirm_priority(ticket.id, "s", ConfirmPriorityRequest(confirmed=True)))

        assert exc.value.code is ErrorCode.VALIDATION_FAILED

    def test_only_waiting_tickets_can_change_priority(self, store):
        ticket = join("u1", priority_request="elderly")
        call()

        with pytest.raises(AppError) as exc:
            run(qs.confirm_priority(ticket.id, "s", ConfirmPriorityRequest(confirmed=True)))

        assert exc.value.code is ErrorCode.INVALID_TRANSITION


class TestNoShow:
    def test_first_no_shows_go_back_in_the_queue_marked_late(self, store):
        first, second = join("u1"), join("u2")
        assert call().id == first.id

        result = no_show(first.id)

        assert result.status == "waiting"
        assert result.late_flag is True
        assert result.no_show_count == 1
        assert result.position == 2
        assert waiting_order(store) == [second.display_number, first.display_number]

    def test_a_late_ticket_is_pushed_back_by_the_penalty(self, store):
        tickets = [join(f"u{n}") for n in range(7)]
        call()

        no_show(tickets[0].id)

        # Penalty is 5, so A-001 sits behind A-002 to A-006 and ahead of only A-007.
        assert waiting_order(store) == ["A-002", "A-003", "A-004", "A-005", "A-006", "A-001", "A-007"]

    def test_removed_on_the_fourth_no_show(self, store):
        ticket = join("u1")

        counts = []
        for _ in range(qs.MAX_NO_SHOWS):
            assert call().id == ticket.id
            result = no_show(ticket.id)
            counts.append((result.status.value, result.no_show_count))

        assert counts == [("waiting", 1), ("waiting", 2), ("waiting", 3), ("no_show", 4)]
        assert ticket.display_number not in waiting_order(store)
        assert result.closed_at is not None

    def test_only_called_tickets_can_be_no_shows(self, store):
        ticket = join("u1")

        with pytest.raises(AppError) as exc:
            no_show(ticket.id)

        assert exc.value.code is ErrorCode.INVALID_TRANSITION

    def test_unknown_ticket(self, store):
        join("u1")

        with pytest.raises(AppError) as exc:
            no_show(f"{QUEUE_ID}~missing")

        assert exc.value.code is ErrorCode.NOT_FOUND

    def test_new_tickets_do_not_reuse_numbers_after_a_requeue(self, store):
        first = join("u1")
        call()
        no_show(first.id)

        later = join("u2")

        assert later.display_number == "A-002"
        assert waiting_order(store) == [later.display_number, first.display_number]


class TestStarvationGuard:
    def test_a_long_waiting_ticket_cannot_be_passed_by_priority(self, store):
        join("u1")
        for n in range(2, 4):
            join(f"u{n}")
        # The first ticket has waited past the 100 minute threshold.
        store["data"]["tickets"][next(iter(store["data"]["tickets"]))]["joined_at"] = dt.datetime.now(
            dt.UTC
        ) - dt.timedelta(minutes=101)

        urgent = run(qs.add_walk_in(QUEUE_ID, "s", WalkInRequest(language="en", priority_category="urgent")))

        assert urgent.position == 2
        entries = qs.build_entries(store["data"])
        assert [(e.display_number, e.protected) for e in entries][:2] == [("A-001", True), ("A-004", False)]


class TestPause:
    def test_paused_queue_blocks_joins_and_calls(self, store):
        join("u1")

        paused = run(qs.set_paused(QUEUE_ID, True, PauseQueueRequest(reason="Tea break")))
        assert paused.status == "paused"
        assert paused.pause_reason == "Tea break"

        with pytest.raises(AppError) as exc:
            call()
        assert exc.value.code is ErrorCode.QUEUE_PAUSED
        with pytest.raises(AppError) as exc:
            join("u2")
        assert exc.value.code is ErrorCode.QUEUE_PAUSED

    def test_resume_reopens_the_queue(self, store):
        join("u1")
        run(qs.set_paused(QUEUE_ID, True))

        resumed = run(qs.set_paused(QUEUE_ID, False))

        assert resumed.status == "open"
        assert resumed.pause_reason is None
        assert call().display_number == "A-001"

    def test_closed_queue_cannot_be_paused(self, store):
        join("u1")
        store["data"]["status"] = "closed"

        with pytest.raises(AppError) as exc:
            run(qs.set_paused(QUEUE_ID, True))

        assert exc.value.code is ErrorCode.QUEUE_CLOSED

    def test_walk_ins_still_allowed_while_paused(self, store):
        join("u1")
        run(qs.set_paused(QUEUE_ID, True))

        ticket = run(qs.add_walk_in(QUEUE_ID, "s", WalkInRequest(language="en")))

        assert ticket.display_number == "A-002"


class TestClinicScope:
    def test_staff_of_another_clinic_cannot_act(self, store):
        join("u1")

        with pytest.raises(AppError) as exc:
            run(qs.call_next(QUEUE_ID, "staff1", None, clinic_scope="other"))

        assert exc.value.code is ErrorCode.NOT_FOUND

    def test_staff_of_the_same_clinic_can(self, store):
        join("u1")

        assert run(qs.call_next(QUEUE_ID, "staff1", None, clinic_scope="c1")).status == "called"


@pytest.fixture
def client():
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c
    app.dependency_overrides.clear()


def sign_in(role):
    app.dependency_overrides[get_caller] = lambda: build_caller("uid_1", role, clinic_id="c1")


KEY = {"Idempotency-Key": "3f1c2a9e-6b7d-4e0a-9f2b-1c8d5e7a4b60"}


class TestStaffRoutes:
    @pytest.mark.parametrize(
        ("role", "method", "path", "expected"),
        [
            (Role.patient, "post", "/v1/queues/q/call-next", 403),
            (Role.it_admin, "post", "/v1/queues/q/call-next", 403),
            (Role.nurse, "post", "/v1/queues/q/pause", 403),
            (Role.nurse, "post", "/v1/queues/q/resume", 403),
            (Role.nurse, "post", "/v1/queues/q/walk-ins", 403),
            (Role.patient, "post", "/v1/tickets/t/no-show", 403),
            (Role.patient, "post", "/v1/tickets/t/confirm-priority", 403),
        ],
    )
    def test_roles(self, client, role, method, path, expected):
        sign_in(role)

        response = getattr(client, method)(path, json={"language": "en"}, headers=KEY)

        assert response.status_code == expected

    def test_walk_in_replays_with_the_same_key(self, client, store, monkeypatch):
        from services import idempotency_service

        seen = {}

        async def find(uid, key, req_hash):
            return seen.get((uid, key))

        async def save(uid, key, req_hash, status_code, body):
            seen[(uid, key)] = idempotency_service.IdempotencyRecord(
                uid=uid, request_hash=req_hash, status_code=status_code, response_body=body
            )

        monkeypatch.setattr(idempotency_service, "find", find)
        monkeypatch.setattr(idempotency_service, "save", save)
        sign_in(Role.receptionist)
        body = {"language": "en", "priority_category": "pregnant"}

        first = client.post(f"/v1/queues/{QUEUE_ID}/walk-ins", json=body, headers=KEY)
        second = client.post(f"/v1/queues/{QUEUE_ID}/walk-ins", json=body, headers=KEY)

        assert first.status_code == 201
        assert second.status_code == 200
        assert second.json()["id"] == first.json()["id"]
        assert store["data"]["ticket_count"] == 1

    def test_call_next_without_a_body(self, client, store):
        join("u1")
        sign_in(Role.nurse)

        response = client.post(f"/v1/queues/{QUEUE_ID}/call-next")

        assert response.status_code == 200
        assert response.json()["status"] == "called"

    def test_no_show_endpoint_reports_the_count(self, client, store):
        ticket = join("u1")
        call()
        sign_in(Role.nurse)

        response = client.post(f"/v1/tickets/{ticket.id}/no-show")

        assert response.status_code == 200
        assert response.json()["no_show_count"] == 1
        assert response.json()["status"] == "waiting"

    def test_pause_and_resume_endpoints(self, client, store):
        join("u1")
        sign_in(Role.receptionist)

        paused = client.post(f"/v1/queues/{QUEUE_ID}/pause", json={"reason": "Break"})
        resumed = client.post(f"/v1/queues/{QUEUE_ID}/resume")

        assert paused.json()["status"] == "paused"
        assert resumed.json()["status"] == "open"

    def test_confirm_priority_endpoint(self, client, store):
        join("u1")
        ticket = join("u2", priority_request="pregnant")
        sign_in(Role.nurse)

        response = client.post(f"/v1/tickets/{ticket.id}/confirm-priority", json={"confirmed": True})

        assert response.status_code == 200
        assert response.json()["position"] == 1


class TestLiveSocket:
    def staff(self, monkeypatch, role=Role.nurse):
        async def verify(token):
            return build_caller("uid_1", role, clinic_id="c1")

        monkeypatch.setattr(live, "verify_token", verify)

    def test_no_token_is_closed(self, client):
        with pytest.raises(WebSocketDisconnect) as exc:
            with client.websocket_connect(f"/v1/queues/{QUEUE_ID}/live"):
                pass

        assert exc.value.code == live.CLOSE_UNAUTHENTICATED

    def test_patient_is_closed(self, client, monkeypatch):
        self.staff(monkeypatch, Role.patient)

        with pytest.raises(WebSocketDisconnect) as exc:
            with client.websocket_connect(f"/v1/queues/{QUEUE_ID}/live?token=x"):
                pass

        assert exc.value.code == live.CLOSE_FORBIDDEN

    def test_unknown_queue_is_closed(self, client, monkeypatch):
        self.staff(monkeypatch)

        with pytest.raises(WebSocketDisconnect) as exc:
            with client.websocket_connect("/v1/queues/nonsense/live?token=x"):
                pass

        assert exc.value.code == live.CLOSE_NOT_FOUND

    def test_sends_the_snapshot_with_the_status_then_each_change(self, client, store, monkeypatch):
        self.staff(monkeypatch)
        join("u1")
        async def changes(queue_id):
            yield
            # A second patient joins while the staff screen is open.
            qs._add_ticket(
                store["data"], owner_uid="u2", channel="app", display_name=None, phone=None,
                language="en", notification_channel=None, category=None,
            )
            yield

        monkeypatch.setattr(qs, "changes", changes)

        with client.websocket_connect(f"/v1/queues/{QUEUE_ID}/live?token=x") as ws:
            first = ws.receive_json()
            assert first["type"] == "queue"
            assert first["status"] == "open"
            assert first["waiting_count"] == 1
            # Staff see names and categories, unlike patients.
            assert "staff_confirmed" in first["queue"]["entries"][0]

            second = ws.receive_json()

        assert second["waiting_count"] == 2

    def test_header_token_is_accepted(self, client, store, monkeypatch):
        self.staff(monkeypatch)
        join("u1")

        async def changes(queue_id):
            yield

        monkeypatch.setattr(qs, "changes", changes)

        with client.websocket_connect(
            f"/v1/queues/{QUEUE_ID}/live", headers={"Authorization": "Bearer x"}
        ) as ws:
            assert ws.receive_json()["status"] == "open"
