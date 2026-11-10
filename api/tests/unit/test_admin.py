import asyncio
import datetime as dt

import pytest

from core.auth import build_caller, get_caller
from main import app
from schemas import DEFAULT_QUEUE_CONFIG, AppError, ErrorCode, Role, SyncLogStatus
from schemas.admin import AuditOutcome
from schemas.db.documents import AuditDoc, QueueConfigDoc, ServicePointDoc, SyncLogDoc
from services import analytics_service as an
from services import audit_service, clinic_service, sync_log_service, user_service

NOW = dt.datetime(2026, 10, 12, 8, 0, tzinfo=dt.UTC)  # 10:00 SAST


def ticket(status, joined_min=0, wait_min=None, channel="app", called_by=None):
    joined = NOW + dt.timedelta(minutes=joined_min)
    return {
        "status": status,
        "channel": channel,
        "joined_at": joined,
        "called_at": joined + dt.timedelta(minutes=wait_min) if wait_min is not None else None,
        "called_by": called_by,
    }


def test_snapshot_counts_and_waits():
    queue = {
        "tickets": {
            "a": ticket("served", wait_min=10, called_by="s1"),
            "b": ticket("served", 5, wait_min=20, called_by="s1"),
            "c": ticket("no_show", 10, channel="sms"),
            "d": ticket("cancelled", 15, channel="staff_assisted"),
        }
    }
    snap = an.snapshot_for_day("c1", NOW.date(), [queue])
    assert (snap.tickets_joined, snap.tickets_served, snap.no_shows, snap.cancellations) == (4, 2, 1, 1)
    assert snap.avg_wait_minutes == 15 and snap.p90_wait_minutes == 20
    assert snap.peak_hour == 10
    assert snap.joins_by_channel == {"app": 2, "sms": 1, "staff_assisted": 1}
    assert [(c.staff_id, c.calls) for c in snap.calls_per_staff] == [("s1", 2)]


def test_empty_day_has_no_waits():
    snap = an.snapshot_for_day("c1", NOW.date(), [])
    assert snap.tickets_joined == 0 and snap.avg_wait_minutes is None and snap.peak_hour is None


def test_range_limit():
    an.check_range(dt.date(2026, 1, 1), dt.date(2026, 4, 2))
    with pytest.raises(AppError) as e:
        an.check_range(dt.date(2026, 1, 1), dt.date(2026, 4, 3))
    assert e.value.code is ErrorCode.DATE_RANGE_TOO_LARGE


def test_csv_header_rows_and_formula_guard():
    queue = {"tickets": {"a": ticket("served", wait_min=4, called_by="=cmd")}}
    lines = an.to_csv([an.snapshot_for_day("c1", NOW.date(), [queue])]).splitlines()
    assert lines[0].startswith("date,clinic_id,tickets_joined")
    assert lines[1].startswith("2026-10-12,c1,1,1,0,0,4.0,4.0,10,1,0,0,")
    assert lines[1].endswith("'=cmd:1")


@pytest.fixture
def client_as(client):
    def _as(role, clinic_id=None, uid="u1"):
        app.dependency_overrides[get_caller] = lambda: build_caller(uid, role, clinic_id=clinic_id)
        return client

    return _as


def stub_staff(monkeypatch, store):
    async def get_staff(uid):
        if uid not in store:
            raise AppError(ErrorCode.NOT_FOUND)
        return uid, store[uid]

    async def update_staff(uid, changes):
        store[uid] = store[uid] | {
            ("full_name" if k == "display_name" else k): getattr(v, "value", v) for k, v in changes.items()
        }
        return uid, store[uid]

    monkeypatch.setattr(user_service, "get_staff", get_staff)
    monkeypatch.setattr(user_service, "update_staff", update_staff)


def staff_row(role="nurse", clinic="c1"):
    return {"email": "n@x.co.za", "full_name": "N", "role": role, "clinic_id": clinic, "is_active": True,
            "created_at": NOW}


def test_manager_cannot_touch_other_clinic_or_promote(monkeypatch, client_as):
    stub_staff(monkeypatch, {"s1": staff_row(clinic="c2"), "s2": staff_row(), "s3": staff_row(role="manager")})
    c = client_as(Role.manager, "c1", uid="m1")
    assert c.patch("/v1/admin/staff/s1", json={"display_name": "X"}).status_code == 403
    assert c.patch("/v1/admin/staff/s3", json={"display_name": "X"}).status_code == 403
    assert c.patch("/v1/admin/staff/s2", json={"role": "manager"}).status_code == 403
    assert c.patch("/v1/admin/staff/s2", json={"display_name": "Zola"}).json()["display_name"] == "Zola"


