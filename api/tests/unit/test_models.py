# Behaviour of the custom validators, error catalogue, documents and mappers.
from __future__ import annotations

import datetime as dt
import uuid

import pytest
from pydantic import ValidationError

import schemas as s
from schemas.db import documents as d
from schemas.db import mappers as m

NOW = dt.datetime(2026, 10, 8, 8, 30, tzinfo=dt.UTC)


# requests: basics
def test_join_minimal_ok():
    body = s.JoinQueueRequest(language="zu")
    assert body.priority_request is None


def test_unknown_field_rejected():
    with pytest.raises(ValidationError):
        s.JoinQueueRequest(language="en", surname="Mokoena")  # extra="forbid"


def test_phone_format_and_sms_needs_phone():
    with pytest.raises(ValidationError):
        s.JoinQueueRequest(language="en", phone="12345678")  # not a South African number
    for typed in ("0712345678", "27712345678", "+27 71 234 5678", "071 234 5678", "0027712345678", "+27 (0)71 234 5678"):
        assert s.JoinQueueRequest(language="en", phone=typed).phone == "+27712345678"
    with pytest.raises(ValidationError, match="phone number"):
        s.JoinQueueRequest(language="en", notification_channel="sms")
    assert s.JoinQueueRequest(language="en", phone="+27712345678", notification_channel="sms").phone == "+27712345678"


def test_email_channel_needs_an_email():
    with pytest.raises(ValidationError, match="email address"):
        s.JoinQueueRequest(language="en", notification_channel="email")
    assert s.JoinQueueRequest(language="en", notification_channel="email", email="a@b.co.za").email == "a@b.co.za"


def test_email_channel_needs_an_email():
    with pytest.raises(ValidationError, match="email address"):
        s.JoinQueueRequest(language="en", notification_channel="email")
    assert s.JoinQueueRequest(language="en", notification_channel="email", email="a@b.co.za").email == "a@b.co.za"


def test_safe_text_strips_and_blocks_html():
    assert s.JoinQueueRequest(language="en", display_name=" Thabo ").display_name == "Thabo"
    with pytest.raises(ValidationError, match="< or >"):
        s.JoinQueueRequest(language="en", display_name="<script>")


def test_priority_category_closed_list():
    with pytest.raises(ValidationError):
        s.JoinQueueRequest(language="en", priority_request="vip")


# PATCH semantics
def test_patch_needs_one_field_and_tracks_sent_fields():
    with pytest.raises(ValidationError, match="at least one"):
        s.UpdateTicketRequest()
    body = s.UpdateTicketRequest(phone=None)  # explicit null = clear phone
    assert body.model_dump(exclude_unset=True) == {"phone": None}


def test_update_staff_and_queue_patch():
    assert s.UpdateStaffRequest(role="nurse").model_dump(exclude_unset=True) == {"role": s.StaffRole.nurse}
    with pytest.raises(ValidationError):
        s.UpdateQueueRequest(concurrent_servers=0)


# staff bodies
def test_dismiss_other_needs_note_and_expired_allowed():
    s.DismissRequest(reason="expired")
    with pytest.raises(ValidationError, match="note"):
        s.DismissRequest(reason="other")


def test_confirm_priority_rules():
    s.ConfirmPriorityRequest(confirmed=False)
    with pytest.raises(ValidationError):
        s.ConfirmPriorityRequest(confirmed=True, category="none")


def test_opening_hours_rules():
    ok = [{"day": "mon", "opens": "07:00", "closes": "16:00"}]
    s.CreateClinicRequest(name="Block B", address="Soshanguve", opening_hours=ok)
    with pytest.raises(ValidationError, match="after opening"):
        s.CreateClinicRequest(name="X", address="Y", opening_hours=[{"day": "mon", "opens": "16:00", "closes": "07:00"}])
    with pytest.raises(ValidationError, match="once"):
        s.CreateClinicRequest(name="X", address="Y", opening_hours=ok + ok)


def test_staff_email_lowercased():
    body = s.CreateStaffRequest(email="Nurse@Clinic.co.za", display_name="Lerato", role="nurse", clinic_id="c1")
    assert body.email == "nurse@clinic.co.za"


def test_analytics_query_alias_and_range():
    q = s.AnalyticsQuery.model_validate({"from": "2026-10-01", "to": "2026-10-07"})
    assert q.from_ == dt.date(2026, 10, 1)
    with pytest.raises(ValidationError):
        s.AnalyticsQuery.model_validate({"from": "2026-10-07", "to": "2026-10-01"})


# sync
def _item(op, payload, **kw):
    return {
        "idempotency_key": str(uuid.uuid4()),
        "op": op,
        "client_updated_at": NOW.isoformat(),
        "payload": payload,
        **kw,
    }


def test_outbox_payload_parsed_by_op():
    join = s.OutboxItem.model_validate(_item("join", {"language": "en"}, queue_id="q1"))
    assert isinstance(join.payload, s.JoinQueueRequest)
    upd = s.OutboxItem.model_validate(_item("update_ticket", {"language": "en"}, ticket_id="t1"))
    assert isinstance(upd.payload, s.UpdateTicketRequest)  # not mis-read as a join
    cancel = s.OutboxItem.model_validate(_item("cancel", {}, local_ref="local-7"))
    assert isinstance(cancel.payload, s.EmptyPayload)


