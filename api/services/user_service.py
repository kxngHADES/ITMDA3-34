import asyncio
from datetime import UTC, datetime

from core.firebase import get_firestore, set_role_claim
from schemas import AppError, ErrorCode, StaffRole
from schemas.auth import AuthUser

USERS_COLLECTION = "users"


def _utcnow() -> datetime:
    return datetime.now(UTC)


def _create_user_sync(uid: str, email: str, full_name: str, role: StaffRole) -> None:
    now = _utcnow()
    get_firestore().collection(USERS_COLLECTION).document(uid).set(
        {
            "email": email,
            "full_name": full_name,
            "role": role.value,
            "is_active": True,
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


async def create_user(uid: str, email: str, full_name: str, role: StaffRole) -> AuthUser:
    # The Firestore client is blocking, so it runs off the event loop.
    await asyncio.to_thread(_create_user_sync, uid, email, full_name, role)
    return AuthUser(id=uid, email=email, role=role, full_name=full_name)


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
    )
