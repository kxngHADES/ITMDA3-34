import asyncio
import logging
from datetime import UTC, datetime

from firebase_admin import auth
from google.cloud.firestore_v1.base_query import FieldFilter

from core.firebase import (
    create_auth_user,
    delete_auth_user,
    get_firestore,
    password_setup_link,
    revoke_refresh_tokens,
    set_auth_disabled,
    set_role_claim,
    update_auth_display_name,
)
from core.templates import templates
from schemas import AppError, CreateStaffRequest, ErrorCode, StaffRole
from schemas.auth import AuthUser
from utils.email_helper import send_email

USERS_COLLECTION = "users"

logger = logging.getLogger(__name__)


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


def _create_staff_sync(body: CreateStaffRequest) -> tuple[str, dict]:
    try:
        uid = create_auth_user(body.email, body.display_name)
    except auth.EmailAlreadyExistsError:
        raise AppError(ErrorCode.EMAIL_IN_USE, detail=body.email) from None
    try:
        _create_user_sync(uid, body.email, body.display_name, body.role, body.clinic_id)
    except Exception:
        # No half-made account: without a profile the Firebase user could never sign in anyway.
        delete_auth_user(uid)
        raise
    return uid, _get_user_sync(uid)


def _get_staff_sync(uid: str) -> dict:
    data = _get_user_sync(uid)
    if data is None:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"user {uid} does not exist")
    return data


def _list_staff_sync(clinic_id: str | None) -> list[tuple[str, dict]]:
    users = get_firestore().collection(USERS_COLLECTION)
    query = users.where(filter=FieldFilter("clinic_id", "==", clinic_id)) if clinic_id else users
    return sorted(((s.id, s.to_dict()) for s in query.stream()), key=lambda r: r[1].get("email", ""))


def _update_staff_sync(uid: str, changes: dict) -> dict:
    ref = get_firestore().collection(USERS_COLLECTION).document(uid)
    snapshot = ref.get()
    if not snapshot.exists:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"user {uid} does not exist")
    before = snapshot.to_dict()
    stored = {("full_name" if k == "display_name" else k): v for k, v in changes.items()}
    if "role" in stored:
        stored["role"] = stored["role"].value
    ref.update(stored | {"updated_at": _utcnow()})
    after = before | stored
    if stored.get("role", before["role"]) != before["role"]:
        set_role_claim(uid, stored["role"])
    if "full_name" in stored:
        update_auth_display_name(uid, stored["full_name"])
    if "is_active" in stored:
        set_auth_disabled(uid, not stored["is_active"])
    # A changed role, clinic or deactivation must not wait for the old refresh token to expire.
    if {"role", "clinic_id", "is_active"} & stored.keys():
        revoke_refresh_tokens(uid)
    return after


async def create_staff(body: CreateStaffRequest) -> tuple[str, dict]:
    return await asyncio.to_thread(_create_staff_sync, body)


async def get_staff(uid: str) -> tuple[str, dict]:
    return uid, await asyncio.to_thread(_get_staff_sync, uid)


async def list_staff(clinic_id: str | None = None) -> list[tuple[str, dict]]:
    return await asyncio.to_thread(_list_staff_sync, clinic_id)


async def update_staff(uid: str, changes: dict) -> tuple[str, dict]:
    # `changes` is UpdateStaffRequest.model_dump(exclude_unset=True).
    return uid, await asyncio.to_thread(_update_staff_sync, uid, changes)


async def assign_clinic(uid: str, clinic_id: str) -> tuple[str, dict]:
    # The new clinic reaches the user's token the next time they sign in or refresh.
    return uid, await asyncio.to_thread(_assign_clinic_sync, uid, clinic_id)


async def list_clinic_staff(clinic_id: str, roles: set[str] | None = None) -> list[tuple[str, dict]]:
    staff = await asyncio.to_thread(_list_clinic_staff_sync, clinic_id)
    return [(uid, d) for uid, d in staff if roles is None or d.get("role") in roles]


async def send_staff_invite(email: str, display_name: str, role: StaffRole, clinic_name: str) -> bool:
    # The account already exists, so a failed email is reported to the caller instead of undoing it.
    try:
        link = await asyncio.to_thread(password_setup_link, email)
        html = templates.get_template("staff_invite.html").render(
            display_name=display_name, role=role.value.replace("_", " "), clinic_name=clinic_name, link=link
        )
        await send_email(to=email, subject="Set up your SwiftQ account", html=html)
        return True
    except Exception:
        logger.warning("Could not send the invite to %s", email, exc_info=True)
        return False
