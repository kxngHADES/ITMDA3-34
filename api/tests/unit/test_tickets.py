import datetime as dt
import string

import pytest
from fastapi.testclient import TestClient

from core import clock
from core.auth import build_caller, get_caller
from main import app
from schemas import AppError, ErrorCode, Role, Ticket
from services import clinic_service, idempotency_service, queue_service

KEY = "3f1c2a9e-6b7d-4e0a-9f2b-1c8d5e7a4b60"
OTHER_KEY = "9a1c2a9e-6b7d-4e0a-9f2b-1c8d5e7a4b61"
QUEUE_ID = "2026-10-12-clinic1-sp1"
TICKET_ID = f"{QUEUE_ID}~abc123"


def ticket(**overrides) -> Ticket:
    now = dt.datetime.now(dt.UTC)
    base = dict(
        id=TICKET_ID,
        display_number="A-001",
        sequence_no=1,
        queue_id=QUEUE_ID,
        service_point_id="general",
        clinic_id="clinic1",
        status="waiting",
        channel="app",
        position=1,
        people_ahead=0,
        priority={"requested_category": "none", "staff_confirmed": False},
        late_flag=False,
        created_at=now,
        updated_at=now,
    )
    return Ticket.model_validate(base | overrides)


@pytest.fixture
def client():
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c
    app.dependency_overrides.clear()


def sign_in(role=Role.patient, uid="uid_1", clinic_id=None):
    app.dependency_overrides[get_caller] = lambda: build_caller(uid, role, clinic_id=clinic_id)


@pytest.fixture
def idem(monkeypatch):
    # In-memory stand-in for the Redis idempotency store.
    store = {}

    async def find(uid, key, req_hash):
        record = store.get((uid, key))
        if record is None:
            return None
        if record.request_hash != req_hash:
            raise AppError(ErrorCode.IDEMPOTENCY_KEY_REUSED)
        return record

    async def save(uid, key, req_hash, status_code, body):
        store.setdefault(
            (uid, key),
            idempotency_service.IdempotencyRecord(
                uid=uid, request_hash=req_hash, status_code=status_code, response_body=body
            ),
        )

    monkeypatch.setattr(idempotency_service, "find", find)
    monkeypatch.setattr(idempotency_service, "save", save)
    return store


class TestJoin:
    def test_creates_a_ticket(self, client, idem, monkeypatch):
        calls = []

        async def join(queue_id, uid, body):
            calls.append((queue_id, uid))
            return ticket(), True

        monkeypatch.setattr(queue_service, "join_queue", join)
        sign_in()

        response = client.post(
            f"/v1/queues/{QUEUE_ID}/tickets", json={"language": "en"}, headers={"Idempotency-Key": KEY}
        )

        assert response.status_code == 201
        assert response.headers["Location"] == f"/v1/tickets/{TICKET_ID}"
        assert calls == [(QUEUE_ID, "uid_1")]

    def test_same_key_and_payload_replays_without_creating(self, client, idem, monkeypatch):
        calls = []

        async def join(queue_id, uid, body):
            calls.append(1)
            return ticket(), True

        monkeypatch.setattr(queue_service, "join_queue", join)
        sign_in()
        headers = {"Idempotency-Key": KEY}

        first = client.post(f"/v1/queues/{QUEUE_ID}/tickets", json={"language": "en"}, headers=headers)
        second = client.post(f"/v1/queues/{QUEUE_ID}/tickets", json={"language": "en"}, headers=headers)

        assert first.status_code == 201
        assert second.status_code == 200
        assert second.json()["id"] == first.json()["id"]
        assert len(calls) == 1

    def test_same_key_with_a_different_payload_is_rejected(self, client, idem, monkeypatch):
        async def join(queue_id, uid, body):
            return ticket(), True

        monkeypatch.setattr(queue_service, "join_queue", join)
        sign_in()
        headers = {"Idempotency-Key": KEY}

        client.post(f"/v1/queues/{QUEUE_ID}/tickets", json={"language": "en"}, headers=headers)
        response = client.post(f"/v1/queues/{QUEUE_ID}/tickets", json={"language": "zu"}, headers=headers)

        assert response.status_code == 400
        assert response.json()["error"]["code"] == "IDEMPOTENCY_KEY_REUSED"

    def test_existing_active_ticket_returns_200(self, client, idem, monkeypatch):
        async def join(queue_id, uid, body):
            return ticket(), False

        monkeypatch.setattr(queue_service, "join_queue", join)
        sign_in()

        response = client.post(
            f"/v1/queues/{QUEUE_ID}/tickets", json={"language": "en"}, headers={"Idempotency-Key": OTHER_KEY}
        )

        assert response.status_code == 200
        assert "Location" not in response.headers

    def test_key_must_be_a_uuid(self, client, idem):
        sign_in()

        response = client.post(
            f"/v1/queues/{QUEUE_ID}/tickets", json={"language": "en"}, headers={"Idempotency-Key": "nope"}
        )

        assert response.status_code == 422


