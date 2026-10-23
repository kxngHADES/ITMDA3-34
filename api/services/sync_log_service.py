import asyncio

from google.cloud.firestore_v1.base_query import FieldFilter

from core.firebase import get_firestore
from schemas import AppError, ErrorCode, ResolveConflictRequest, SyncLogStatus
from schemas.admin import SyncResolution
from schemas.db.documents import SyncLogDoc, utcnow

SYNC_LOG_COLLECTION = "sync_log"


def _list_sync(clinic_id: str | None, status: SyncLogStatus | None, limit: int) -> list[SyncLogDoc]:
    collection = get_firestore().collection(SYNC_LOG_COLLECTION)
    query = collection.where(filter=FieldFilter("clinic_id", "==", clinic_id)) if clinic_id else collection
    docs = [SyncLogDoc.from_firestore(s.id, s.to_dict()) for s in query.stream()]
    docs = [d for d in docs if status is None or d.status == status]
    return sorted(docs, key=lambda d: d.created_at, reverse=True)[:limit]


def _resolve_sync(
    entry_id: str, body: ResolveConflictRequest, admin_uid: str, clinic_scope: str | None
) -> SyncLogDoc:
    ref = get_firestore().collection(SYNC_LOG_COLLECTION).document(entry_id)
    snapshot = ref.get()
    if not snapshot.exists:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"sync log entry {entry_id} does not exist")
    doc = SyncLogDoc.from_firestore(snapshot.id, snapshot.to_dict())
    if clinic_scope is not None and doc.clinic_id != clinic_scope:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"sync log entry {entry_id} is outside clinic {clinic_scope}")
    if doc.status is not SyncLogStatus.conflict_pending:
        raise AppError(ErrorCode.CONFLICT_ALREADY_RESOLVED, detail=f"{entry_id} is {doc.status}")
    now = utcnow()
    doc.status = SyncLogStatus.resolved
    doc.resolution = SyncResolution(body.resolution)
    doc.resolved_by = admin_uid
    doc.resolved_at = now
    doc.resolution_note = body.note
    ref.update(
        {
            "status": doc.status.value,
            "resolution": doc.resolution.value,
            "resolved_by": admin_uid,
            "resolved_at": now,
            "resolution_note": body.note,
            "updated_at": now,
        }
    )
    return doc


async def list_entries(
    clinic_id: str | None = None, status: SyncLogStatus | None = None, limit: int = 50
) -> list[SyncLogDoc]:
    # Newest first. clinic_id None means every clinic.
    return await asyncio.to_thread(_list_sync, clinic_id, status, limit)


async def resolve_conflict(
    entry_id: str, body: ResolveConflictRequest, admin_uid: str, clinic_scope: str | None = None
) -> SyncLogDoc:
    # Records the admin's decision only, the ticket itself is not rewritten here.
    return await asyncio.to_thread(_resolve_sync, entry_id, body, admin_uid, clinic_scope)
