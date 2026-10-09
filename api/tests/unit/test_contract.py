# Fails if any Pydantic model drifts from openapi-v1.yaml.
from __future__ import annotations

import os
from enum import Enum
from pathlib import Path

import pytest
import yaml

import schemas as s


def _find_spec() -> Path:
    if os.environ.get("SWIFTQ_OPENAPI"):
        return Path(os.environ["SWIFTQ_OPENAPI"])
    here = Path(__file__).resolve()
    candidates = [
        here.parents[3] / "openapi-v1.yaml",
        here.parents[2] / "openapi-v1.yaml",
        Path.cwd() / "openapi-v1.yaml",
        Path.cwd().parent / "openapi-v1.yaml",
    ]
    for cand in candidates:
        if cand.is_file():
            return cand
    return candidates[0]


SPEC_PATH = _find_spec()
SPEC = yaml.safe_load(SPEC_PATH.read_text(encoding="utf-8"))
SCHEMAS = SPEC["components"]["schemas"]

MODEL_FOR = {
    "Role": s.Role,
    "StaffRole": s.StaffRole,
    "TicketStatus": s.TicketStatus,
    "PriorityCategory": s.PriorityCategory,
    "JoinChannel": s.JoinChannel,
    "NotificationChannel": s.NotificationChannel,
    "QueueStatus": s.QueueStatus,
    "SyncLogStatus": s.SyncLogStatus,
    "SyncOp": s.SyncOp,
    "ErrorCode": s.ErrorCode,
    "ErrorResponse": s.ErrorResponse,
    "ErrorBody": s.ErrorBody,
    "Page": s.PageMeta,
    "WaitRange": s.WaitRange,
    "Me": s.Me,
    "PushTokenRequest": s.PushTokenRequest,
    "NotificationLogEntry": s.NotificationLogEntry,
    "Clinic": s.Clinic,
    "ServicePoint": s.ServicePoint,
    "Queue": s.Queue,
    "QueueEntry": s.QueueEntry,
    "QueueConfig": s.QueueConfig,
    "OpenQueueRequest": s.OpenQueueRequest,
    "UpdateQueueRequest": s.UpdateQueueRequest,
    "Ticket": s.Ticket,
    "JoinQueueRequest": s.JoinQueueRequest,
    "UpdateTicketRequest": s.UpdateTicketRequest,
    "WalkInRequest": s.WalkInRequest,
    "StaffAccount": s.StaffAccount,
    "CreateStaffRequest": s.CreateStaffRequest,
    "UpdateStaffRequest": s.UpdateStaffRequest,
    "SyncLogEntry": s.SyncLogEntry,
    "AuditEntry": s.AuditEntry,
    "ErrorLogEntry": s.ErrorLogEntry,
    "AnalyticsSnapshot": s.AnalyticsSnapshot,
    "OutboxItem": s.OutboxItem,
    "SyncPushRequest": s.SyncPushRequest,
    "SyncItemResult": s.SyncItemResult,
    "SyncPushResponse": s.SyncPushResponse,
    "SyncPullResponse": s.SyncPullResponse,
    "CreateClinicRequest": s.CreateClinicRequest,
    "UpdateClinicRequest": s.UpdateClinicRequest,
    "CreateServicePointRequest": s.CreateServicePointRequest,
    "UpdateServicePointRequest": s.UpdateServicePointRequest,
}

NOT_MODELS = {"LanguageCode", "OpeningHours"}

INLINE_BODIES = {
    ("/v1/queues/{queue_id}/pause", "post"): s.PauseQueueRequest,
    ("/v1/queues/{queue_id}/close", "post"): s.CloseQueueRequest,
    ("/v1/queues/{queue_id}/call-next", "post"): s.CallNextRequest,
    ("/v1/tickets/{ticket_id}/dismiss", "post"): s.DismissRequest,
    ("/v1/tickets/{ticket_id}/confirm-priority", "post"): s.ConfirmPriorityRequest,
    ("/v1/tickets/{ticket_id}/transfer", "post"): s.TransferRequest,
    ("/v1/admin/sync-log/{entry_id}/resolve", "post"): s.ResolveConflictRequest,
}

NESTED = {
    ("Ticket", "priority"): s.TicketPriority,
    ("QueueConfig", "priority_weights"): s.PriorityWeights,
    ("SyncLogEntry", "conflict"): s.SyncConflict,
    ("Clinic", "opening_hours[]"): s.OpeningHoursSlot,
    ("AnalyticsSnapshot", "calls_per_staff[]"): s.CallsPerStaff,
}

