import asyncio
import datetime as dt
import logging
import uuid
from collections.abc import AsyncIterator, Callable
from typing import Any

from google.cloud import firestore
from redis.exceptions import RedisError

from core.clock import now_sast, service_date
from core.firebase import get_firestore
from core.redis import get_redis
from schemas import (
    DEFAULT_QUEUE_CONFIG,
    AppError,
    CallNextRequest,
    ConfirmPriorityRequest,
    ErrorCode,
    JoinChannel,
    JoinQueueRequest,
    NotificationChannel,
    PauseQueueRequest,
    PriorityCategory,
    Queue,
    QueueConfig,
    QueueEntry,
    QueueStatus,
    Ticket,
    TicketStatus,
    TransferRequest,
    WalkInRequest,
)
from schemas.db.documents import (
    ClinicDoc,
    QueueDoc,
    ServicePointDoc,
    TicketDoc,
    TicketPriorityDoc,
    utcnow,
)
from schemas.db.mappers import (
    clinic_is_open,
    queue_entry,
    queue_to_api,
    ticket_to_api,
    wait_range,
)
from services import clinic_service, crypto_service, notification_service
from services.queue_engine import Candidate, EffectiveQueue, recompute_effective_queue

logger = logging.getLogger(__name__)

QUEUES_COLLECTION = "queues"
ACTIVE = (TicketStatus.waiting.value, TicketStatus.called.value)
# A patient who is not there when called goes back in the queue until this many no-shows.
MAX_NO_SHOWS = 4
CHANGES_INTERVAL_SECONDS = 15


def queue_id_for(clinic_id: str, service_point_id: str, day: dt.date) -> str:
    # One document per service point per day, named date-clinic-service_point.
    return f"{day.isoformat()}-{clinic_id}-{service_point_id}"


def parse_queue_id(queue_id: str) -> tuple[dt.date, str, str]:
    # Clinic and service point ids are Firestore auto ids, which never contain a dash.
    malformed = AppError(ErrorCode.NOT_FOUND, detail=f"malformed queue id {queue_id}")
    try:
        day = dt.date.fromisoformat(queue_id[:10])
    except ValueError:
        raise malformed from None
    if queue_id[10:11] != "-":
        raise malformed
    clinic_id, sep, service_point_id = queue_id[11:].partition("-")
    if not sep or not clinic_id or not service_point_id or "-" in service_point_id:
        raise malformed
    return day, clinic_id, service_point_id


def new_ticket_id(queue_id: str) -> str:
    # The queue id is part of the ticket id so a ticket can be found without an index.
    return f"{queue_id}~{uuid.uuid4().hex[:10]}"


def queue_id_of_ticket(ticket_id: str) -> str:
    queue_id, sep, _ = ticket_id.rpartition("~")
    if not sep or not queue_id:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"malformed ticket id {ticket_id}")
    return queue_id


def display_number(prefix: str, sequence_no: int) -> str:
    # Short and easy to read out loud, e.g. A-042.
    return f"{prefix}-{sequence_no:03d}"


def _channel(queue_id: str) -> str:
    return f"queue:{queue_id}:changes"


def _ticket_doc(ticket_id: str, data: dict[str, Any]) -> TicketDoc:
    return TicketDoc.from_firestore(ticket_id, data)


def _check_scope(queue_id: str, clinic_scope: str | None) -> None:
    # Staff tied to a clinic cannot touch another clinic's queue.
    if clinic_scope is not None and parse_queue_id(queue_id)[1] != clinic_scope:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"queue {queue_id} is outside clinic {clinic_scope}")


def _config(data: dict[str, Any]) -> QueueConfig:
    stored = data.get("config")
    return QueueConfig.model_validate(stored) if stored else DEFAULT_QUEUE_CONFIG


def effective_queue(data: dict[str, Any], now: dt.datetime | None = None) -> EffectiveQueue:
    # The order depends on how long each ticket has waited, so it is worked out on every read.
    now = now or utcnow()
    candidates = []
    for ticket_id, t in data.get("tickets", {}).items():
        if t["status"] != TicketStatus.waiting.value:
            continue
        joined_at = t.get("joined_at")
        priority = t.get("priority") or {}
        candidates.append(
            Candidate(
                id=ticket_id,
                sequence_no=t["sequence_no"],
                waited_minutes=(now - joined_at).total_seconds() / 60 if joined_at else 0,
                category=PriorityCategory(priority.get("requested_category", "none")),
                staff_confirmed=bool(priority.get("staff_confirmed")),
                late=bool(t.get("late_flag")),
            )
        )
    return recompute_effective_queue(candidates, _config(data))


