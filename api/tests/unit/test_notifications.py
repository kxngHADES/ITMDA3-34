import asyncio
import datetime as dt
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from core.auth import build_caller, get_caller
from core.clock import service_date
from main import app
from schemas import (
    DEFAULT_QUEUE_CONFIG,
    JoinChannel,
    NotificationChannel,
    PriorityCategory,
    Role,
)
from schemas import AppError, ErrorCode
from schemas.db.documents import ClinicDoc, NotificationLogDoc, QueueConfigDoc, ServicePointDoc
from schemas.identity import NotificationKind
from services import clinic_service, crypto_service, notification_manager as nm, notification_service as ns, user_service
from services import queue_service as qs
from services.phone_service import format_sa_number, gateway_number

CLINIC = ClinicDoc(id="c1", name="Laudium CHC", address="1 Main Rd", opening_hours=[])
SERVICE_POINT = ServicePointDoc(
    id="sp1",
    clinic_id="c1",
    name="General consultation",
    icon="ticket",
    display_prefix="A",
    config=QueueConfigDoc.model_validate(DEFAULT_QUEUE_CONFIG.model_dump()),
)
QUEUE_ID = qs.queue_id_for("c1", "sp1", service_date())


def run(coro):
    return asyncio.run(coro)


def queue_with(count, **ticket_fields):
    data = qs._new_queue_data(QUEUE_ID, CLINIC, SERVICE_POINT, service_date())
    data["queue_id"] = QUEUE_ID
    tickets = []
    for i in range(count):
        tickets.append(
            qs._add_ticket(
                data,
                owner_uid=f"u{i}",
                channel=JoinChannel.app,
                display_name=None,
                phone=None,
                language="en",
                notification_channel=ticket_fields.get("channel", NotificationChannel.push),
                category=None,
            )
        )
    return data, tickets


class TestPhoneService:
    @pytest.mark.parametrize(
        "typed",
        ["0821234567", "27821234567", "+27821234567", "082 123 4567", "0027821234567", "+27 (0)82 123 4567", "082-123-4567"],
    )
    def test_formats_to_e164(self, typed):
        assert format_sa_number(typed) == "+27821234567"

    @pytest.mark.parametrize("typed", ["12345", "+44 7911 123456", "082123456", "abc", ""])
    def test_leaves_other_numbers_alone(self, typed):
        assert format_sa_number(typed) == typed

    def test_gateway_number_has_no_plus(self):
        assert gateway_number("082 123 4567") == "27821234567"
        with pytest.raises(ValueError):
            gateway_number("12345")


class TestTemplates:
    def test_almost_your_turn(self):
        html = ns.render(
            "queue_almost_turn.html",
            display_number="A-012", display_name="Thabo", clinic_name="Laudium CHC",
            service_name="General consultation", people_ahead=2, wait_min=16, wait_max=24, counter_label=None,
        )

        assert "Almost your turn" in html
        assert "A-012" in html and "Thabo" in html
        assert "2 people ahead of you" in html
        assert "16-24 minutes" in html
        assert "#FFF4E5" in html  # warning tint from tokens.json

    def test_your_turn(self):
        html = ns.render(
            "queue_your_turn.html",
            display_number="A-012", display_name=None, clinic_name="Laudium CHC",
            service_name="General consultation", counter_label="Room 3",
        )

        assert "your turn" in html
        assert "Please go to Room 3" in html
        assert "#007AFF" in html  # blue.system from tokens.json

    def test_priority_request_escapes_patient_text(self):
        html = ns.render(
            "priority_request.html",
            display_number="A-003", display_name="<script>x</script>", category_label="Elderly",
            service_name="General consultation", clinic_name="Laudium CHC",
        )

        assert "<script>" not in html
        assert "Elderly" in html


