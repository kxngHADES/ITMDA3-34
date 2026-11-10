import asyncio
import datetime as dt
import logging
import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass

from google.cloud.firestore_v1.base_query import FieldFilter

from core.firebase import get_firestore
from schemas import AppError, ErrorCode, Role
from schemas.admin import AuditOutcome
from schemas.db.documents import AuditDoc

AUDIT_COLLECTION = "audit_log"

logger = logging.getLogger(__name__)


@dataclass
class AuditTarget:
    # Handed to the route inside track(), so a create can name what it made once it exists.
    target_type: str | None = None
    target_id: str | None = None
    clinic_id: str | None = None


def _write(doc: AuditDoc) -> None:
    get_firestore().collection(AUDIT_COLLECTION).document(doc.id).set(doc.to_firestore())


def _list_audit(
    clinic_id: str | None,
    actor_id: str | None,
    outcome: AuditOutcome | None,
    from_: dt.datetime | None,
    to: dt.datetime | None,
    limit: int,
) -> list[AuditDoc]:
    query = get_firestore().collection(AUDIT_COLLECTION)
    if clinic_id:
        query = query.where(filter=FieldFilter("clinic_id", "==", clinic_id))
    if actor_id:
        query = query.where(filter=FieldFilter("actor_id", "==", actor_id))
    docs = [AuditDoc.from_firestore(s.id, s.to_dict()) for s in query.stream()]
    docs = [
        d
        for d in docs
        if (outcome is None or d.outcome == outcome)
        and (from_ is None or d.created_at >= from_)
        and (to is None or d.created_at <= to)
    ]
    return sorted(docs, key=lambda d: d.created_at, reverse=True)[:limit]


async def record(
    actor_id: str,
    actor_role: Role,
    action: str,
    outcome: AuditOutcome,
    *,
    clinic_id: str | None = None,
    target_type: str | None = None,
    target_id: str | None = None,
    request_id: str | None = None,
) -> None:
    # A failed audit write is logged but never fails the staff action it describes.
    doc = AuditDoc(
        id=uuid.uuid4().hex,
        actor_id=actor_id,
        actor_role=actor_role,
        clinic_id=clinic_id,
        action=action,
        target_type=target_type,
        target_id=target_id,
        outcome=outcome,
        request_id=request_id,
    )
    try:
        await asyncio.to_thread(_write, doc)
    except Exception:
        logger.exception("could not write audit entry %s for %s", action, actor_id)


def _outcome_for(error: Exception) -> AuditOutcome:
    if isinstance(error, AppError) and error.code is ErrorCode.FORBIDDEN:
        return AuditOutcome.denied
    return AuditOutcome.failed


@asynccontextmanager
async def track(
    caller,
    action: str,
    target_type: str | None = None,
    target_id: str | None = None,
    clinic_id: str | None = None,
) -> AsyncIterator[AuditTarget]:
    # Wrap a staff action: success when the block finishes, denied on FORBIDDEN, failed on any other error.
    target = AuditTarget(target_type, target_id, clinic_id or caller.clinic_id)
    try:
        yield target
    except Exception as error:
        await record(
            caller.uid, caller.role, action, _outcome_for(error),
            clinic_id=target.clinic_id, target_type=target.target_type, target_id=target.target_id,
        )
        raise
    await record(
        caller.uid, caller.role, action, AuditOutcome.success,
        clinic_id=target.clinic_id, target_type=target.target_type, target_id=target.target_id,
    )


async def list_entries(
    clinic_id: str | None = None,
    actor_id: str | None = None,
    outcome: AuditOutcome | None = None,
    from_: dt.datetime | None = None,
    to: dt.datetime | None = None,
    limit: int = 50,
) -> list[AuditDoc]:
    # Newest first. clinic_id None means every clinic.
    return await asyncio.to_thread(_list_audit, clinic_id, actor_id, outcome, from_, to, limit)