def position_from_doc(data: dict[str, Any], ticket_id: str, now: dt.datetime | None = None) -> int | None:
    order = effective_queue(data, now).order
    return order.index(ticket_id) + 1 if ticket_id in order else None


def _new_queue_data(
    queue_id: str, clinic: ClinicDoc, service_point: ServicePointDoc, day: dt.date
) -> dict[str, Any]:
    queue = QueueDoc(
        id=queue_id,
        service_point_id=service_point.id,
        clinic_id=clinic.id,
        service_date=day,
        display_prefix=service_point.display_prefix,
        concurrent_servers=service_point.concurrent_servers,
        opened_by="system",
    )
    # The tickets live inside the same document, keyed by ticket id. The service point's queue
    # configuration is copied in so the day's ordering rules do not change halfway through.
    return queue.to_firestore() | {
        "service_date": day.isoformat(),
        "config": service_point.config.model_dump(mode="json"),
        # The service point's rolling average is copied in so reads need no second document.
        "base_service_seconds": service_point.base_service_seconds,
        "base_service_samples": service_point.base_service_samples,
        "tickets": {},
    }


def base_time(data: dict[str, Any]) -> tuple[float, bool]:
    # B(sp) and whether it is still the cold-start default, until enough samples exist.
    config = _config(data)
    seconds = data.get("base_service_seconds")
    if seconds is None or data.get("base_service_samples", 0) < config.min_samples_for_ema:
        return float(config.default_base_time_seconds), True
    return float(seconds), False


def record_sample(data: dict[str, Any], sample_seconds: float) -> None:
    # B = alpha x latest_sample + (1 - alpha) x B_previous, the first sample starts the average.
    alpha = _config(data).ema_alpha
    previous = data.get("base_service_seconds")
    data["base_service_seconds"] = (
        sample_seconds if previous is None else alpha * sample_seconds + (1 - alpha) * previous
    )
    data["base_service_samples"] = data.get("base_service_samples", 0) + 1


def _add_ticket(
    data: dict[str, Any],
    *,
    owner_uid: str | None,
    channel: JoinChannel,
    created_by_staff_uid: str | None = None,
    display_name: str | None,
    phone: str | None,
    email: str | None = None,
    language: str,
    notification_channel: NotificationChannel | None,
    category: PriorityCategory | None,
    confirmed_by: str | None = None,
) -> TicketDoc:
    queue_id = data["queue_id"]
    sequence_no = data.get("ticket_count", 0) + 1
    category = category or PriorityCategory.none
    priority = TicketPriorityDoc(requested_category=category)
    if confirmed_by and category is not PriorityCategory.none:
        # A category typed in by staff counts as confirmed straight away.
        priority.staff_confirmed = True
        priority.confirmed_by = confirmed_by
        priority.confirmed_at = utcnow()
    doc = TicketDoc(
        id=new_ticket_id(queue_id),
        owner_uid=owner_uid,
        created_by_staff_uid=created_by_staff_uid,
        clinic_id=data["clinic_id"],
        service_point_id=data["service_point_id"],
        queue_id=queue_id,
        sequence_no=sequence_no,
        display_number=display_number(data.get("display_prefix", "A"), sequence_no),
        channel=channel,
        priority=priority,
        display_name=display_name,
        phone_ciphertext=crypto_service.encrypt(phone) if phone else None,
        phone_last4=phone[-4:] if phone else None,
        email_ciphertext=crypto_service.encrypt(email) if email else None,
        notification_channel=notification_channel or NotificationChannel.push,
        language=language,
    )
    data["tickets"][doc.id] = doc.to_firestore()
    data["ticket_count"] = sequence_no
    return doc