class TestClaimAlerts:
    def test_only_the_first_few_are_told_and_only_once(self):
        data, tickets = queue_with(5)

        first = ns.claim_alerts(QUEUE_ID, data)

        assert [a.display_number for a in first] == ["A-001", "A-002", "A-003"]
        assert {a.kind for a in first} == {NotificationKind.almost_your_turn}
        assert first[2].position == 3
        assert first[0].wait_min is not None
        assert ns.claim_alerts(QUEUE_ID, data) == []

    def test_a_ticket_moving_up_is_told_when_it_gets_close(self):
        data, tickets = queue_with(5)
        ns.claim_alerts(QUEUE_ID, data)
        data["tickets"][tickets[0].id]["status"] = "served"

        later = ns.claim_alerts(QUEUE_ID, data)

        assert [a.display_number for a in later] == ["A-004"]

    def test_called_ticket_gets_your_turn(self):
        data, tickets = queue_with(1)
        data["tickets"][tickets[0].id].update(status="called", counter_label="Room 3")

        alerts = ns.claim_alerts(QUEUE_ID, data)

        assert [(a.kind, a.counter_label) for a in alerts] == [(NotificationKind.called, "Room 3")]
        assert ns.claim_alerts(QUEUE_ID, data) == []

    def test_called_again_after_a_no_show_is_told_again(self):
        data, tickets = queue_with(1)
        ticket = data["tickets"][tickets[0].id]
        ticket["status"] = "called"
        ns.claim_alerts(QUEUE_ID, data)
        ticket.update(status="waiting", called_notified=False)
        ns.claim_alerts(QUEUE_ID, data)
        ticket["status"] = "called"

        assert [a.kind for a in ns.claim_alerts(QUEUE_ID, data)] == [NotificationKind.called]

    def test_patients_who_chose_no_alerts_are_skipped(self):
        data, _ = queue_with(2, channel=NotificationChannel.none)

        assert ns.claim_alerts(QUEUE_ID, data) == []


