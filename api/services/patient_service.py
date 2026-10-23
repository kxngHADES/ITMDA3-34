import asyncio
import datetime as dt
import logging
from typing import Any

from core.config import settings
from core.firebase import delete_auth_user, get_firestore, set_role_claim
from schemas import AppError, ErrorCode, PriorityCategory, Role
from schemas.db.documents import PatientDoc, PatientPriorityDoc, TicketPriorityDoc, utcnow
from schemas.patients import ACCOUNT_CATEGORIES, PatientRegisterRequest, UpdatePatientRequest
from services import crypto_service, firebase_auth_service

PATIENTS_COLLECTION = "patients"

logger = logging.getLogger(__name__)


def priority_valid_until(category: PriorityCategory, confirmed_at: dt.datetime) -> dt.datetime | None:
    # Elderly and disability last for good, a pregnancy covers the pregnancy and the postnatal weeks.
    if category is PriorityCategory.pregnant:
        return confirmed_at + dt.timedelta(days=settings.PREGNANCY_PRIORITY_DAYS)
    return None


def _ref(uid: str):
    return get_firestore().collection(PATIENTS_COLLECTION).document(uid)


def _find_sync(uid: str) -> PatientDoc | None:
    snapshot = _ref(uid).get()
    return PatientDoc.from_firestore(uid, snapshot.to_dict()) if snapshot.exists else None


def _create_sync(uid: str, email: str, body: PatientRegisterRequest) -> PatientDoc:
    category = body.priority_request or PriorityCategory.none
    doc = PatientDoc(
        id=uid,
        email=email,
        display_name=body.display_name,
        language=body.language,
        notification_channel=body.notification_channel,
        phone_ciphertext=crypto_service.encrypt(body.phone) if body.phone else None,
        phone_last4=body.phone[-4:] if body.phone else None,
        priority=PatientPriorityDoc(category=category),
    )
    _ref(uid).set(doc.to_firestore())
    set_role_claim(uid, Role.patient.value)
    return doc


def _touch_last_sign_in_sync(uid: str) -> None:
    _ref(uid).update({"last_sign_in_at": utcnow()})


def _update_sync(uid: str, body: UpdatePatientRequest) -> PatientDoc:
    doc = _find_sync(uid)
    if doc is None or not doc.is_active:
        raise AppError(ErrorCode.FORBIDDEN, detail=f"no active patient profile for {uid}")
    changes = body.model_dump(exclude_unset=True)
    stored: dict[str, Any] = {}
    for name in ("display_name", "language", "notification_channel"):
        if name in changes:
            stored[name] = changes[name]
    if "phone" in changes:
        phone = changes["phone"]
        stored["phone_ciphertext"] = crypto_service.encrypt(phone) if phone else None
        stored["phone_last4"] = phone[-4:] if phone else None
    if "priority_request" in changes:
        category = changes["priority_request"] or PriorityCategory.none
        if category is not doc.priority.category:
            # A new category has to be confirmed by staff again, clearing it removes it.
            stored["priority"] = PatientPriorityDoc(category=category).model_dump()

    channel = stored.get("notification_channel", doc.notification_channel)
    has_phone = stored.get("phone_last4", doc.phone_last4) is not None
    if channel in ("sms", "whatsapp") and not has_phone:
        raise AppError(ErrorCode.VALIDATION_FAILED, "Add a phone number to get SMS or WhatsApp alerts.")

    if stored:
        stored["updated_at"] = utcnow()
        _ref(uid).update(stored)
    return PatientDoc.from_firestore(uid, doc.model_dump() | stored)


def _record_decision_sync(uid: str, category: PriorityCategory, confirmed: bool, staff_uid: str) -> None:
    doc = _find_sync(uid)
    if doc is None:
        # Anonymous patients have no account to remember the decision on.
        return
    current = doc.priority
    now = utcnow()
    if confirmed:
        if category not in ACCOUNT_CATEGORIES:
            return
        if current.category is category and current.is_active(now):
            # Already on the account, confirming it again on a later visit does not extend it.
            return
        priority = PatientPriorityDoc(
            category=category,
            verified=True,
            verified_by=staff_uid,
            verified_at=now,
            valid_until=priority_valid_until(category, now),
        )
    elif current.category is category and current.verified:
        # Staff turned it down on a ticket, so new tickets stop joining the priority lane.
        priority = PatientPriorityDoc(category=category)
    else:
        return
    _ref(uid).update({"priority": priority.model_dump(), "updated_at": now})


async def register(body: PatientRegisterRequest) -> PatientDoc:
    try:
        firebase_user = await firebase_auth_service.sign_up_with_password(body.email, body.password)
    except AppError as exc:
        if exc.code is ErrorCode.EMAIL_IN_USE:
            raise AppError(ErrorCode.EMAIL_IN_USE, "An account already uses this email address.", detail=exc.detail) from None
        raise
    try:
        return await asyncio.to_thread(_create_sync, firebase_user.uid, firebase_user.email, body)
    except Exception:
        # No half-made account: the email would be taken but the patient could never sign in.
        await asyncio.to_thread(delete_auth_user, firebase_user.uid)
        raise


async def find_patient(uid: str) -> PatientDoc | None:
    return await asyncio.to_thread(_find_sync, uid)


async def get_active_patient(uid: str, *, signing_in: bool = False) -> PatientDoc:
    doc = await find_patient(uid)
    if doc is None or not doc.is_active:
        # A staff member's Firebase account has no patient profile, so it cannot sign in here.
        raise AppError(ErrorCode.FORBIDDEN, detail=f"no active patient profile for {uid}")
    if signing_in:
        await asyncio.to_thread(_touch_last_sign_in_sync, uid)
    return doc


async def update_patient(uid: str, body: UpdatePatientRequest) -> PatientDoc:
    return await asyncio.to_thread(_update_sync, uid, body)


async def record_priority_decision(uid: str, category: PriorityCategory, confirmed: bool, staff_uid: str) -> None:
    # Called after staff confirm or reject priority on a ticket. Never fails the staff action.
    try:
        await asyncio.to_thread(_record_decision_sync, uid, category, confirmed, staff_uid)
    except Exception:
        logger.warning("Could not save the priority decision on patient %s", uid, exc_info=True)


def ticket_priority(patient: PatientDoc, requested: PriorityCategory | None) -> TicketPriorityDoc:
    # The priority a new ticket starts with. A verified account category is confirmed straight away.
    p = patient.priority
    category = requested or p.category
    if category is p.category and p.is_active():
        return TicketPriorityDoc(
            requested_category=category, staff_confirmed=True, confirmed_by=p.verified_by, confirmed_at=p.verified_at
        )
    return TicketPriorityDoc(requested_category=category)