def _create_sync(
    queue_id: str,
    clinic: ClinicDoc,
    service_point: ServicePointDoc,
    day: dt.date,
    fn: Callable[[dict[str, Any]], Any],
) -> tuple[Any, dict[str, Any]]:
    # Runs fn on today's queue document, creating the document first if nobody has joined yet.
    ref = get_firestore().collection(QUEUES_COLLECTION).document(queue_id)

    @firestore.transactional
    def run(txn: firestore.Transaction):
        snapshot = ref.get(transaction=txn)
        data = snapshot.to_dict() if snapshot.exists else _new_queue_data(queue_id, clinic, service_point, day)
        data["queue_id"] = queue_id
        data.setdefault("tickets", {})
        result = fn(data)
        data["updated_at"] = utcnow()
        txn.set(ref, data)
        return result, data

    return run(get_firestore().transaction())


def _mutate_sync(queue_id: str, fn: Callable[[dict[str, Any]], Any]) -> tuple[Any, dict[str, Any]]:
    # Runs fn on an existing queue document. fn raising an AppError discards every change.
    ref = get_firestore().collection(QUEUES_COLLECTION).document(queue_id)

    @firestore.transactional
    def run(txn: firestore.Transaction):
        snapshot = ref.get(transaction=txn)
        if not snapshot.exists:
            raise AppError(ErrorCode.NOT_FOUND, detail=f"queue {queue_id} does not exist")
        data = snapshot.to_dict()
        data["queue_id"] = queue_id
        data.setdefault("tickets", {})
        result = fn(data)
        data["updated_at"] = utcnow()
        txn.set(ref, data)
        return result, data

    return run(get_firestore().transaction())


def _load_queue_sync(queue_id: str) -> dict[str, Any] | None:
    snapshot = get_firestore().collection(QUEUES_COLLECTION).document(queue_id).get()
    return snapshot.to_dict() if snapshot.exists else None


async def _after_change(queue_id: str, data: dict[str, Any]) -> None:
    # Wakes every live queue listener.
    try:
        await get_redis().publish(_channel(queue_id), "changed")
    except RedisError:
        logger.warning("Redis unavailable, listeners not notified", exc_info=True)
    # Patients near the front or being called are told on their chosen channel, off the request path.
    _spawn(notification_service.dispatch_queue_events(queue_id))


_background_tasks: set[asyncio.Task] = set()


def _spawn(coro) -> None:
    task = asyncio.get_running_loop().create_task(coro)
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)


def _to_api(doc: TicketDoc, data: dict[str, Any]) -> Ticket:
    position = None
    wait = None
    if doc.status is TicketStatus.waiting:
        position = position_from_doc(data, doc.id)
        if position is not None:
            base_seconds, cold_start = base_time(data)
            wait = wait_range(position, data.get("concurrent_servers", 1), base_seconds, cold_start)
    return ticket_to_api(doc, position=position, wait=wait)


async def _open_service_point(clinic_id: str, service_point_id: str) -> tuple[ClinicDoc, ServicePointDoc]:
    clinic = await clinic_service.get_clinic(clinic_id)
    service_point = await clinic_service.get_service_point(service_point_id)
    if not clinic.is_active or service_point.clinic_id != clinic.id:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"{service_point_id} is not an active part of {clinic_id}")
    return clinic, service_point


def _todays_queue(queue_id: str) -> tuple[dt.date, str, str]:
    day, clinic_id, service_point_id = parse_queue_id(queue_id)
    if day != service_date():
        # Only today's queue accepts new tickets.
        raise AppError(ErrorCode.NOT_FOUND, detail=f"queue {queue_id} is not today's queue")
    return day, clinic_id, service_point_id


async def join_queue(queue_id: str, uid: str, body: JoinQueueRequest) -> tuple[Ticket, bool]:
    # Returns the ticket and whether it was newly created.
    day, clinic_id, service_point_id = _todays_queue(queue_id)
    clinic, service_point = await _open_service_point(clinic_id, service_point_id)
    if not clinic_is_open(clinic, now_sast()):
        raise AppError(ErrorCode.CLINIC_CLOSED)
    if not service_point.is_active:
        raise AppError(ErrorCode.SERVICE_POINT_INACTIVE)

    def join(data: dict[str, Any]) -> tuple[TicketDoc, bool]:
        # A patient holds one active ticket per clinic, joining again returns it.
        for existing_id, existing in data["tickets"].items():
            if existing.get("owner_uid") == uid and existing["status"] in ACTIVE:
                return _ticket_doc(existing_id, existing), False
        if data["status"] == QueueStatus.paused.value:
            raise AppError(ErrorCode.QUEUE_PAUSED)
        if data["status"] == QueueStatus.closed.value:
            raise AppError(ErrorCode.QUEUE_CLOSED)
        doc = _add_ticket(
            data,
            owner_uid=uid,
            channel=JoinChannel.app,
            display_name=body.display_name,
            phone=body.phone,
            email=body.email,
            language=body.language,
            notification_channel=body.notification_channel,
            category=body.priority_request,
        )
        return doc, True

    (doc, created), data = await asyncio.to_thread(_create_sync, queue_id, clinic, service_point, day, join)
    if created:
        await _after_change(queue_id, data)
        if doc.priority.requested_category is not PriorityCategory.none:
            _spawn(notification_service.notify_priority_request(doc, service_point.name, clinic.name))
    return _to_api(doc, data), created