class TestDeliver:
    @pytest.fixture
    def sent(self, monkeypatch):
        log = SimpleNamespace(push=[], sms=[], email=[], batch=[], logged=[], push_ok=True, sms_ok=True)

        async def names(clinic_id, service_point_id):
            return "Laudium CHC", "General consultation"

        async def send_push(uid, title, body, data=None):
            log.push.append((uid, title, body, data))
            return (True, None) if log.push_ok else (False, "no registered device")

        async def send_sms(phone, text):
            log.sms.append((phone, text))
            return log.sms_ok

        async def send_email(**kwargs):
            log.email.append(kwargs)
            return {"id": "re_1"}

        async def send_batch(emails):
            log.batch.append(emails)

        monkeypatch.setattr(ns, "_names", names)
        monkeypatch.setattr(nm.push_service, "send_push", send_push)
        monkeypatch.setattr(nm, "send_sms", send_sms)
        monkeypatch.setattr(nm, "send_email", send_email)
        monkeypatch.setattr(ns, "send_batch_emails", send_batch)
        monkeypatch.setattr(nm, "_write_log_sync", lambda doc: log.logged.append(doc))
        ids = iter(f"log{i}" for i in range(1, 50))
        monkeypatch.setattr(
            nm,
            "get_firestore",
            lambda: SimpleNamespace(
                collection=lambda name: SimpleNamespace(document=lambda: SimpleNamespace(id=next(ids)))
            ),
        )
        return log

    def alert(self, store_fields, kind=NotificationKind.called, channel=NotificationChannel.push):
        base = dict(
            kind=kind, ticket_id="t1", queue_id=QUEUE_ID, clinic_id="c1", service_point_id="sp1", owner_uid="u1",
            channel=channel, display_number="A-001", display_name=None, counter_label="Room 3", position=2,
            wait_min=16, wait_max=24, phone_ciphertext=None, email_ciphertext=None,
        )
        return ns.Alert(**{**base, **store_fields})

    def contacts(self):
        return {
            "phone_ciphertext": crypto_service.encrypt("+27821234567"),
            "email_ciphertext": crypto_service.encrypt("a@b.co.za"),
        }

    def test_push_is_tried_first_and_logged(self, sent):
        assert run(ns.deliver(self.alert(self.contacts())))

        uid, title, body, data = sent.push[0]
        assert (uid, title) == ("u1", "It's your turn")
        assert "Room 3" in body
        assert data == {"kind": "called", "ticket_id": "t1", "queue_id": QUEUE_ID}
        assert sent.sms == [] and sent.email == []
        entry = sent.logged[0]
        assert (entry.channel, entry.status, entry.kind) == ("push", "sent", "called")
        assert entry.ticket_id == "t1" and entry.fallback_of is None

    def test_failed_push_falls_back_to_sms_and_points_at_the_failure(self, sent):
        sent.push_ok = False

        assert run(ns.deliver(self.alert(self.contacts())))

        failed, replacement = sent.logged
        assert (failed.channel, failed.status, failed.error) == ("push", "failed", "no registered device")
        assert (replacement.channel, replacement.status) == ("sms", "sent")
        assert replacement.fallback_of == failed.id
        assert sent.sms[0][0] == "+27821234567"
        assert sent.email == []

    def test_sms_failing_too_falls_back_to_email(self, sent):
        sent.push_ok = False
        sent.sms_ok = False

        assert run(ns.deliver(self.alert(self.contacts())))

        assert [(e.channel.value, e.status.value) for e in sent.logged] == [
            ("push", "failed"), ("sms", "failed"), ("email", "sent"),
        ]
        assert sent.logged[2].fallback_of == sent.logged[1].id
        assert sent.logged[2].provider_message_id == "re_1"
        assert "Please go to Room 3" in sent.email[0]["html"]

    def test_everything_failing_is_logged_and_reported(self, sent):
        sent.push_ok = False
        sent.sms_ok = False

        assert not run(ns.deliver(self.alert({"phone_ciphertext": crypto_service.encrypt("+27821234567")})))

        assert [e.status.value for e in sent.logged] == ["failed", "failed"]

    def test_the_patients_own_channel_goes_first(self, sent):
        alert = self.alert(self.contacts(), kind=NotificationKind.almost_your_turn, channel=NotificationChannel.sms)

        assert run(ns.deliver(alert))

        phone, text = sent.sms[0]
        assert phone == "+27821234567"
        assert "Almost your turn" in text and "16-24 min" in text and len(text) <= 160
        assert sent.push == []

    def test_email_channel_renders_the_template(self, sent):
        assert run(ns.deliver(self.alert(self.contacts(), channel=NotificationChannel.email)))

        mail = sent.email[0]
        assert mail["to"] == "a@b.co.za"
        assert "your turn" in mail["subject"]
        assert sent.push == [] and sent.sms == []

    def test_a_chosen_channel_with_no_contact_is_logged_then_falls_back(self, sent):
        assert run(ns.deliver(self.alert({}, channel=NotificationChannel.sms)))

        first, second = sent.logged
        assert (first.channel, first.status, first.error) == ("sms", "failed", "no phone number")
        assert (second.channel, second.status, second.fallback_of) == ("push", "sent", first.id)

    def test_a_walk_in_without_any_contact_logs_the_failure(self, sent):
        assert not run(ns.deliver(self.alert({"owner_uid": None})))

        assert [(e.channel.value, e.status.value) for e in sent.logged] == [("push", "failed")]

    def test_whatsapp_falls_back_because_it_has_no_sender(self, sent):
        assert run(ns.deliver(self.alert(self.contacts(), channel=NotificationChannel.whatsapp)))

        assert (sent.logged[0].channel, sent.logged[0].status) == ("whatsapp", "failed")
        assert sent.logged[1].channel == "push"

    def test_channel_order(self):
        assert nm.channel_order(NotificationChannel.none) == []
        assert nm.channel_order(NotificationChannel.sms) == [
            NotificationChannel.sms, NotificationChannel.push, NotificationChannel.email,
        ]

    def test_priority_request_emails_each_staff_member_separately_and_logs_it(self, sent, monkeypatch):
        async def staff(clinic_id, roles):
            assert roles == {"receptionist", "nurse", "manager"}
            return [("s1", {"email": "r@clinic.co.za"}), ("s2", {"email": "n@clinic.co.za"})]

        monkeypatch.setattr(ns.user_service, "list_clinic_staff", staff)
        data, tickets = queue_with(1)
        ticket = qs._ticket_doc(tickets[0].id, data["tickets"][tickets[0].id])
        ticket.priority.requested_category = PriorityCategory.elderly

        run(ns.notify_priority_request(ticket, "General consultation", "Laudium CHC"))

        emails = sent.batch[0]
        assert [e["to"] for e in emails] == [["r@clinic.co.za"], ["n@clinic.co.za"]]
        assert "Elderly" in emails[0]["subject"] and "A-001" in emails[0]["subject"]
        assert "Elderly" in emails[0]["html"]
        entry = sent.logged[0]
        assert (entry.kind, entry.channel, entry.status) == ("priority_requested", "email", "sent")

    def test_priority_request_with_no_staff_sends_nothing(self, sent, monkeypatch):
        async def staff(clinic_id, roles):
            return []

        monkeypatch.setattr(ns.user_service, "list_clinic_staff", staff)
        data, tickets = queue_with(1)

        run(ns.notify_priority_request(qs._ticket_doc(tickets[0].id, data["tickets"][tickets[0].id]), "s", "c"))

        assert sent.batch == [] and sent.logged == []


