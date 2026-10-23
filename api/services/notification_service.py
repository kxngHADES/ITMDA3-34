import asyncio
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from google.cloud import firestore
from jinja2 import Environment, FileSystemLoader, select_autoescape

from core.config import settings
from core.firebase import get_firestore
from schemas import NotificationChannel, PriorityCategory, TicketStatus
from schemas.identity import NotificationKind, NotificationStatus
from schemas.db.documents import TicketDoc
from schemas.db.mappers import wait_range
from services import clinic_service, crypto_service, notification_manager, user_service
from utils.email_helper import send_batch_emails

logger = logging.getLogger(__name__)

TEMPLATES_DIR = Path(__file__).resolve().parent.parent / "templates"
_templates = Environment(loader=FileSystemLoader(TEMPLATES_DIR), autoescape=select_autoescape(["html"]))

PRIORITY_RECIPIENT_ROLES = {"receptionist", "nurse", "manager"}
CATEGORY_LABELS = {
    PriorityCategory.elderly: "Elderly",
    PriorityCategory.pregnant: "Pregnant",
    PriorityCategory.disability: "Disability",
    PriorityCategory.urgent: "Urgent",
}


@dataclass
class Alert:
    kind: NotificationKind
    ticket_id: str
    queue_id: str
    clinic_id: str
    service_point_id: str
    owner_uid: str | None
    channel: NotificationChannel
    display_number: str
    display_name: str | None
    counter_label: str | None
    position: int | None
    wait_min: int | None
    wait_max: int | None
    phone_ciphertext: bytes | None
    email_ciphertext: bytes | None


def render(template: str, **context: Any) -> str:
    return _templates.get_template(template).render(**context)


def claim_alerts(queue_id: str, data: dict[str, Any]) -> list[Alert]:
    # Finds tickets that are being called or are nearly up and marks them as told, so each alert goes once.
    from services import queue_service

    order = queue_service.effective_queue(data).order
    base_seconds, cold_start = queue_service.base_time(data)
    alerts = []
    for ticket_id, t in data.get("tickets", {}).items():
        channel = NotificationChannel(t.get("notification_channel", "push"))
        if channel is NotificationChannel.none:
            continue
        kind = position = wait = None
        if t["status"] == TicketStatus.called.value and not t.get("called_notified"):
            kind = NotificationKind.called
            t["called_notified"] = t["almost_notified"] = True
        elif t["status"] == TicketStatus.waiting.value and not t.get("almost_notified") and ticket_id in order:
            position = order.index(ticket_id) + 1
            if position > settings.ALMOST_TURN_POSITION:
                continue
            kind = NotificationKind.almost_your_turn
            t["almost_notified"] = True
            wait = wait_range(position, data.get("concurrent_servers", 1), base_seconds, cold_start)
        if kind is None:
            continue
        alerts.append(
            Alert(
                kind=kind,
                ticket_id=ticket_id,
                queue_id=queue_id,
                clinic_id=t["clinic_id"],
                service_point_id=t["service_point_id"],
                owner_uid=t.get("owner_uid"),
                channel=channel,
                display_number=t["display_number"],
                display_name=t.get("display_name"),
                counter_label=t.get("counter_label"),
                position=position,
                wait_min=wait.min_minutes if wait else None,
                wait_max=wait.max_minutes if wait else None,
                phone_ciphertext=t.get("phone_ciphertext"),
                email_ciphertext=t.get("email_ciphertext"),
            )
        )
    return alerts


def _claim_sync(queue_id: str) -> list[Alert]:
    from services import queue_service

    ref = get_firestore().collection(queue_service.QUEUES_COLLECTION).document(queue_id)

    @firestore.transactional
    def run(txn: firestore.Transaction) -> list[Alert]:
        snapshot = ref.get(transaction=txn)
        if not snapshot.exists:
            return []
        data = snapshot.to_dict()
        alerts = claim_alerts(queue_id, data)
        if alerts:
            txn.update(ref, {"tickets": data["tickets"]})
        return alerts

    return run(get_firestore().transaction())


async def _names(clinic_id: str, service_point_id: str) -> tuple[str, str]:
    try:
        clinic = await clinic_service.get_clinic(clinic_id)
        service = await clinic_service.get_service_point(service_point_id)
        return clinic.name, service.name
    except Exception:
        logger.warning("Could not look up names for %s / %s", clinic_id, service_point_id, exc_info=True)
        return "the clinic", "your service"


def _push_text(alert: Alert, clinic_name: str) -> tuple[str, str]:
    if alert.kind is NotificationKind.called:
        where = f"Please go to {alert.counter_label}." if alert.counter_label else "Please go in now."
        return "It's your turn", f"Ticket {alert.display_number}. {where}"
    ahead = (alert.position or 1) - 1
    who = "You are next" if ahead == 0 else f"{ahead} ahead of you"
    return "Almost your turn", f"Ticket {alert.display_number} at {clinic_name}. {who}, please head to the clinic."