async def add_walk_in(
    queue_id: str, staff_uid: str, body: WalkInRequest, clinic_scope: str | None = None
) -> Ticket:
    _check_scope(queue_id, clinic_scope)
    day, clinic_id, service_point_id = _todays_queue(queue_id)
    clinic, service_point = await _open_service_point(clinic_id, service_point_id)
    if not service_point.is_active:
        raise AppError(ErrorCode.SERVICE_POINT_INACTIVE)

    def walk_in(data: dict[str, Any]) -> TicketDoc:
        if data["status"] == QueueStatus.closed.value:
            raise AppError(ErrorCode.QUEUE_CLOSED)
        return _add_ticket(
            data,
            owner_uid=None,
            channel=JoinChannel.staff_assisted,
            created_by_staff_uid=staff_uid,
            display_name=body.display_name,
            phone=body.phone,
            email=body.email,
            language=body.language,
            notification_channel=body.notification_channel,
            category=body.priority_category,
            confirmed_by=staff_uid,
        )

    doc, data = await asyncio.to_thread(_create_sync, queue_id, clinic, service_point, day, walk_in)
    await _after_change(queue_id, data)
    return _to_api(doc, data)


async def call_next(
    queue_id: str, staff_uid: str, body: CallNextRequest | None, clinic_scope: str | None = None
) -> Ticket:
    _check_scope(queue_id, clinic_scope)

    def call(data: dict[str, Any]) -> TicketDoc:
        if data["status"] == QueueStatus.paused.value:
            raise AppError(ErrorCode.QUEUE_PAUSED)
        if data["status"] == QueueStatus.closed.value:
            raise AppError(ErrorCode.QUEUE_CLOSED)
        order = effective_queue(data).order
        if not order:
            raise AppError(ErrorCode.QUEUE_EMPTY)
        head = data["tickets"][order[0]]
        now = utcnow()
        head.update(
            status=TicketStatus.called.value,
            called_at=now,
            called_by=staff_uid,
            counter_label=body.counter_label if body else None,
            updated_at=now,
        )
        data["now_serving"] = head["display_number"]
        return _ticket_doc(order[0], head)

    doc, data = await asyncio.to_thread(_mutate_sync, queue_id, call)
    await _after_change(queue_id, data)
    return _to_api(doc, data)


def _save_base_time_sync(service_point_id: str, seconds: float, samples: int) -> None:
    ref = get_firestore().collection(clinic_service.SERVICE_POINTS_COLLECTION).document(service_point_id)
    ref.update({"base_service_seconds": seconds, "base_service_samples": samples})


async def complete_ticket(ticket_id: str, clinic_scope: str | None = None) -> Ticket:
    # Ends the consultation and feeds how long it took into the service point's average.
    queue_id = queue_id_of_ticket(ticket_id)
    _check_scope(queue_id, clinic_scope)

    def complete(data: dict[str, Any]) -> TicketDoc:
        ticket = data["tickets"].get(ticket_id)
        if ticket is None:
            raise AppError(ErrorCode.NOT_FOUND, detail=f"ticket {ticket_id} not found")
        if ticket["status"] != TicketStatus.called.value:
            raise AppError(ErrorCode.INVALID_TRANSITION, detail=f"complete from {ticket['status']}")
        now = utcnow()
        seconds = max(int((now - ticket["called_at"]).total_seconds()), 1)
        ticket.update(
            status=TicketStatus.served.value, consultation_seconds=seconds, closed_at=now, updated_at=now
        )
        record_sample(data, seconds)
        return _ticket_doc(ticket_id, ticket)

    doc, data = await asyncio.to_thread(_mutate_sync, queue_id, complete)
    # The queue document already holds the new average, so this copy is safe to repeat.
    try:
        await asyncio.to_thread(
            _save_base_time_sync, doc.service_point_id, data["base_service_seconds"], data["base_service_samples"]
        )
    except Exception:
        logger.warning("Could not save base time for %s", doc.service_point_id, exc_info=True)
    await _after_change(queue_id, data)
    return _to_api(doc, data)


