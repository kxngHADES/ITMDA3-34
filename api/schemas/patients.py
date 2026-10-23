# Registered patient accounts for the patient app: sign up with email, contact preferences and a lasting priority.
from __future__ import annotations

from pydantic import AwareDatetime, BaseModel, ConfigDict, EmailStr, Field, model_validator

from .common import (
    LanguageCode,
    NotificationChannel,
    PatchModel,
    PriorityCategory,
    RequestModel,
    ResponseModel,
    SafeText,
    SAPhone,
)

# Urgent is decided by staff on the day, so it is never kept on an account.
ACCOUNT_CATEGORIES = frozenset({PriorityCategory.elderly, PriorityCategory.pregnant, PriorityCategory.disability})


def _check_priority(category: PriorityCategory | None) -> None:
    if category is not None and category is not PriorityCategory.none and category not in ACCOUNT_CATEGORIES:
        raise ValueError("Urgent care is decided at the clinic on the day, so it can't be saved to your account.")


def _check_channel(channel: NotificationChannel | None, phone: str | None) -> None:
    if channel in (NotificationChannel.sms, NotificationChannel.whatsapp) and not phone:
        raise ValueError("Add a phone number to get SMS or WhatsApp alerts.")


class PatientRegisterRequest(RequestModel):
    # Passwords are taken exactly as typed, the same as on sign in.
    model_config = ConfigDict(str_strip_whitespace=False)

    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: SafeText = Field(max_length=40, description="First name or nickname shown on the ticket.")
    language: LanguageCode = "en"
    notification_channel: NotificationChannel = NotificationChannel.push
    phone: SAPhone | None = Field(default=None, description="Only for SMS/WhatsApp alerts. Encrypted before storage.")
    priority_request: PriorityCategory | None = Field(
        default=None, description="Saved unverified until staff confirm it on one of the patient's tickets."
    )

    @model_validator(mode="after")
    def _check(self):
        _check_priority(self.priority_request)
        _check_channel(self.notification_channel, self.phone)
        return self


class PatientLoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1)


class UpdatePatientRequest(PatchModel):
    # PATCH /v1/patients/me. A different priority_request clears the verification, an explicit null phone clears the number.
    display_name: SafeText | None = Field(default=None, max_length=40)
    phone: SAPhone | None = None
    language: LanguageCode | None = None
    notification_channel: NotificationChannel | None = None
    priority_request: PriorityCategory | None = None

    @model_validator(mode="after")
    def _check(self):
        for name in ("display_name", "language", "notification_channel"):
            if name in self.model_fields_set and getattr(self, name) is None:
                raise ValueError(f"{name.replace('_', ' ').capitalize()} can't be empty.")
        _check_priority(self.priority_request)
        return self


class PatientPriority(ResponseModel):
    category: PriorityCategory
    verified: bool = Field(description="True once staff confirmed the category on one of the patient's tickets.")
    verified_at: AwareDatetime | None = None
    valid_until: AwareDatetime | None = Field(
        default=None, description="When a verified priority runs out. Null on a verified priority means permanent."
    )
    active: bool = Field(description="Verified and not yet expired, so new tickets join the priority lane.")


class PatientProfile(ResponseModel):
    id: str
    email: EmailStr
    display_name: str
    language: LanguageCode
    notification_channel: NotificationChannel
    phone_last4: str | None = Field(default=None, description="Full number is encrypted at rest and never returned.")
    priority: PatientPriority
    created_at: AwareDatetime
    updated_at: AwareDatetime


class PatientAuthResponse(ResponseModel):
    access_token: str
    token_type: str = "Bearer"
    expires_in: int
    patient: PatientProfile
