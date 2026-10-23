import asyncio
import logging
from dataclasses import dataclass, field

from google.cloud.firestore_v1.base_query import FieldFilter

from core.firebase import get_firestore
from schemas import NotificationChannel
from schemas.db.documents import NotificationLogDoc
from schemas.identity import NotificationKind, NotificationStatus
from services import push_service
from utils.email_helper import send_email
from utils.send_sms import send_sms

logger = logging.getLogger(__name__)

NOTIFICATION_LOG_COLLECTION = "notification_log"
# After the channel the patient picked, FCM push falls back to SMS, then email, then push.
FALLBACK_ORDER = (NotificationChannel.push, NotificationChannel.sms, NotificationChannel.email)
MAX_ERROR_CHARS = 200


@dataclass
class Message:
    # Everything needed to send one alert on any channel, so a fallback needs no rebuilding.
    kind: NotificationKind
    ticket_id: str
    clinic_id: str
    owner_uid: str | None = None
    phone: str | None = None
    email_address: str | None = None
    push_title: str = ""
    push_body: str = ""
    push_data: dict[str, str] = field(default_factory=dict)
    sms_text: str = ""
    email_subject: str = ""
    email_html: str = ""


@dataclass
class Result:
    ok: bool
    error: str | None = None
    provider_id: str | None = None


def channel_order(chosen: NotificationChannel) -> list[NotificationChannel]:
    # The patient's pick first, then the others in fallback order. Nothing is sent when they chose none.
    if chosen is NotificationChannel.none:
        return []
    return [chosen, *(c for c in FALLBACK_ORDER if c is not chosen)]


async def _send(channel: NotificationChannel, m: Message, *, chosen: bool) -> Result | None:
    # None means the channel cannot be used for this patient. Only the chosen channel is worth logging then.
    if channel is NotificationChannel.push:
        if m.owner_uid is None:
            return Result(False, "no signed-in device for this ticket") if chosen else None
        ok, error = await push_service.send_push(m.owner_uid, m.push_title, m.push_body, m.push_data)
        return Result(ok, error)
    if channel is NotificationChannel.sms:
        if not m.phone:
            return Result(False, "no phone number") if chosen else None
        ok = await send_sms(m.phone, m.sms_text)
        return Result(ok, None if ok else "the SMS gateway did not accept the message")
    if channel is NotificationChannel.email:
        if not m.email_address:
            return Result(False, "no email address") if chosen else None
        try:
            response = await send_email(to=m.email_address, subject=m.email_subject, html=m.email_html)
        except Exception as exc:
            logger.exception("Email alert for %s failed", m.ticket_id)
            return Result(False, f"{type(exc).__name__}: {exc}"[:MAX_ERROR_CHARS])
        return Result(True, provider_id=(response or {}).get("id"))
    return Result(False, f"{channel.value} is not supported yet") if chosen else None


def _write_log_sync(doc: NotificationLogDoc) -> None:
    get_firestore().collection(NOTIFICATION_LOG_COLLECTION).document(doc.id).set(doc.to_firestore())


async def log_attempt(
    m: Message, channel: NotificationChannel, result: Result, fallback_of: str | None = None
) -> NotificationLogDoc:
    ref = get_firestore().collection(NOTIFICATION_LOG_COLLECTION).document()
    doc = NotificationLogDoc(
        id=ref.id,
        ticket_id=m.ticket_id,
        clinic_id=m.clinic_id,
        channel=channel,
        kind=m.kind,
        status=NotificationStatus.sent if result.ok else NotificationStatus.failed,
        provider_message_id=result.provider_id,
        fallback_of=fallback_of,
        error=result.error[:MAX_ERROR_CHARS] if result.error else None,
    )
    try:
        await asyncio.to_thread(_write_log_sync, doc)
    except Exception:
        logger.exception("Notification log entry for %s was not saved", m.ticket_id)
    return doc


async def notify(m: Message, chosen: NotificationChannel) -> list[NotificationLogDoc]:
    # Tries the patient's channel, then falls back until one works. Every attempt is logged, and each
    # fallback points at the failed attempt it replaced. Never raises.
    attempts: list[NotificationLogDoc] = []
    failed_id: str | None = None
    for channel in channel_order(chosen):
        try:
            result = await _send(channel, m, chosen=channel is chosen)
        except Exception as exc:
            logger.exception("%s alert for %s crashed", channel.value, m.ticket_id)
            result = Result(False, f"{type(exc).__name__}: {exc}"[:MAX_ERROR_CHARS])
        if result is None:
            continue
        entry = await log_attempt(m, channel, result, fallback_of=failed_id)
        attempts.append(entry)
        if result.ok:
            break
        failed_id = entry.id
    return attempts


def _list_for_ticket_sync(ticket_id: str) -> list[NotificationLogDoc]:
    query = get_firestore().collection(NOTIFICATION_LOG_COLLECTION).where(filter=FieldFilter("ticket_id", "==", ticket_id))
    docs = [NotificationLogDoc.from_firestore(s.id, s.to_dict()) for s in query.stream()]
    # Sorted here because ordering in the query would need a composite index.
    return sorted(docs, key=lambda d: d.created_at, reverse=True)


async def list_for_ticket(ticket_id: str) -> list[NotificationLogDoc]:
    # Newest first.
    return await asyncio.to_thread(_list_for_ticket_sync, ticket_id)