def _sms_text(alert: Alert, clinic_name: str) -> str:
    if alert.kind is NotificationKind.called:
        where = f"Go to {alert.counter_label}." if alert.counter_label else "Please go in now."
        text = f"SwiftQ: It's your turn! Ticket {alert.display_number} at {clinic_name}. {where}"
    else:
        wait = f" Wait {alert.wait_min}-{alert.wait_max} min." if alert.wait_min is not None else ""
        text = f"SwiftQ: Almost your turn. Ticket {alert.display_number} at {clinic_name}.{wait} Please head to the clinic."
    return text[:160]


def _email_parts(alert: Alert, clinic_name: str, service_name: str) -> tuple[str, str]:
    context = dict(
        display_number=alert.display_number,
        display_name=alert.display_name,
        clinic_name=clinic_name,
        service_name=service_name,
        counter_label=alert.counter_label,
        people_ahead=max((alert.position or 1) - 1, 0),
        wait_min=alert.wait_min,
        wait_max=alert.wait_max,
    )
    if alert.kind is NotificationKind.called:
        return f"It's your turn, ticket {alert.display_number}", render("queue_your_turn.html", **context)
    return f"Almost your turn, ticket {alert.display_number}", render("queue_almost_turn.html", **context)


async def build_message(alert: Alert) -> notification_manager.Message:
    clinic_name, service_name = await _names(alert.clinic_id, alert.service_point_id)
    phone = crypto_service.decrypt(alert.phone_ciphertext) if alert.phone_ciphertext else None
    email = crypto_service.decrypt(alert.email_ciphertext) if alert.email_ciphertext else None
    push_title, push_body = _push_text(alert, clinic_name)
    email_subject, email_html = _email_parts(alert, clinic_name, service_name)
    return notification_manager.Message(
        kind=alert.kind,
        ticket_id=alert.ticket_id,
        clinic_id=alert.clinic_id,
        owner_uid=alert.owner_uid,
        phone=phone,
        email_address=email,
        push_title=push_title,
        push_body=push_body,
        push_data={"kind": alert.kind.value, "ticket_id": alert.ticket_id, "queue_id": alert.queue_id},
        sms_text=_sms_text(alert, clinic_name),
        email_subject=email_subject,
        email_html=email_html,
    )


async def deliver(alert: Alert) -> bool:
    # Sends one alert on the patient's channel, falling back to the others, and logs every attempt.
    attempts = await notification_manager.notify(await build_message(alert), alert.channel)
    return bool(attempts) and attempts[-1].status is NotificationStatus.sent


async def dispatch_queue_events(queue_id: str) -> None:
    # Runs after every queue change. Never raises, because it is a side effect of the request.
    try:
        alerts = await asyncio.to_thread(_claim_sync, queue_id)
        for alert in alerts:
            if not await deliver(alert):
                logger.warning("%s alert for %s was not delivered", alert.kind.value, alert.ticket_id)
    except Exception:
        logger.exception("Queue notifications for %s failed", queue_id)


async def notify_priority_request(ticket: TicketDoc, service_name: str, clinic_name: str) -> None:
    # Tells the clinic's receptionists, nurses and managers that a patient asked for priority.
    try:
        staff = await user_service.list_clinic_staff(ticket.clinic_id, PRIORITY_RECIPIENT_ROLES)
        emails = [d["email"] for _, d in staff if d.get("email")]
        if not emails:
            logger.info("No staff are bound to clinic %s, priority request %s not emailed", ticket.clinic_id, ticket.id)
            return
        category = ticket.priority.requested_category
        label = CATEGORY_LABELS.get(category, category.value.title())
        html = render(
            "priority_request.html",
            display_number=ticket.display_number,
            display_name=ticket.display_name,
            category_label=label,
            service_name=service_name,
            clinic_name=clinic_name,
        )
        subject = f"Priority request: {label}, ticket {ticket.display_number}"
        message = notification_manager.Message(
            kind=NotificationKind.priority_requested, ticket_id=ticket.id, clinic_id=ticket.clinic_id
        )
        try:
            # One email each, so staff do not see each other's addresses.
            await send_batch_emails([{"to": [email], "subject": subject, "html": html} for email in emails])
        except Exception as exc:
            logger.exception("Priority request email for %s failed", ticket.id)
            result = notification_manager.Result(False, f"{type(exc).__name__}: {exc}")
        else:
            result = notification_manager.Result(True)
        await notification_manager.log_attempt(message, NotificationChannel.email, result)
    except Exception:
        logger.exception("Priority request email for %s failed", ticket.id)