def move_ticket(source: dict[str, Any], target: dict[str, Any], ticket_id: str) -> TicketDoc:
    # The ticket id holds its queue id, so a transfer closes the old ticket and opens a new one
    # in the target queue. It keeps the original join time, priority and contact details.
    ticket = source["tickets"].get(ticket_id)
    if ticket is None:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"ticket {ticket_id} not found")
    if ticket["status"] not in ACTIVE:
        raise AppError(ErrorCode.INVALID_TRANSITION, detail=f"transfer from {ticket['status']}")
    if target["status"] == QueueStatus.closed.value:
        raise AppError(ErrorCode.QUEUE_CLOSED)
    now = utcnow()
    sequence_no = target.get("ticket_count", 0) + 1
    new_id = new_ticket_id(target["queue_id"])
    moved = ticket | {
        "queue_id": target["queue_id"],
        "service_point_id": target["service_point_id"],
        "sequence_no": sequence_no,
        "display_number": display_number(target.get("display_prefix", "A"), sequence_no),
        "status": TicketStatus.waiting.value,
        "called_at": None,
        "called_by": None,
        "counter_label": None,
        "almost_notified": False,
        "called_notified": False,
        "updated_at": now,
    }
    target["tickets"][new_id] = moved
    target["ticket_count"] = sequence_no
    ticket.update(status=TicketStatus.cancelled.value, closed_at=now, updated_at=now)
    return _ticket_doc(new_id, moved)


def _transfer_sync(
    source_id: str,
    target_id: str,
    ticket_id: str,
    clinic: ClinicDoc,
    service_point: ServicePointDoc,
    day: dt.date,
) -> tuple[TicketDoc, dict[str, Any], dict[str, Any]]:
    # Both queue documents change in one transaction so a ticket is never in two queues or none.
    db = get_firestore()
    source_ref = db.collection(QUEUES_COLLECTION).document(source_id)
    target_ref = db.collection(QUEUES_COLLECTION).document(target_id)

    @firestore.transactional
    def run(txn: firestore.Transaction):
        source_snapshot = source_ref.get(transaction=txn)
        if not source_snapshot.exists:
            raise AppError(ErrorCode.NOT_FOUND, detail=f"queue {source_id} does not exist")
        target_snapshot = target_ref.get(transaction=txn)
        source = source_snapshot.to_dict()
        target = target_snapshot.to_dict() if target_snapshot.exists else _new_queue_data(
            target_id, clinic, service_point, day
        )
        source["queue_id"], target["queue_id"] = source_id, target_id
        source.setdefault("tickets", {})
        target.setdefault("tickets", {})
        doc = move_ticket(source, target, ticket_id)
        source["updated_at"] = target["updated_at"] = utcnow()
        txn.set(source_ref, source)
        txn.set(target_ref, target)
        return doc, source, target

    return run(db.transaction())


async def transfer_ticket(
    ticket_id: str, body: TransferRequest, clinic_scope: str | None = None
) -> Ticket:
    source_id = queue_id_of_ticket(ticket_id)
    target_id = body.target_queue_id
    _check_scope(source_id, clinic_scope)
    if target_id == source_id:
        raise AppError(ErrorCode.INVALID_TRANSITION, detail="ticket is already in that queue")
    day, clinic_id, service_point_id = _todays_queue(target_id)
    if clinic_id != parse_queue_id(source_id)[1]:
        raise AppError(ErrorCode.CROSS_CLINIC_TRANSFER)
    clinic, service_point = await _open_service_point(clinic_id, service_point_id)
    if not service_point.is_active:
        raise AppError(ErrorCode.SERVICE_POINT_INACTIVE)

    doc, source, target = await asyncio.to_thread(
        _transfer_sync, source_id, target_id, ticket_id, clinic, service_point, day
    )
    await _after_change(source_id, source)
    await _after_change(target_id, target)
    return _to_api(doc, target)


