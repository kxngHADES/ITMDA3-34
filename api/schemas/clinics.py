# Clinics and service points (Queues tag reads, Admin tag writes).
from __future__ import annotations
from enum import StrEnum
from pydantic import AwareDatetime, Field, field_validator
from .common import HHMM, PatchModel, RequestModel, ResponseModel, SafeText

class Weekday(StrEnum):
    mon = "mon"
    tue = "tue"
    wed = "wed"
    thu = "thu"
    fri = "fri"
    sat = "sat"
    sun = "sun"

class OpeningHoursSlot(RequestModel):
    day: Weekday
    opens: HHMM = Field(examples=["07:00"])
    closes: HHMM = Field(examples=["16:00"])
    @field_validator("closes")
    @classmethod
    def _closes_after_opens(cls, v: str, info):
        opens = info.data.get("opens")
        if opens and v <= opens: # "HH:MM" strings compare correctly
            raise ValueError("Closing time must be after opening time.")
        return v
def _one_slot_per_day(slots: list[OpeningHoursSlot] | None):
    if slots is None:
        return slots
    days = [s.day for s in slots]
    if len(days) != len(set(days)):
        raise ValueError("Each day can only appear once.")
    return slots

class Clinic(ResponseModel):
    id: str
    name: str = Field(examples=["Soshanguve Block B Clinic"])
    address: str | None = None
    is_open: bool = Field(description="Computed from opening_hours in Africa/Johannesburg time.")
    is_active: bool | None = None
    opening_hours: list[OpeningHoursSlot] | None = None
    updated_at: AwareDatetime | None = None

class ServicePoint(ResponseModel):
    id: str
    clinic_id: str
    name: str = Field(examples=["General consultation"])
    icon: str | None = Field(default=None, description="Icon key from the shared icon set.")
    is_active: bool
    queue_id: str | None = Field(description="Today's open or paused queue; null until staff open one.")
    concurrent_servers: int = Field(default=1, ge=1)
    updated_at: AwareDatetime | None = None

class CreateClinicRequest(RequestModel):
    name: SafeText = Field(max_length=80)
    address: SafeText = Field(max_length=200)
    opening_hours: list[OpeningHoursSlot] = Field(min_length=1)

    @field_validator("opening_hours")
    @classmethod
    def _unique_days(cls, v):
        return _one_slot_per_day(v)

class UpdateClinicRequest(PatchModel):
    name: SafeText | None = Field(default=None, max_length=80)
    address: SafeText | None = Field(default=None, max_length=200)
    opening_hours: list[OpeningHoursSlot] | None = None
    is_active: bool | None = None

    @field_validator("opening_hours")
    @classmethod
    def _unique_days(cls, v):
        return _one_slot_per_day(v)

class CreateServicePointRequest(RequestModel):
    name: SafeText = Field(max_length=60)
    icon: str = Field(max_length=40, examples=["stethoscope"])
    concurrent_servers: int = Field(default=1, ge=1, le=20)

class UpdateServicePointRequest(PatchModel):
    name: SafeText | None = Field(default=None, max_length=60)
    icon: str | None = Field(default=None, max_length=40)
    concurrent_servers: int | None = Field(default=None, ge=1, le=20)
    is_active: bool | None = None