# Firestore document models — what is stored, as opposed to what the API returns.
from __future__ import annotations

import datetime as dt
from typing import Any

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field

from ..admin import AuditOutcome, SyncConflict, SyncResolution
from ..clinics import OpeningHoursSlot
from ..common import (
    JoinChannel,
    NotificationChannel,
    PriorityCategory,
    QueueStatus,
    Role,
    StaffRole,
    SyncLogStatus,
    SyncOp,
    TicketStatus,
)
from ..errors import ErrorCode
from ..identity import NotificationKind, NotificationStatus
from ..queues import QueueConfig
from ..tickets import DismissReason


def utcnow() -> dt.datetime:
    return dt.datetime.now(dt.UTC)


class Document(BaseModel):
    # Base for every stored document.

    model_config = ConfigDict(extra="ignore", validate_assignment=True)

    id: str = Field(exclude=True)
    created_at: AwareDatetime = Field(default_factory=utcnow)
    updated_at: AwareDatetime = Field(default_factory=utcnow)

    def to_firestore(self) -> dict[str, Any]:
        return self.model_dump(mode="python", exclude={"id"}, exclude_none=False) | {
            "updated_at": utcnow()
        }

    @classmethod
    def from_firestore(cls, doc_id: str, data: dict[str, Any]):
        return cls.model_validate({**data, "id": doc_id})


class ClinicDoc(Document):
    name: str
    address: str
    opening_hours: list[OpeningHoursSlot]
    timezone: str = "Africa/Johannesburg"
    is_active: bool = True


class QueueConfigDoc(QueueConfig):
    # Embedded in ServicePointDoc.config with the same fields and rules as the API model.
    pass


class ServicePointDoc(Document):
    clinic_id: str
    name: str
    icon: str
    display_prefix: str = "A"
    is_active: bool = True
    concurrent_servers: int = 1
    current_queue_id: str | None = None
    config: QueueConfigDoc
    base_service_seconds: float | None = None
    base_service_samples: int = 0


class QueueDoc(Document):
    service_point_id: str
    clinic_id: str
    service_date: dt.date
    display_prefix: str = "A"
    status: QueueStatus = QueueStatus.open
    concurrent_servers: int = 1
    counter_labels: list[str] = Field(default_factory=list)
    note: str | None = None
    pause_reason: str | None = None
    opened_by: str
    opened_at: AwareDatetime = Field(default_factory=utcnow)
    closed_at: AwareDatetime | None = None
    now_serving: str | None = None
    ticket_count: int = 0
    etag: str | None = None


class TicketPriorityDoc(BaseModel):
    requested_category: PriorityCategory = PriorityCategory.none
    staff_confirmed: bool = False
    confirmed_by: str | None = None
    confirmed_at: AwareDatetime | None = None


class TicketDoc(Document):
    owner_uid: str | None
    device_id: str | None = None
    created_by_staff_uid: str | None = None
    clinic_id: str
    service_point_id: str
    queue_id: str
    sequence_no: int
    display_number: str
    no_show_count: int = 0
    status: TicketStatus = TicketStatus.waiting
    channel: JoinChannel
    priority: TicketPriorityDoc = Field(default_factory=TicketPriorityDoc)
    late_flag: bool = False
    joined_at: AwareDatetime = Field(default_factory=utcnow)
    called_at: AwareDatetime | None = None
    closed_at: AwareDatetime | None = None
    counter_label: str | None = None
    called_by: str | None = None
    consultation_seconds: int | None = None
    dismiss_reason: DismissReason | None = None
    display_name: str | None = None
    phone_ciphertext: bytes | None = None
    phone_last4: str | None = None
    email_ciphertext: bytes | None = None
    # Set once each alert has gone out, so a patient is not told twice.
    almost_notified: bool = False
    called_notified: bool = False
    notification_channel: NotificationChannel = NotificationChannel.push
    language: str = "en"
    client_updated_at: AwareDatetime | None = None
    created_idempotency_key: str | None = None
    hidden_by_patient: bool = False


class StaffDoc(Document):
    # Doc id is the Firebase UID. The role here is mirrored into the custom claim.

    email: str
    display_name: str
    role: StaffRole
    clinic_id: str
    is_active: bool = True
    last_sign_in_at: AwareDatetime | None = None


class PatientPriorityDoc(BaseModel):
    category: PriorityCategory = PriorityCategory.none
    verified: bool = False
    verified_by: str | None = None
    verified_at: AwareDatetime | None = None
    # None on a verified priority means it never runs out.
    valid_until: AwareDatetime | None = None

    def is_active(self, now: dt.datetime | None = None) -> bool:
        if not self.verified or self.category is PriorityCategory.none:
            return False
        return self.valid_until is None or (now or utcnow()) < self.valid_until


class PatientDoc(Document):
    # Doc id is the Firebase UID. Kept apart from the staff `users` collection.

    email: str
    display_name: str
    language: str = "en"
    notification_channel: NotificationChannel = NotificationChannel.push
    phone_ciphertext: bytes | None = None
    phone_last4: str | None = None
    priority: PatientPriorityDoc = Field(default_factory=PatientPriorityDoc)
    is_active: bool = True
    last_sign_in_at: AwareDatetime | None = None


class PushTokenDoc(Document):
    uid: str
    role: Role
    clinic_id: str | None = None
    device_id: str
    token: str
    platform: str


class NotificationLogDoc(Document):
    ticket_id: str
    clinic_id: str
    channel: NotificationChannel
    kind: NotificationKind
    status: NotificationStatus = NotificationStatus.queued
    provider_message_id: str | None = None
    fallback_of: str | None = None
    error: str | None = None


class SyncLogDoc(Document):
    device_uid: str
    clinic_id: str | None = None
    idempotency_key: str
    entity_type: str = "ticket"
    entity_id: str | None = None
    op: SyncOp
    status: SyncLogStatus
    attempt: int | None = None
    conflict: SyncConflict | None = None
    resolution: SyncResolution | None = None
    resolved_by: str | None = None
    resolved_at: AwareDatetime | None = None
    resolution_note: str | None = None


class AuditDoc(Document):
    actor_id: str
    actor_role: Role
    clinic_id: str | None = None
    action: str
    target_type: str | None = None
    target_id: str | None = None
    outcome: AuditOutcome
    request_id: str | None = None


class ErrorLogDoc(Document):
    request_id: str
    status: int
    code: ErrorCode
    method: str
    path: str
    actor_id: str | None = None
    detail: str | None = None


class AnalyticsSnapshotDoc(Document):
    clinic_id: str
    date: dt.date
    tickets_joined: int = 0
    tickets_served: int = 0
    no_shows: int = 0
    cancellations: int = 0
    avg_wait_minutes: float | None = None
    p90_wait_minutes: float | None = None
    peak_hour: int | None = None
    joins_by_channel: dict[str, int] = Field(default_factory=dict)
    calls_per_staff: dict[str, int] = Field(default_factory=dict)


class IdempotencyRecord(BaseModel):
    # Stored in Redis as JSON with SETNX and a 24h TTL.

    uid: str
    request_hash: str
    status_code: int
    response_body: dict[str, Any]
    created_at: AwareDatetime = Field(default_factory=utcnow)