def test_cannot_deactivate_self_and_delete_deactivates(monkeypatch, client_as):
    store = {"a1": staff_row("it_admin", None), "s2": staff_row()}
    stub_staff(monkeypatch, store)
    c = client_as(Role.it_admin, uid="a1")
    assert c.delete("/v1/admin/staff/a1").json()["error"]["code"] == "CANNOT_DEACTIVATE_SELF"
    assert c.patch("/v1/admin/staff/a1", json={"is_active": False}).status_code == 400
    assert c.delete("/v1/admin/staff/s2").status_code == 204
    assert store["s2"]["is_active"] is False


def test_staff_endpoints_reject_non_admins(client_as):
    for role in (Role.nurse, Role.receptionist, Role.patient):
        assert client_as(role).get("/v1/admin/staff").status_code == 403


def test_list_staff_includes_admin_without_clinic(monkeypatch, client_as):
    async def list_staff(clinic_id=None):
        return [("a1", staff_row("it_admin", None)), ("s2", staff_row())]

    monkeypatch.setattr(user_service, "list_staff", list_staff)
    r = client_as(Role.it_admin, uid="a1").get("/v1/admin/staff")
    assert r.status_code == 200
    assert [i["clinic_id"] for i in r.json()["items"]] == [None, "c1"]


def test_unhandled_error_keeps_cors_headers(monkeypatch, client_as):
    async def list_staff(clinic_id=None):
        raise RuntimeError("boom")

    monkeypatch.setattr(user_service, "list_staff", list_staff)
    c = client_as(Role.it_admin)
    r = c.get("/v1/admin/staff", headers={"Origin": "http://localhost:3000"})
    assert r.status_code == 500 and r.json()["error"]["code"] == "SERVER_ERROR"
    assert "access-control-allow-origin" in r.headers


def test_analytics_csv_and_json(monkeypatch, client_as):
    async def get_clinic(clinic_id):
        return None

    monkeypatch.setattr(clinic_service, "get_clinic", get_clinic)
    monkeypatch.setattr(
        an,
        "_load_queues_sync",
        lambda *a: [{"service_date": "2026-10-12", "tickets": {"a": ticket("served", wait_min=6)}}],
    )
    c = client_as(Role.manager, "c1")
    q = "from=2026-10-11&to=2026-10-12"
    items = c.get(f"/v1/admin/clinics/c1/analytics?{q}").json()["items"]
    assert [i["tickets_joined"] for i in items] == [0, 1]
    r = c.get(f"/v1/admin/clinics/c1/analytics?{q}&format=csv")
    assert r.headers["content-type"].startswith("text/csv") and "attachment" in r.headers["content-disposition"]
    assert len(r.text.splitlines()) == 3
    assert c.get("/v1/admin/clinics/c2/analytics?" + q).status_code == 403
    assert c.get("/v1/admin/clinics/c1/analytics?from=2026-01-01&to=2026-12-31").status_code == 400


def test_queue_config_roundtrip(monkeypatch, client_as):
    sp = ServicePointDoc(id="sp1", clinic_id="c1", name="G", icon="x",
                         config=QueueConfigDoc.model_validate(DEFAULT_QUEUE_CONFIG.model_dump()))

    async def get_sp(i):
        return sp

    async def update(i, config):
        sp.config = QueueConfigDoc.model_validate(config.model_dump())
        return sp

    monkeypatch.setattr(clinic_service, "get_service_point", get_sp)
    monkeypatch.setattr(clinic_service, "update_service_point_config", update)
    c = client_as(Role.manager, "c1")
    body = DEFAULT_QUEUE_CONFIG.model_dump() | {"priority_max_jump_k": 4}
    assert c.put("/v1/admin/service-points/sp1/queue-config", json=body).json()["priority_max_jump_k"] == 4
    assert c.get("/v1/admin/service-points/sp1/queue-config").json()["priority_max_jump_k"] == 4
    assert c.put("/v1/admin/service-points/sp1/queue-config", json=body | {"ema_alpha": 5}).status_code == 422
    sp.clinic_id = "c9"
    assert c.get("/v1/admin/service-points/sp1/queue-config").status_code == 403


def test_sync_log_list_and_scope(monkeypatch, client_as):
    entry = SyncLogDoc(id="e1", device_uid="d", clinic_id="c1", idempotency_key="k", op="join",
                       status=SyncLogStatus.conflict_pending)

    async def list_entries(clinic_id, status, limit):
        assert clinic_id == "c1"
        return [entry]

    monkeypatch.setattr(sync_log_service, "list_entries", list_entries)
    c = client_as(Role.manager, "c1")
    assert c.get("/v1/admin/sync-log?status=conflict_pending").json()["items"][0]["id"] == "e1"
    assert c.get("/v1/admin/sync-log?clinic_id=c2").status_code == 403
    assert c.get("/v1/admin/sync-log?status=bogus").status_code == 422


