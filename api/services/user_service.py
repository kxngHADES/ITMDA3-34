import asyncio
from datetime import UTC, datetime

from google.cloud.firestore_v1.base_query import FieldFilter

from core.firebase import get_firestore, set_role_claim
from schemas import AppError, ErrorCode, StaffRole
from schemas.auth import AuthUser

USERS_COLLECTION = "users"


def _utcnow() -> datetime:
    return datetime.now(UTC)


def _create_user_sync(uid: str, email: str, full_name: str, role: StaffRole, clinic_id: str | None) -> None:
    now = _utcnow()
    get_firestore().collection(USERS_COLLECTION).document(uid).set(
        {
            "email": email,
            "full_name": full_name,
            "role": role.value,
            "is_active": True,
            # Blank until the user is bound to a clinic.
            "clinic_id": clinic_id,
            "created_at": now,
            "updated_at": now,
        }
    )
    set_role_claim(uid, role.value)


def _get_user_sync(uid: str) -> dict | None:
    snapshot = get_firestore().collection(USERS_COLLECTION).document(uid).get()
    return snapshot.to_dict() if snapshot.exists else None


def _touch_last_sign_in_sync(uid: str) -> None:
    get_firestore().collection(USERS_COLLECTION).document(uid).update(
        {"last_sign_in_at": _utcnow()}
    )


async def create_user(
    uid: str, email: str, full_name: str, role: StaffRole, clinic_id: str | None = None
) -> AuthUser:
    # The Firestore client is blocking, so it runs off the event loop.
    await asyncio.to_thread(_create_user_sync, uid, email, full_name, role, clinic_id)
    return AuthUser(id=uid, email=email, role=role, full_name=full_name, clinic_id=clinic_id)


async def get_active_user(uid: str) -> AuthUser:
    data = await asyncio.to_thread(_get_user_sync, uid)
    if data is None or not data.get("is_active", False):
        # Signed in with Firebase but has no active profile, so there is no role to issue.
        raise AppError(ErrorCode.FORBIDDEN, detail=f"no active user profile for {uid}")
    await asyncio.to_thread(_touch_last_sign_in_sync, uid)
    return AuthUser(
        id=uid,
        email=data["email"],
        role=StaffRole(data["role"]),
        full_name=data.get("full_name", ""),
        clinic_id=data.get("clinic_id"),
    )


def _assign_clinic_sync(uid: str, clinic_id: str) -> dict:
    ref = get_firestore().collection(USERS_COLLECTION).document(uid)
    snapshot = ref.get()
    if not snapshot.exists:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"user {uid} does not exist")
    data = snapshot.to_dict()
    if not data.get("is_active", False) or data.get("role") == StaffRole.it_admin.value:
        # Admins work across every clinic, so they are never bound to one.
        raise AppError(ErrorCode.VALIDATION_FAILED, "Only active clinic staff can be added to a clinic.")
    ref.update({"clinic_id": clinic_id, "updated_at": _utcnow()})
    return {**data, "clinic_id": clinic_id}


def _list_clinic_staff_sync(clinic_id: str) -> list[tuple[str, dict]]:
    query = get_firestore().collection(USERS_COLLECTION).where(filter=FieldFilter("clinic_id", "==", clinic_id))
    return [(s.id, s.to_dict()) for s in query.stream() if s.to_dict().get("is_active", False)]


async def assign_clinic(uid: str, clinic_id: str) -> tuple[str, dict]:
    # The new clinic reaches the user's token the next time they sign in or refresh.
    return uid, await asyncio.to_thread(_assign_clinic_sync, uid, clinic_id)


async def list_clinic_staff(clinic_id: str, roles: set[str] | None = None) -> list[tuple[str, dict]]:
    staff = await asyncio.to_thread(_list_clinic_staff_sync, clinic_id)
    return [(uid, d) for uid, d in staff if roles is None or d.get("role") in roles]