async def mark_no_show(ticket_id: str, clinic_scope: str | None = None) -> Ticket:
    queue_id = queue_id_of_ticket(ticket_id)
    _check_scope(queue_id, clinic_scope)

    def no_show(data: dict[str, Any]) -> TicketDoc:
        ticket = data["tickets"].get(ticket_id)
        if ticket is None:
            raise AppError(ErrorCode.NOT_FOUND, detail=f"ticket {ticket_id} not found")
        if ticket["status"] != TicketStatus.called.value:
            raise AppError(ErrorCode.INVALID_TRANSITION, detail=f"no-show from {ticket['status']}")
        now = utcnow()
        count = ticket.get("no_show_count", 0) + 1
        ticket.update(
            no_show_count=count, updated_at=now, called_at=None, called_by=None, counter_label=None,
            called_notified=False,
        )
        if count < MAX_NO_SHOWS:
            # Back in the queue with its original sequence, the late flag pushes it back in the order.
            ticket.update(status=TicketStatus.waiting.value, late_flag=True)
        else:
            ticket.update(status=TicketStatus.no_show.value, closed_at=now)
        return _ticket_doc(ticket_id, ticket)

    doc, data = await asyncio.to_thread(_mutate_sync, queue_id, no_show)
    await _after_change(queue_id, data)
    return _to_api(doc, data)


async def confirm_priority(
    ticket_id: str, staff_uid: str, body: ConfirmPriorityRequest, clinic_scope: str | None = None
) -> Ticket:
    queue_id = queue_id_of_ticket(ticket_id)
    _check_scope(queue_id, clinic_scope)

    def confirm(data: dict[str, Any]) -> TicketDoc:
        ticket = data["tickets"].get(ticket_id)
        if ticket is None:
            raise AppError(ErrorCode.NOT_FOUND, detail=f"ticket {ticket_id} not found")
        if ticket["status"] != TicketStatus.waiting.value:
            raise AppError(ErrorCode.INVALID_TRANSITION, detail=f"priority change from {ticket['status']}")
        priority = ticket.setdefault("priority", {})
        now = utcnow()
        if body.confirmed:
            category = body.category or PriorityCategory(priority.get("requested_category", "none"))
            if category is PriorityCategory.none:
                raise AppError(ErrorCode.VALIDATION_FAILED, "Choose a priority category to confirm.")
            priority.update(
                requested_category=category.value, staff_confirmed=True, confirmed_by=staff_uid, confirmed_at=now
            )
        else:
            # An unconfirmed request stays visible to staff but no longer moves the ticket.
            priority.update(staff_confirmed=False, confirmed_by=None, confirmed_at=None)
        ticket["updated_at"] = now
        return _ticket_doc(ticket_id, ticket)

    doc, data = await asyncio.to_thread(_mutate_sync, queue_id, confirm)
    await _after_change(queue_id, data)
    return _to_api(doc, data)


async def set_paused(
    queue_id: str, paused: bool, body: PauseQueueRequest | None = None, clinic_scope: str | None = None
) -> Queue:
    _check_scope(queue_id, clinic_scope)

    def change(data: dict[str, Any]) -> None:
        if data["status"] == QueueStatus.closed.value:
            raise AppError(ErrorCode.QUEUE_CLOSED)
        if paused:
            data.update(status=QueueStatus.paused.value, pause_reason=body.reason if body else None)
        else:
            data.update(status=QueueStatus.open.value, pause_reason=None)

    _, data = await asyncio.to_thread(_mutate_sync, queue_id, change)
    await _after_change(queue_id, data)
    return _queue_view(queue_id, data, for_patient=False)


def build_entries(data: dict[str, Any], now: dt.datetime | None = None) -> list[QueueEntry]:
    # Called tickets are shown first, then the waiting ones in effective order.
    tickets = data.get("tickets", {})
    called = sorted(
        (tid for tid, t in tickets.items() if t["status"] == TicketStatus.called.value),
        key=lambda tid: tickets[tid]["sequence_no"],
    )
    effective = effective_queue(data, now)
    rows = [(tid, False) for tid in called] + [(tid, tid in effective.protected) for tid in effective.order]
    return [
        queue_entry(_ticket_doc(tid, tickets[tid]), position, protected=protected)
        for position, (tid, protected) in enumerate(rows, 1)
    ]


