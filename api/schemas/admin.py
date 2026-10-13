# Admin web app: staff accounts, sync log and conflicts, audit and error logs, analytics.
from __future__ import annotations

import datetime as dt
from enum import StrEnum
from typing import Any, Literal

from pydantic import AwareDatetime, ConfigDict, EmailStr, Field, field_validator, model_validator

from .common import (
    PatchModel,
    RequestModel,
    ResponseModel,
    Role,
    SafeText,
    StaffRole,
    SyncLogStatus,
    SyncOp,
)
from .errors import ErrorCode


class StaffAccount(ResponseModel):
    id: str
    email: EmailStr
    display_name: str
    role: StaffRole
    clinic_id: str
    is_active: bool
    created_at: AwareDatetime
    updated_at: AwareDatetime | None = None
    last_sign_in_at: AwareDatetime | None = None


class CreateStaffRequest(RequestModel):
    # Creates the Firebase user, sets the `role` claim, emails a set-password link.

    email: EmailStr
    display_name: SafeText = Field(max_length=60)
    role: StaffRole
    clinic_id: str = Field(min_length=1)

    @field_validator("email")
    @classmethod
    def _lower(cls, v: str) -> str:
        return v.lower()


class AssignStaffRequest(RequestModel):
    # POST /v1/admin/clinics/{clinic_id}/staff, binds an existing staff account to the clinic.
    user_id: str = Field(min_length=1)


class UpdateStaffRequest(PatchModel):
    # A role change must revoke the user's refresh tokens (service layer).

    display_name: SafeText | None = Field(default=None, max_length=60)
    role: StaffRole | None = None
    clinic_id: str | None = Field(default=None, min_length=1)
    is_active: bool | None = None


class SyncResolution(StrEnum):
    last_write_wins = "last_write_wins"
    keep_server = "keep_server"
    apply_client = "apply_client"
    discard = "discard"


class SyncConflict(ResponseModel):
    client_value: dict[str, Any] | None = None
    server_value: dict[str, Any] | None = None
    client_updated_at: AwareDatetime | None = None
    server_updated_at: AwareDatetime | None = None


class SyncLogEntry(ResponseModel):
    id: str
    device_uid: str
    clinic_id: str | None = None
    idempotency_key: str | None = None
    entity_type: Literal["ticket"]
    entity_id: str | None = Field(
        description="Null when the item was rejected before a ticket existed."
    )
    op: SyncOp
    status: SyncLogStatus
    attempt: int | None = Field(default=None, ge=1)
    conflict: SyncConflict | None = None
    resolution: SyncResolution | None = None
    resolved_by: str | None = None
    resolved_at: AwareDatetime | None = None
    created_at: AwareDatetime


class ResolveConflictRequest(RequestModel):
    resolution: Literal["keep_server", "apply_client", "discard"]
    note: SafeText | None = Field(default=None, max_length=280)


class AuditOutcome(StrEnum):
    success = "success"
    denied = "denied"
    failed = "failed"


class AuditEntry(ResponseModel):
    id: str
    actor_id: str
    actor_role: Role
    action: str = Field(examples=["ticket.call_next"])
    target_type: str | None = None
    target_id: str | None = None
    outcome: AuditOutcome
    created_at: AwareDatetime


class ErrorLogEntry(ResponseModel):
    id: str
    request_id: str
    status: int
    code: ErrorCode
    method: str | None = None
    path: str
    actor_id: str | None = None
    detail: str | None = Field(default=None, description="Internal detail for IT admin only.")
    created_at: AwareDatetime


class CallsPerStaff(ResponseModel):
    staff_id: str | None = None
    calls: int | None = None


class AnalyticsSnapshot(ResponseModel):
    clinic_id: str
    date: dt.date
    tickets_joined: int | None = None
    tickets_served: int | None = None
    no_shows: int | None = None
    cancellations: int | None = None
    avg_wait_minutes: float | None = None
    p90_wait_minutes: float | None = None
    peak_hour: int | None = Field(default=None, ge=0, le=23)
    joins_by_channel: dict[str, int] | None = None
    calls_per_staff: list[CallsPerStaff] | None = None


class AnalyticsQuery(RequestModel):
    # Query parameters for GET /v1/admin/clinics/{id}/analytics (use with Depends()).

    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
        use_enum_values=False,
        populate_by_name=True,
    )

    from_: dt.date = Field(alias="from")
    to: dt.date
    format: Literal["json", "csv"] = "json"

    @model_validator(mode="after")
    def _range(self):
        if self.to < self.from_:
            raise ValueError("The end date must be on or after the start date.")
        return self