LIMIT_KEYS = ["maxLength", "minLength", "minimum", "maximum", "pattern", "minItems", "maxItems"]


def _model_schema(model):
    return model.model_json_schema(mode="validation", by_alias=True)


def _flatten(sub: dict, defs: dict) -> dict:
    if "$ref" in sub:
        return _flatten(defs[sub["$ref"].split("/")[-1]], defs)
    if "anyOf" in sub:
        non_null = [b for b in sub["anyOf"] if b.get("type") != "null"]
        if len(non_null) == 1:
            merged = _flatten(non_null[0], defs)
            return {**merged, **{k: v for k, v in sub.items() if k != "anyOf"}}
    if "allOf" in sub and len(sub["allOf"]) == 1:
        return _flatten(sub["allOf"][0], defs)
    return sub


def _compare_object(name: str, contract: dict, model) -> list[str]:
    js = _model_schema(model)
    defs = js.get("$defs", {})
    problems = []
    c_props = set(contract.get("properties", {}))
    m_props = set(js.get("properties", {}))
    if c_props != m_props:
        problems.append(
            f"{name}: fields differ contract-only={sorted(c_props - m_props)}"
            f" model-only={sorted(m_props - c_props)}"
        )
    c_req = set(contract.get("required", []))
    m_req = set(js.get("required", []))
    if c_req != m_req:
        problems.append(
            f"{name}: required differs contract-only={sorted(c_req - m_req)}"
            f" model-only={sorted(m_req - c_req)}"
        )
    for prop, c_sub in contract.get("properties", {}).items():
        if prop not in js.get("properties", {}):
            continue
        m_sub = _flatten(js["properties"][prop], defs)
        for key in LIMIT_KEYS:
            if key in c_sub and c_sub[key] != m_sub.get(key):
                problems.append(
                    f"{name}.{prop}: {key} contract={c_sub[key]!r} model={m_sub.get(key)!r}"
                )
        if "enum" in c_sub:
            m_enum = m_sub.get("enum") or ([m_sub["const"]] if "const" in m_sub else None)
            c_enum = [v for v in c_sub["enum"] if v is not None]
            if m_enum is None or set(c_enum) != set(m_enum):
                problems.append(f"{name}.{prop}: enum contract={c_enum} model={m_enum}")
    return problems


def _contract_items():
    for name, sc in SCHEMAS.items():
        if name in NOT_MODELS:
            continue
        yield name, sc, MODEL_FOR[name]


def test_every_contract_schema_has_a_model():
    missing = set(SCHEMAS) - set(MODEL_FOR) - NOT_MODELS
    assert not missing, f"No Pydantic model for: {sorted(missing)}"


@pytest.mark.parametrize(
    "name,contract,model", list(_contract_items()), ids=[n for n, *_ in _contract_items()]
)
def test_schema_matches_model(name, contract, model):
    if isinstance(model, type) and issubclass(model, Enum):
        assert [m.value for m in model] == contract["enum"], f"{name}: enum values differ"
        return
    problems = _compare_object(name, contract, model)
    assert not problems, "\n".join(problems)


@pytest.mark.parametrize("key,model", list(NESTED.items()), ids=[f"{a}.{b}" for a, b in NESTED])
def test_nested_objects(key, model):
    parent, prop = key
    sub = SCHEMAS[parent]["properties"][prop.rstrip("[]")]
    if prop.endswith("[]"):
        sub = sub["items"]
    if isinstance(sub.get("type"), list):
        sub = {k: v for k, v in sub.items() if k != "type"}
    problems = _compare_object(f"{parent}.{prop}", sub, model)
    assert not problems, "\n".join(problems)


@pytest.mark.parametrize("key,model", list(INLINE_BODIES.items()), ids=[p for p, _ in INLINE_BODIES])
def test_inline_request_bodies(key, model):
    path, method = key
    body = SPEC["paths"][path][method]["requestBody"]["content"]["application/json"]["schema"]
    problems = _compare_object(f"{method.upper()} {path}", body, model)
    assert not problems, "\n".join(problems)


def test_every_error_code_has_a_catalogue_entry():
    assert set(s.ERROR_CATALOGUE) == set(s.ErrorCode)
    for code, (status, _, msg) in s.ERROR_CATALOGUE.items():
        assert status in (400, 401, 403, 404, 422, 429, 500), code
        assert code.value not in msg and "_" not in msg, f"{code}: message must be plain language"