def test_outbox_rules():
    with pytest.raises(ValidationError, match="queue_id"):
        s.OutboxItem.model_validate(_item("join", {"language": "en"}))
    with pytest.raises(ValidationError, match="ticket_id or local_ref"):
        s.OutboxItem.model_validate(_item("cancel", {}))
    with pytest.raises(ValidationError):
        s.OutboxItem.model_validate(_item("cancel", {"x": 1}, ticket_id="t1"))
    with pytest.raises(ValidationError):  # not a UUIDv4
        s.OutboxItem.model_validate({**_item("cancel", {}, ticket_id="t1"), "idempotency_key": "abc"})


def test_sync_push_batch_limits():
    with pytest.raises(ValidationError):
        s.SyncPushRequest(device_id="device-123", items=[])
    items = [_item("cancel", {}, ticket_id=f"t{i}") for i in range(51)]
    with pytest.raises(ValidationError):
        s.SyncPushRequest.model_validate({"device_id": "device-123", "items": items})


# errors & permissions
def test_app_error_response_shape():
    err = s.AppError(s.ErrorCode.RATE_LIMITED, retry_after_seconds=12)
    body = err.to_response("req_1").model_dump(mode="json")
    assert err.status_code == 429
    assert body == {
        "error": {
            "code": "RATE_LIMITED",
            "message": "Please wait a moment before trying again.",
            "retryable": True,
            "retry_after_seconds": 12,
            "request_id": "req_1",
        }
    }


def test_role_permissions():
    P = s.Permission
    assert P.queue_pause in s.ROLE_PERMISSIONS[s.Role.receptionist]
    assert P.queue_pause not in s.ROLE_PERMISSIONS[s.Role.nurse]
    assert P.analytics_view in s.ROLE_PERMISSIONS[s.Role.manager]
    assert s.ROLE_PERMISSIONS[s.Role.it_admin] >= {P.staff_manage, P.sync_manage}
    assert P.queue_call_next not in s.ROLE_PERMISSIONS[s.Role.it_admin]
    assert all(P.queue_view in perms for perms in s.ROLE_PERMISSIONS.values())


def test_default_queue_config():
    c = s.DEFAULT_QUEUE_CONFIG
    assert c.priority_weights.for_category(s.PriorityCategory.urgent) == 6
    assert c.priority_weights.for_category(s.PriorityCategory.none) == 0
    assert (c.priority_max_jump_k, c.starvation_threshold_w_minutes, c.ema_alpha) == (10, 100, 0.2)


# documents & mappers
def _ticket_doc(**kw):
    base = dict(
        id="tkt_1", owner_uid="uid_p", device_id="dev-1", clinic_id="c1", service_point_id="sp1", queue_id="q1",
        sequence_no=14, display_number="A014", channel="app", display_name="Thabo", phone_ciphertext=b"\x01\x02",
        phone_last4="4821", created_at=NOW, updated_at=NOW, joined_at=NOW,
    )
    return d.TicketDoc(**(base | kw))


def test_document_roundtrip_keeps_id_out_of_body():
    doc = _ticket_doc()
    data = doc.to_firestore()
    assert "id" not in data and data["phone_ciphertext"] == b"\x01\x02"
    again = d.TicketDoc.from_firestore("tkt_1", data)
    assert again.display_number == "A014" and again.id == "tkt_1"


def test_ticket_mapper_hides_storage_fields():
    api = m.ticket_to_api(_ticket_doc(), position=4, wait=m.wait_range(4, 2, 600, cold_start=False))
    out = api.model_dump()
    for secret in ("owner_uid", "device_id", "phone_ciphertext", "hidden_by_patient", "created_idempotency_key"):
        assert secret not in out
    assert api.people_ahead == 3
    assert (api.estimated_wait.min_minutes, api.estimated_wait.max_minutes) == (16, 24)


def test_position_only_for_waiting_tickets():
    api = m.ticket_to_api(_ticket_doc(status="called"), position=1)
    assert api.position is None and api.estimated_wait is None


def test_patient_queue_view_is_anonymised():
    tdoc = _ticket_doc(priority=d.TicketPriorityDoc(requested_category="pregnant", staff_confirmed=True))
    qdoc = d.QueueDoc(
        id="q1", service_point_id="sp1", clinic_id="c1", service_date=dt.date(2026, 10, 8), opened_by="uid_s",
        created_at=NOW, updated_at=NOW,
    )
    entry = m.queue_entry(tdoc, position=1, protected=False)
    patient = m.queue_to_api(qdoc, [entry], base_service_seconds=600, for_patient=True).model_dump(
        mode="json", exclude_none=True
    )
    staff = m.queue_to_api(qdoc, [entry], base_service_seconds=600, for_patient=False).model_dump(
        mode="json", exclude_none=True
    )
    assert patient["entries"][0]["is_priority"] is True
    assert "display_name" not in patient["entries"][0] and "priority_category" not in patient["entries"][0]
    assert "opened_by" not in patient
    assert staff["entries"][0]["priority_category"] == "pregnant"


def test_clinic_is_open_uses_sast():
    doc = d.ClinicDoc(
        id="c1", name="Block B", address="Soshanguve",
        opening_hours=[{"day": "thu", "opens": "07:00", "closes": "16:00"}],
    )
    assert m.clinic_is_open(doc, now=dt.datetime(2026, 10, 8, 5, 30, tzinfo=dt.UTC)) is True  # 07:30 SAST Thursday
    assert m.clinic_is_open(doc, now=dt.datetime(2026, 10, 8, 4, 30, tzinfo=dt.UTC)) is False  # 06:30 SAST