def _queue_view(queue_id: str, data: dict[str, Any], for_patient: bool) -> Queue:
    return queue_to_api(
        QueueDoc.from_firestore(queue_id, data),
        build_entries(data),
        base_service_seconds=round(base_time(data)[0]),
        for_patient=for_patient,
    )


async def get_queue(queue_id: str, for_patient: bool, clinic_scope: str | None = None) -> Queue:
    _check_scope(queue_id, clinic_scope)
    day, clinic_id, service_point_id = parse_queue_id(queue_id)
    data = await asyncio.to_thread(_load_queue_sync, queue_id)
    if data is None:
        # Nobody has joined yet, so today's queue is shown empty without being stored.
        if day != service_date():
            raise AppError(ErrorCode.NOT_FOUND, detail=f"queue {queue_id} does not exist")
        clinic, service_point = await _open_service_point(clinic_id, service_point_id)
        data = _new_queue_data(queue_id, clinic, service_point, day)
    return _queue_view(queue_id, data, for_patient)


async def changes(queue_id: str) -> AsyncIterator[None]:
    # Yields once straight away, then whenever the queue changes and at least every few seconds,
    # because a ticket reaching the starvation threshold reorders the queue without any event.
    pubsub = None
    try:
        # Subscribing first means a change between the first snapshot and the subscription is not lost.
        try:
            pubsub = get_redis().pubsub()
            await pubsub.subscribe(_channel(queue_id))
        except RedisError:
            logger.warning("Redis unavailable, live queue is polling", exc_info=True)
            pubsub = None
        yield
        loop = asyncio.get_running_loop()
        while True:
            if pubsub is not None:
                try:
                    # get_message also returns early for the subscribe confirmation, so keep waiting
                    # until a real message arrives or the interval is over.
                    deadline = loop.time() + CHANGES_INTERVAL_SECONDS
                    while (remaining := deadline - loop.time()) > 0:
                        message = await pubsub.get_message(ignore_subscribe_messages=True, timeout=remaining)
                        if message is not None:
                            break
                except RedisError:
                    pubsub = None
                    continue
            else:
                await asyncio.sleep(5)
            yield
    finally:
        if pubsub is not None:
            try:
                await pubsub.aclose()
            except RedisError:
                pass


async def get_ticket(ticket_id: str, uid: str, clinic_id: str | None = None) -> Ticket:
    # Patients read their own tickets, staff read tickets at their own clinic.
    queue_id = queue_id_of_ticket(ticket_id)
    data = await asyncio.to_thread(_load_queue_sync, queue_id)
    existing = (data or {}).get("tickets", {}).get(ticket_id)
    if existing is None:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"ticket {ticket_id} not found")
    owned = existing.get("owner_uid") == uid
    same_clinic = clinic_id is not None and existing["clinic_id"] == clinic_id
    if not (owned or same_clinic):
        raise AppError(ErrorCode.NOT_FOUND, detail=f"ticket {ticket_id} not visible to {uid}")
    return _to_api(_ticket_doc(ticket_id, existing), data)


async def cancel_ticket(ticket_id: str, uid: str) -> Ticket:
    queue_id = queue_id_of_ticket(ticket_id)

    def cancel(data: dict[str, Any]) -> TicketDoc:
        existing = data["tickets"].get(ticket_id)
        # A ticket that is not the caller's looks exactly like one that does not exist.
        if existing is None or existing.get("owner_uid") != uid:
            raise AppError(ErrorCode.NOT_FOUND, detail=f"ticket {ticket_id} not found for {uid}")
        status = existing["status"]
        if status == TicketStatus.cancelled.value:
            raise AppError(ErrorCode.ALREADY_CANCELLED)
        if status == TicketStatus.served.value:
            raise AppError(ErrorCode.TICKET_CLOSED)
        if status != TicketStatus.waiting.value:
            raise AppError(ErrorCode.INVALID_TRANSITION, detail=f"cancel from {status}")
        now = utcnow()
        existing.update(status=TicketStatus.cancelled.value, closed_at=now, updated_at=now)
        return _ticket_doc(ticket_id, existing)

    doc, data = await asyncio.to_thread(_mutate_sync, queue_id, cancel)
    await _after_change(queue_id, data)
    return _to_api(doc, data)