def test_staff_changes_are_audited(monkeypatch, client_as, audit_log):
    stub_staff(monkeypatch, {"s1": staff_row(clinic="c2"), "s2": staff_row()})
    c = client_as(Role.manager, "c1", uid="m1")
    assert c.patch("/v1/admin/staff/s2", json={"display_name": "Zola"}).status_code == 200
    assert c.patch("/v1/admin/staff/s1", json={"display_name": "X"}).status_code == 403
    assert c.patch("/v1/admin/staff/missing", json={"display_name": "X"}).status_code == 404
    assert [(e.action, e.target_id, e.outcome, e.clinic_id) for e in audit_log] == [
        ("staff.update", "s2", AuditOutcome.success, "c1"),
        ("staff.update", "s1", AuditOutcome.denied, "c2"),
        ("staff.update", "missing", AuditOutcome.failed, "c1"),
    ]
    assert all(e.actor_id == "m1" and e.actor_role is Role.manager for e in audit_log)


def test_audit_write_failure_does_not_fail_the_action(monkeypatch, client_as):
    def broken(doc):
        raise RuntimeError("firestore down")

    monkeypatch.setattr(audit_service, "_write", broken)
    stub_staff(monkeypatch, {"s2": staff_row()})
    assert client_as(Role.it_admin).delete("/v1/admin/staff/s2").status_code == 204


def test_audit_log_list_filters_and_scope(monkeypatch, client_as):
    calls = []
    entry = AuditDoc(id="a1", actor_id="s1", actor_role=Role.nurse, clinic_id="c1", action="ticket.call_next",
                     target_type="ticket", target_id="t1", outcome=AuditOutcome.success, created_at=NOW)

    async def list_entries(clinic_id, actor_id, outcome, from_, to, limit):
        calls.append((clinic_id, actor_id, outcome, from_, to, limit))
        return [entry]

    monkeypatch.setattr(audit_service, "list_entries", list_entries)
    c = client_as(Role.manager, "c1")
    body = c.get("/v1/admin/audit-log?actor_id=s1&outcome=success&from=2026-10-12T00:00:00&limit=10").json()
    assert body["items"][0] | {"created_at": None} == {
        "id": "a1", "actor_id": "s1", "actor_role": "nurse", "action": "ticket.call_next",
        "target_type": "ticket", "target_id": "t1", "outcome": "success", "created_at": None,
    }
    assert calls[-1] == ("c1", "s1", AuditOutcome.success, dt.datetime(2026, 10, 12, tzinfo=dt.UTC), None, 10)
    assert c.get("/v1/admin/audit-log?clinic_id=c2").status_code == 403
    assert c.get("/v1/admin/audit-log?outcome=bogus").status_code == 422
    assert c.get("/v1/admin/audit-log?from=2026-10-12T00:00:00Z&to=2026-10-11T00:00:00Z").status_code == 422
    assert client_as(Role.it_admin).get("/v1/admin/audit-log?clinic_id=c2").status_code == 200
    assert calls[-1][0] == "c2"
    assert client_as(Role.nurse, "c1").get("/v1/admin/audit-log").status_code == 403


def test_audit_list_filters_newest_first(monkeypatch):
    rows = {
        f"a{i}": AuditDoc(id=f"a{i}", actor_id="s1", actor_role=Role.nurse, clinic_id="c1", action="x",
                          outcome=AuditOutcome.denied if i == 2 else AuditOutcome.success,
                          created_at=NOW + dt.timedelta(hours=i)).to_firestore()
        for i in range(4)
    }

    class _Snap:
        def __init__(self, doc_id, data):
            self.id, self._data = doc_id, data

        def to_dict(self):
            return dict(self._data)

    class _Query:
        def where(self, filter):
            return self

        def stream(self):
            return [_Snap(k, v) for k, v in rows.items()]

    class _Db:
        def collection(self, name):
            assert name == audit_service.AUDIT_COLLECTION
            return _Query()

    monkeypatch.setattr(audit_service, "get_firestore", lambda: _Db())
    got = asyncio.run(audit_service.list_entries("c1", to=NOW + dt.timedelta(hours=2), limit=5))
    assert [d.id for d in got] == ["a2", "a1", "a0"]
    got = asyncio.run(audit_service.list_entries(outcome=AuditOutcome.success, limit=2))
    assert [d.id for d in got] == ["a3", "a1"]
