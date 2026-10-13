"""
Shared base classes, enums and constrained types for every SwiftQ API schema

Contract: openapi-v1.yaml
"""

from __future__ import annotations

import re
from enum import StrEnum
from typing import Annotated, Generic, TypeVar
from pydantic import AfterValidator, BeforeValidator, BaseModel, ConfigDict, Field, StringConstraints, model_validator

# Base classes

class RequestModel(BaseModel):
    """
    Base for every request body.

    extra = "forbid" --> unknown fields are a 422 (contract: additionalProperties: false)
    str_strip_whitespace -> " Thabo " is stored as "Thabo"
    """

    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
        use_enum_values=False
    )

class PatchModel(RequestModel):
    """
    Base for PATCH bodies: at least one field must be sent (contract: minProperties: 1)

    in the service layer we use body.model_dump(exclude_unset=True) so only the fields the client actually sent are written.
    """

    @model_validator(mode="after")
    def _at_least_one_field(self):
        if not self.model_fields_set:
            raise ValueError("Send at least one field to change")
        return self


class ResponseModel(BaseModel):
    model_config = ConfigDict(
        extra="ignore",
        from_attributes=True
    )


# Enums

class Role(StrEnum):
    patient = "patient"
    receptionist = "receptionist"
    nurse = "nurse"
    manager = "manager"
    it_admin = "it_admin"

class StaffRole(StrEnum):
    receptionist = "receptionist"
    nurse = "nurse"
    manager = "manager"
    it_admin = "it_admin"

class TicketStatus(StrEnum):
    waiting = "waiting"
    called = "called"
    served = "served"
    no_show = "no_show"
    cancelled = "cancelled"

ACTIVE_STATUSES = frozenset({TicketStatus.waiting, TicketStatus.called})
TERMINAL_STATUSES = frozenset({TicketStatus.served, TicketStatus.cancelled})

# no_show is not a terminal: return late moves it to the back to waiting

class PriorityCategory(StrEnum):
    # five allowed
    # none = standard FIFO
    none = "none"
    elderly = "elderly"
    pregnant = "pregnant"
    disability = "disability"
    urgent = "urgent"


class JoinChannel(StrEnum):
    app = "app"
    sms = "sms"
    staff_assisted = "staff_assisted"

class NotificationChannel(StrEnum):
    push = "push"
    sms = "sms"
    whatsapp = "whatsapp"
    email = "email"
    none = "none"


class QueueStatus(StrEnum):
    open = "open"
    paused = "paused"
    closed = "closed"


class SyncLogStatus(StrEnum):
    applied = "applied"
    duplicate = "duplicate"
    conflict_resolved = "conflict_resolved"
    conflict_pending = "conflict_pending"
    resolved = "resolved"
    rejected = "rejected"

class SyncOp(StrEnum):
    join = "join"
    update_ticket = "update_ticket"
    cancel = "cancel"


# Constrained types

_CONTROL_CHARS = re.compile(r"[\x00-\x08\x0b\x0e-\x1f\x7f]") # This regular expression matches non-printable ASCII control characters to sanitize raw text.

"""
EXAMPLE:
dirty_text = "Hello\x00World\x07!"

clean_text = _CONTROL_CHARS.sub("", dirty_text)

print(clean_text)  # Output: HelloWorld!
"""

def _safe_text(v: str):
    # Rejects "< >"

    if "<" in v or ">" in v:
        raise ValueError('Please do not use < or > here')
    return _CONTROL_CHARS.sub("",v)

SafeText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1), AfterValidator(_safe_text)]
# Set the length per field: `name: SafeText = Field(max_length=80)`

LanguageCode = Annotated[str, StringConstraints(pattern=r"^[a-z]{2,3}$")]
# ISO 639 codes so en for english , af for afikaans, zu for zulu and etc

from services.phone_service import format_sa_number  # noqa: E402

# Numbers typed as 082..., 2782... or with spaces are turned into +27 form before the check.
SAPhone = Annotated[str, StringConstraints(pattern=r"^\+27[0-9]{9}$"), BeforeValidator(format_sa_number)]
# SA Mobile in E.164 - ICASA form. Encrypted before storage

DisplayPrefix = Annotated[str, StringConstraints(pattern=r"^[A-Z]{1,2}$")]

HHMM = Annotated[str, StringConstraints(pattern=r"^[0-2][0-9]:[0-5][0-9]$")]
CounterLabel = Annotated[SafeText, StringConstraints(max_length=20)]


# Paging and shared shapes
T = TypeVar("T")

class PageMeta(ResponseModel):
    #Cursor paging fields (contract schema `Page`).
    next_cursor: str | None = None
    has_more: bool

class Page(PageMeta, Generic[T]):
    #`Page[Ticket]` etc. — the contract's `allOf: [Page, {items: [...]}]`.
    items: list[T]

class ItemList(ResponseModel, Generic[T]):
    #Un-paged list wrapper: `{ "items": [...] }`.
    items: list[T]

class WaitRange(ResponseModel):
    #Displayed as "16–24 minutes". ±20% of ceil(position / concurrent_servers) × B(sp).
    min_minutes: int
    max_minutes: int
    cold_start: bool = Field(description="True while < 5 samples exist and the default base time is used.")