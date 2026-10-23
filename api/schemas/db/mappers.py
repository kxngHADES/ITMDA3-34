# Document -> API model mapping. The only place storage fields cross into responses.
from __future__ import annotations

import datetime as dt
import math
from zoneinfo import ZoneInfo

from ..admin import StaffAccount, SyncLogEntry
from ..clinics import Clinic, ServicePoint
from ..common import TicketStatus, WaitRange
from ..identity import NotificationLogEntry
from ..patients import PatientPriority, PatientProfile
from ..queues import STAFF_ONLY_ENTRY_FIELDS, Queue, QueueEntry
from ..tickets import Ticket, TicketPriority
from .documents import (
    ClinicDoc,
    NotificationLogDoc,
    PatientDoc,
    QueueDoc,
    ServicePointDoc,
    StaffDoc,
    SyncLogDoc,
    TicketDoc,
)


def clinic_is_open(doc: ClinicDoc, now: dt.datetime | None = None) -> bool:
    now = (now or dt.datetime.now(dt.UTC)).astimezone(ZoneInfo(doc.timezone))
    day = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"][now.weekday()]
    hhmm = now.strftime("%H:%M")
    return doc.is_active and any(s.day == day and s.opens <= hhmm < s.closes for s in doc.opening_hours)


def clinic_to_api(doc: ClinicDoc) -> Clinic:
    return Clinic(
        id=doc.id, name=doc.name, address=doc.address, is_open=clinic_is_open(doc),
        is_active=doc.is_active, opening_hours=doc.opening_hours, updated_at=doc.updated_at,
    )


def notification_to_api(doc: NotificationLogDoc) -> NotificationLogEntry:
    return NotificationLogEntry(
        id=doc.id, ticket_id=doc.ticket_id, channel=doc.channel, kind=doc.kind, status=doc.status,
        fallback_of=doc.fallback_of, error=doc.error, created_at=doc.created_at,
    )


def service_point_to_api(doc: ServicePointDoc) -> ServicePoint:
    return ServicePoint(
        id=doc.id, clinic_id=doc.clinic_id, name=doc.name, icon=doc.icon, is_active=doc.is_active,
        queue_id=doc.current_queue_id, concurrent_servers=doc.concurrent_servers, updated_at=doc.updated_at,
    )


def patient_to_api(doc: PatientDoc, now: dt.datetime | None = None) -> PatientProfile:
    p = doc.priority
    return PatientProfile(
        id=doc.id, email=doc.email, display_name=doc.display_name, language=doc.language,
        notification_channel=doc.notification_channel, phone_last4=doc.phone_last4,
        priority=PatientPriority(
            category=p.category, verified=p.verified, verified_at=p.verified_at, valid_until=p.valid_until,
            active=p.is_active(now),
        ),
        created_at=doc.created_at, updated_at=doc.updated_at,
    )


def wait_range(position: int, concurrent_servers: int, base_seconds: float, cold_start: bool) -> WaitRange:
    # estimated_wait = ceil(position / concurrent_servers) × B(sp), shown as ±20%.
    est_minutes = math.ceil(position / max(concurrent_servers, 1)) * base_seconds / 60
    return WaitRange(
        min_minutes=math.floor(est_minutes * 0.8), max_minutes=math.ceil(est_minutes * 1.2), cold_start=cold_start,
    )


def ticket_to_api(doc: TicketDoc, *, position: int | None = None, wait: WaitRange | None = None) -> Ticket:
    # `position`/`wait` come from the Queue Engine's effective order (only for waiting tickets).
    waiting = doc.status is TicketStatus.waiting
    return Ticket(
        id=doc.id, display_number=doc.display_number, sequence_no=doc.sequence_no, queue_id=doc.queue_id,
        service_point_id=doc.service_point_id, clinic_id=doc.clinic_id, status=doc.status, channel=doc.channel,
        position=position if waiting else None, people_ahead=(position - 1) if (waiting and position) else None,
        estimated_wait=wait if waiting else None, counter_label=doc.counter_label,
        priority=TicketPriority.model_validate(doc.priority, from_attributes=True), late_flag=doc.late_flag,
        no_show_count=doc.no_show_count, display_name=doc.display_name, phone_last4=doc.phone_last4, notification_channel=doc.notification_channel,
        language=doc.language, consultation_seconds=doc.consultation_seconds, created_at=doc.created_at,
        updated_at=doc.updated_at, called_at=doc.called_at, closed_at=doc.closed_at,
    )


def queue_entry(doc: TicketDoc, position: int, protected: bool) -> QueueEntry:
    return QueueEntry(
        ticket_id=doc.id, display_number=doc.display_number, position=position, status=doc.status,
        is_priority=doc.priority.staff_confirmed and doc.priority.requested_category != "none",
        late_flag=doc.late_flag, joined_at=doc.joined_at, protected=protected, display_name=doc.display_name,
        priority_category=doc.priority.requested_category, staff_confirmed=doc.priority.staff_confirmed,
    )


def queue_to_api(
    doc: QueueDoc, entries: list[QueueEntry], *, base_service_seconds: int | None, for_patient: bool
) -> Queue:
    if for_patient:  # patients never see names or categories
        entries = [e.model_copy(update={f: None for f in STAFF_ONLY_ENTRY_FIELDS}) for e in entries]
    return Queue(
        id=doc.id, service_point_id=doc.service_point_id, clinic_id=doc.clinic_id, service_date=doc.service_date,
        display_prefix=doc.display_prefix, status=doc.status, concurrent_servers=doc.concurrent_servers,
        counter_labels=doc.counter_labels, note=doc.note, opened_by=None if for_patient else doc.opened_by,
        opened_at=doc.opened_at, closed_at=doc.closed_at, pause_reason=doc.pause_reason, now_serving=doc.now_serving,
        waiting_count=sum(1 for e in entries if e.status is TicketStatus.waiting),
        base_service_seconds=base_service_seconds, entries=entries, updated_at=doc.updated_at,
    )


def sync_log_to_api(doc: SyncLogDoc) -> SyncLogEntry:
    return SyncLogEntry(
        id=doc.id, device_uid=doc.device_uid, clinic_id=doc.clinic_id, idempotency_key=doc.idempotency_key,
        entity_type="ticket", entity_id=doc.entity_id, op=doc.op, status=doc.status, attempt=doc.attempt,
        conflict=doc.conflict, resolution=doc.resolution, resolved_by=doc.resolved_by,
        resolved_at=doc.resolved_at, created_at=doc.created_at,
    )


def staff_to_api(doc: StaffDoc) -> StaffAccount:
    return StaffAccount(
        id=doc.id, email=doc.email, display_name=doc.display_name, role=doc.role, clinic_id=doc.clinic_id,
        is_active=doc.is_active, created_at=doc.created_at, updated_at=doc.updated_at,
        last_sign_in_at=doc.last_sign_in_at,
    )
