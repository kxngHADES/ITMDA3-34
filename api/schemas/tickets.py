# Tickets: the patient-facing resource and every staff action body.
from __future__ import annotations
from enum import StrEnum
from pydantic import AwareDatetime, EmailStr, Field, model_validator
from .common import (
    JoinChannel,
    LanguageCode,
    NotificationChannel,
    PatchModel,
    PriorityCategory,
    RequestModel,
    ResponseModel,
    SafeText,
    SAPhone,
    TicketStatus,
    WaitRange,
)

class TicketPriority(ResponseModel):
    requested_category: PriorityCategory
    staff_confirmed: bool
    confirmed_by: str | None = None
    confirmed_at: AwareDatetime | None = None

class Ticket(ResponseModel):
    id: str
    display_number: str = Field(examples=["A014"])
    sequence_no: int = Field(ge=1)
    queue_id: str
    service_point_id: str
    clinic_id: str
    status: TicketStatus
    channel: JoinChannel
    position: int | None = Field(default=None, description="Effective position while waiting; null otherwise.")
    people_ahead: int | None = None
    estimated_wait: WaitRange | None = None
    counter_label: str | None = None
    priority: TicketPriority
    late_flag: bool
    no_show_count: int = Field(default=0, ge=0, description="Times the ticket was called and nobody came.")
    display_name: str | None = None
    phone_last4: str | None = Field(default=None, description="Full number is encrypted at rest and never returned.")
    notification_channel: NotificationChannel | None = None
    language: LanguageCode | None = None
    consultation_seconds: int | None = None
    created_at: AwareDatetime
    updated_at: AwareDatetime
    called_at: AwareDatetime | None = None
    closed_at: AwareDatetime | None = None

def _channel_needs_phone(phone: str | None, channel: NotificationChannel | None):
    if channel in (NotificationChannel.sms, NotificationChannel.whatsapp) and not phone:
        raise ValueError("Add a phone number to get SMS or WhatsApp alerts.")


def _channel_needs_email(email: str | None, channel: NotificationChannel | None):
    if channel is NotificationChannel.email and not email:
        raise ValueError("Add an email address to get email alerts.")

class JoinQueueRequest(RequestModel):
    # POST /v1/queues/{queue_id}/tickets (and sync op `join`).
    display_name: SafeText | None = Field(default=None, max_length=40, description="Optional first name or nickname. No surname.")
    phone: SAPhone | None = Field(default=None, description="Only for SMS/WhatsApp alerts. Encrypted before storage.")
    email: EmailStr | None = Field(default=None, description="Only for email alerts. Encrypted before storage.")
    language: LanguageCode
    notification_channel: NotificationChannel | None = None
    priority_request: PriorityCategory | None = Field(default=None, description="Stored, but does not move the ticket until staff confirm.")
    client_created_at: AwareDatetime | None = Field(default=None, description="When Join was tapped on the device (may be earlier if offline).")

    @model_validator(mode="after")
    def _phone_for_sms(self):
        _channel_needs_phone(self.phone, self.notification_channel)
        _channel_needs_email(self.email, self.notification_channel)
        return self

class UpdateTicketRequest(PatchModel):
    """PATCH /v1/tickets/{ticket_id} (and sync op `update_ticket`). Changing priority_request
    resets staff_confirmed to false in the service layer."""
    display_name: SafeText | None = Field(default=None, max_length=40)
    phone: SAPhone | None = None # explicit null clears the stored number
    notification_channel: NotificationChannel | None = None
    language: LanguageCode | None = None
    priority_request: PriorityCategory | None = None

    @model_validator(mode="after")
    def _phone_for_sms(self):
        if "notification_channel" in self.model_fields_set and "phone" in self.model_fields_set:
            _channel_needs_phone(self.phone, self.notification_channel)
        return self

class WalkInRequest(RequestModel):
    # POST /v1/queues/{queue_id}/walk-ins. A staff-entered category is confirmed immediately.
    display_name: SafeText | None = Field(default=None, max_length=40)
    phone: SAPhone | None = None
    email: EmailStr | None = None
    language: LanguageCode
    notification_channel: NotificationChannel | None = None
    priority_category: PriorityCategory | None = None

    @model_validator(mode="after")
    def _phone_for_sms(self):
        _channel_needs_phone(self.phone, self.notification_channel)
        _channel_needs_email(self.email, self.notification_channel)
        return self

class DismissReason(StrEnum):
    duplicate = "duplicate"
    wrong_service_point = "wrong_service_point"
    patient_left = "patient_left"
    expired = "expired" # staff mobile "expire ticket" action
    other = "other"

class DismissRequest(RequestModel):
    reason: DismissReason
    note: SafeText | None = Field(default=None, max_length=140)

    @model_validator(mode="after")
    def _note_for_other(self):
        if self.reason is DismissReason.other and not self.note:
            raise ValueError("Add a short note when the reason is 'other'.")
        return self

class ConfirmPriorityRequest(RequestModel):
    confirmed: bool
    category: PriorityCategory | None = Field(default=None, description="Optional correction of the requested category.")

    @model_validator(mode="after")
    def _category_not_none_when_confirming(self):
        if self.confirmed and self.category is PriorityCategory.none:
            raise ValueError("Choose a priority category to confirm, or set confirmed to false.")
        return self

class TransferRequest(RequestModel):
    target_queue_id: str = Field(min_length=1)