class TestStatus:
    def test_returns_the_ticket(self, client, monkeypatch):
        async def get_ticket(ticket_id, uid, clinic_id):
            return ticket()

        monkeypatch.setattr(queue_service, "get_ticket", get_ticket)
        sign_in()

        response = client.get(f"/v1/tickets/{TICKET_ID}")

        assert response.status_code == 200
        assert response.json()["position"] == 1

    def test_it_admin_is_forbidden(self, client):
        sign_in(Role.it_admin)

        assert client.get(f"/v1/tickets/{TICKET_ID}").status_code == 403

    def test_unknown_ticket_is_404(self, client):
        sign_in()

        response = client.get("/v1/tickets/not-a-ticket")

        assert response.status_code == 404
        assert response.json()["error"]["code"] == "NOT_FOUND"


class TestCancel:
    def test_cancels_and_replays(self, client, idem, monkeypatch):
        calls = []

        async def cancel(ticket_id, uid):
            calls.append(ticket_id)
            return ticket(status="cancelled", position=None, people_ahead=None)

        monkeypatch.setattr(queue_service, "cancel_ticket", cancel)
        sign_in()
        headers = {"Idempotency-Key": KEY}

        first = client.post(f"/v1/tickets/{TICKET_ID}/cancel", headers=headers)
        second = client.post(f"/v1/tickets/{TICKET_ID}/cancel", headers=headers)

        assert first.status_code == second.status_code == 200
        assert first.json()["status"] == "cancelled"
        assert len(calls) == 1

    def test_second_cancel_with_a_new_key_is_already_cancelled(self, client, idem, monkeypatch):
        async def cancel(ticket_id, uid):
            raise AppError(ErrorCode.ALREADY_CANCELLED)

        monkeypatch.setattr(queue_service, "cancel_ticket", cancel)
        sign_in()

        response = client.post(f"/v1/tickets/{TICKET_ID}/cancel", headers={"Idempotency-Key": OTHER_KEY})

        assert response.status_code == 400
        assert response.json()["error"]["code"] == "ALREADY_CANCELLED"

    def test_staff_cannot_cancel(self, client, idem):
        sign_in(Role.nurse)

        response = client.post(f"/v1/tickets/{TICKET_ID}/cancel", headers={"Idempotency-Key": KEY})

        assert response.status_code == 403


class TestAdminClinics:
    BODY = {
        "name": "Soshanguve Block B Clinic",
        "address": "12 Main Rd, Soshanguve",
        "opening_hours": [{"day": "mon", "opens": "07:00", "closes": "16:00"}],
    }

    @pytest.mark.parametrize("role", [Role.it_admin, Role.manager])
    def test_creates_a_clinic(self, client, monkeypatch, role):
        from schemas.db.documents import ClinicDoc
        from services import clinic_service

        async def create(body):
            return ClinicDoc(id="clinic1", name=body.name, address=body.address, opening_hours=body.opening_hours)

        monkeypatch.setattr(clinic_service, "create_clinic", create)
        sign_in(role)

        response = client.post("/v1/admin/clinics", json=self.BODY)

        assert response.status_code == 201
        assert response.json()["id"] == "clinic1"
        assert response.json()["name"] == self.BODY["name"]

    @pytest.mark.parametrize("role", [Role.patient, Role.receptionist, Role.nurse])
    def test_other_roles_are_forbidden(self, client, role):
        sign_in(role)

        assert client.post("/v1/admin/clinics", json=self.BODY).status_code == 403

    def test_needs_opening_hours(self, client):
        sign_in(Role.it_admin)

        response = client.post("/v1/admin/clinics", json={**self.BODY, "opening_hours": []})

        assert response.status_code == 422


class TestQueueIds:
    def test_queue_id_is_date_clinic_service_point(self):
        assert queue_service.queue_id_for("clinic1", "sp1", dt.date(2026, 10, 12)) == QUEUE_ID

    def test_queue_id_parses_back(self):
        assert queue_service.parse_queue_id(QUEUE_ID) == (dt.date(2026, 10, 12), "clinic1", "sp1")

    @pytest.mark.parametrize(
        "bad", ["q1", "2026-10-12", "2026-10-12-clinic1", "2026-10-12--sp1", "2026-10-12-c-s-x", "2026-13-40-c-s"]
    )
    def test_malformed_queue_ids_are_not_found(self, bad):
        with pytest.raises(AppError) as exc:
            queue_service.parse_queue_id(bad)

        assert exc.value.code is ErrorCode.NOT_FOUND

    def test_ticket_id_carries_its_queue(self):
        assert queue_service.queue_id_of_ticket(queue_service.new_ticket_id(QUEUE_ID)) == QUEUE_ID

    def test_display_number(self):
        assert queue_service.display_number("A", 14) == "A-014"
        assert queue_service.display_number("B", 42) == "B-042"

    def test_position_follows_sequence_among_waiting_tickets(self):
        data = {
            "tickets": {
                "a": {"status": "cancelled", "sequence_no": 1},
                "b": {"status": "waiting", "sequence_no": 3},
                "c": {"status": "waiting", "sequence_no": 2},
            }
        }

        assert queue_service.position_from_doc(data, "c") == 1
        assert queue_service.position_from_doc(data, "b") == 2
        assert queue_service.position_from_doc(data, "a") is None