class TestNotificationLogEndpoint:
    @pytest.fixture
    def client(self):
        with TestClient(app, raise_server_exceptions=False) as c:
            yield c
        app.dependency_overrides.clear()

    @pytest.fixture(autouse=True)
    def stores(self, monkeypatch):
        self.visible_to = []
        now = dt.datetime.now(dt.UTC)

        async def get_ticket(ticket_id, uid, clinic_id=None):
            self.visible_to.append((uid, clinic_id))
            if uid == "stranger":
                raise AppError(ErrorCode.NOT_FOUND)

        async def list_for_ticket(ticket_id):
            return [
                NotificationLogDoc(id="n2", ticket_id=ticket_id, clinic_id="c1", channel="sms", kind="called",
                                   status="sent", fallback_of="n1", created_at=now),
                NotificationLogDoc(id="n1", ticket_id=ticket_id, clinic_id="c1", channel="push", kind="called",
                                   status="failed", error="no registered device", created_at=now),
            ]

        monkeypatch.setattr(qs, "get_ticket", get_ticket)
        monkeypatch.setattr(nm, "list_for_ticket", list_for_ticket)

    def test_owner_sees_the_log_newest_first(self, client):
        app.dependency_overrides[get_caller] = lambda: build_caller("u1", Role.patient)

        response = client.get(f"/v1/tickets/{QUEUE_ID}~abc/notifications")

        assert response.status_code == 200
        items = response.json()["items"]
        assert [i["id"] for i in items] == ["n2", "n1"]
        assert items[0]["fallback_of"] == "n1"
        assert items[1]["error"] == "no registered device"
        assert "clinic_id" not in items[0]

    def test_someone_elses_ticket_is_not_found(self, client):
        app.dependency_overrides[get_caller] = lambda: build_caller("stranger", Role.patient)

        assert client.get(f"/v1/tickets/{QUEUE_ID}~abc/notifications").status_code == 404

    def test_staff_are_checked_against_their_clinic(self, client):
        app.dependency_overrides[get_caller] = lambda: build_caller("n1", Role.nurse, clinic_id="c1")

        assert client.get(f"/v1/tickets/{QUEUE_ID}~abc/notifications").status_code == 200
        assert self.visible_to == [("n1", "c1")]


class TestJoinStoresContact:
    def test_phone_and_email_are_stored_encrypted(self):
        data, _ = queue_with(0)
        doc = qs._add_ticket(
            data, owner_uid="u1", channel=JoinChannel.app, display_name=None, phone="+27821234567",
            email="a@b.co.za", language="en", notification_channel=NotificationChannel.sms, category=None,
        )

        stored = data["tickets"][doc.id]
        assert stored["phone_ciphertext"] != b"+27821234567"
        assert crypto_service.decrypt(stored["phone_ciphertext"]) == "+27821234567"
        assert crypto_service.decrypt(stored["email_ciphertext"]) == "a@b.co.za"
        assert stored["phone_last4"] == "4567"


@pytest.fixture
def client():
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c
    app.dependency_overrides.clear()


def sign_in(role, clinic_id=None):
    app.dependency_overrides[get_caller] = lambda: build_caller("admin1", role, clinic_id=clinic_id)


class TestAddStaffToClinic:
    @pytest.fixture(autouse=True)
    def stores(self, monkeypatch):
        self.assigned = []
        now = dt.datetime.now(dt.UTC)

        async def get_clinic(clinic_id):
            return ClinicDoc(id=clinic_id, name="Laudium CHC", address="a", opening_hours=[])

        async def assign(uid, clinic_id):
            self.assigned.append((uid, clinic_id))
            return uid, {"email": "n@clinic.co.za", "full_name": "Nurse", "role": "nurse", "is_active": True,
                         "clinic_id": clinic_id, "created_at": now}

        async def staff(clinic_id, roles=None):
            return [("s1", {"email": "n@clinic.co.za", "full_name": "Nurse", "role": "nurse", "is_active": True,
                            "clinic_id": clinic_id, "created_at": now})]

        monkeypatch.setattr(clinic_service, "get_clinic", get_clinic)
        monkeypatch.setattr(user_service, "assign_clinic", assign)
        monkeypatch.setattr(user_service, "list_clinic_staff", staff)

    def test_it_admin_binds_a_user(self, client):
        sign_in(Role.it_admin)

        response = client.post("/v1/admin/clinics/c1/staff", json={"user_id": "s1"})

        assert response.status_code == 200
        assert response.json()["clinic_id"] == "c1"
        assert self.assigned == [("s1", "c1")]

    def test_manager_of_the_same_clinic_can(self, client):
        sign_in(Role.manager, "c1")

        assert client.post("/v1/admin/clinics/c1/staff", json={"user_id": "s1"}).status_code == 200

    def test_manager_of_another_clinic_cannot(self, client):
        sign_in(Role.manager, "c2")

        assert client.post("/v1/admin/clinics/c1/staff", json={"user_id": "s1"}).status_code == 403
        assert self.assigned == []

    @pytest.mark.parametrize("role", [Role.patient, Role.receptionist, Role.nurse])
    def test_other_roles_cannot(self, client, role):
        sign_in(role, "c1")

        assert client.post("/v1/admin/clinics/c1/staff", json={"user_id": "s1"}).status_code == 403

    def test_lists_the_clinic_staff(self, client):
        sign_in(Role.it_admin)

        response = client.get("/v1/admin/clinics/c1/staff")

        assert [s["id"] for s in response.json()["items"]] == ["s1"]