class TestSouthAfricanDay:
    def test_day_rolls_over_at_midnight_sast_not_utc(self):
        # 22:30 UTC on the 11th is 00:30 on the 12th in South Africa.
        utc = dt.datetime(2026, 10, 11, 22, 30, tzinfo=dt.UTC)

        assert clock.service_date(utc) == dt.date(2026, 10, 12)


class TestPrefixes:
    def test_first_free_letter(self):
        assert clinic_service.next_free_prefix(set()) == "A"
        assert clinic_service.next_free_prefix({"A", "B"}) == "C"
        assert clinic_service.next_free_prefix({"A", "C"}) == "B"

    def test_moves_to_two_letters_after_z(self):
        taken = set(string.ascii_uppercase)

        assert clinic_service.next_free_prefix(taken) == "AA"


def raw_ticket(seq, status="waiting"):
    now = dt.datetime.now(dt.UTC)
    return {
        "owner_uid": "u", "clinic_id": "clinic1", "service_point_id": "sp1", "queue_id": QUEUE_ID,
        "sequence_no": seq, "display_number": f"A-{seq:03d}", "status": status, "channel": "app",
        "created_at": now, "updated_at": now, "joined_at": now, "display_name": "Thabo",
    }


class TestGetQueue:
    DATA = {
        "tickets": {
            "t1": raw_ticket(1, "cancelled"),
            "t2": raw_ticket(2),
            "t3": raw_ticket(3, "called"),
            "t4": raw_ticket(4),
        }
    }

    def test_entries_show_called_first_then_waiting(self):
        entries = queue_service.build_entries(self.DATA)

        assert [(e.display_number, e.position) for e in entries] == [("A-003", 1), ("A-002", 2), ("A-004", 3)]

    def stored(self, monkeypatch, data=None):
        data = data or {
            "service_point_id": "sp1", "clinic_id": "clinic1", "service_date": "2026-10-12",
            "display_prefix": "A", "status": "open", "opened_by": "system",
            "created_at": dt.datetime.now(dt.UTC), "updated_at": dt.datetime.now(dt.UTC),
            **self.DATA,
        }
        monkeypatch.setattr(queue_service, "_load_queue_sync", lambda queue_id: data)

    def test_patient_view_is_anonymised(self, client, monkeypatch):
        self.stored(monkeypatch)
        sign_in(Role.patient)

        response = client.get(f"/v1/queues/{QUEUE_ID}")

        assert response.status_code == 200
        body = response.json()
        assert body["waiting_count"] == 2
        assert "display_name" not in body["entries"][0]
        assert "opened_by" not in body

    def test_staff_view_has_names(self, client, monkeypatch):
        self.stored(monkeypatch)
        sign_in(Role.nurse)

        response = client.get(f"/v1/queues/{QUEUE_ID}")

        assert response.json()["entries"][0]["display_name"] == "Thabo"

    def test_etag_gives_304(self, client, monkeypatch):
        self.stored(monkeypatch)
        sign_in(Role.patient)

        etag = client.get(f"/v1/queues/{QUEUE_ID}").headers["ETag"]
        response = client.get(f"/v1/queues/{QUEUE_ID}", headers={"If-None-Match": etag})

        assert response.status_code == 304

    def test_malformed_id_is_404(self, client):
        sign_in(Role.patient)

        assert client.get("/v1/queues/q1").status_code == 404

    def test_old_day_without_a_document_is_404(self, client, monkeypatch):
        monkeypatch.setattr(queue_service, "_load_queue_sync", lambda queue_id: None)
        sign_in(Role.patient)

        assert client.get("/v1/queues/2020-01-01-clinic1-sp1").status_code == 404


class TestServicePoints:
    def test_admin_creates_one_with_a_queue_id(self, client, monkeypatch):
        from schemas.db.documents import QueueConfigDoc, ServicePointDoc
        from schemas import DEFAULT_QUEUE_CONFIG

        async def create(clinic_id, body):
            return ServicePointDoc(
                id="sp1", clinic_id=clinic_id, name=body.name, icon=body.icon, display_prefix="B",
                config=QueueConfigDoc.model_validate(DEFAULT_QUEUE_CONFIG.model_dump()),
            )

        monkeypatch.setattr(clinic_service, "create_service_point", create)
        sign_in(Role.it_admin)

        response = client.post(
            "/v1/admin/clinics/clinic1/service-points", json={"name": "General", "icon": "stethoscope"}
        )

        assert response.status_code == 201
        assert response.json()["queue_id"].endswith("-clinic1-sp1")

    def test_patient_cannot_create(self, client):
        sign_in(Role.patient)

        response = client.post(
            "/v1/admin/clinics/clinic1/service-points", json={"name": "General", "icon": "x"}
        )

        assert response.status_code == 403
