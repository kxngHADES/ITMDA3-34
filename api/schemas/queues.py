# The day's queue, its live snapshot, configuration (Table 8) and queue-level actions.
from __future__ import annotations

import datetime as dt

from pydantic import AwareDatetime, Field

from .common import (
    CounterLabel,
    DisplayPrefix,
    PatchModel,
    PriorityCategory,
    QueueStatus,
    RequestModel,
    ResponseModel,
    SafeText,
    TicketStatus,
)


class PriorityWeights(RequestModel):
    # Positions a *confirmed* priority ticket may jump ahead of standard tickets.

    elderly: int = Field(ge=0)
    pregnant: int = Field(ge=0)
    disability: int = Field(ge=0)
    urgent: int = Field(ge=0)

    def for_category(self, category: PriorityCategory) -> int:
        return 0 if category is PriorityCategory.none else getattr(self, category.value)


class QueueConfig(RequestModel):
    # Used as both the GET response and the PUT body (full replace).

    priority_weights: PriorityWeights
    priority_max_jump_k: int = Field(
        ge=0, description="Hard cap on positions any ticket may jump in one pass."
    )
    starvation_threshold_w_minutes: int = Field(
        ge=1,
        description="Waited this long -> protected; no priority ticket may pass it.",
    )
    late_requeue_penalty_p: int = Field(
        ge=0, description="Positions a late return is pushed back."
    )
    default_base_time_seconds: int = Field(
        ge=60,
        description="Cold-start B(sp) until min_samples_for_ema samples exist.",
    )
    ema_alpha: float = Field(default=0.2, ge=0.01, le=1)
    min_samples_for_ema: int = Field(default=5, ge=1)


DEFAULT_QUEUE_CONFIG = QueueConfig(
    priority_weights=PriorityWeights(elderly=3, pregnant=3, disability=3, urgent=6),
    priority_max_jump_k=10,
    starvation_threshold_w_minutes=100,
    late_requeue_penalty_p=5,
    default_base_time_seconds=900,
)

# live snapshot
class QueueEntry(ResponseModel):
    """One row of the effective queue. Staff-only fields are None for patients and are dropped
    from the patient response with `response_model_exclude_none=True` (Table 11)."""

    ticket_id: str
    display_number: str = Field(examples=["A014"])
    position: int = Field(ge=1, description="1-based effective position after recompute.")
    status: TicketStatus
    is_priority: bool = Field(
        description="Neutral icon flag. True only when a category is confirmed."
    )
    late_flag: bool
    joined_at: AwareDatetime | None = None
    # staff only
    protected: bool | None = None
    display_name: str | None = None
    priority_category: PriorityCategory | None = None
    staff_confirmed: bool | None = None


STAFF_ONLY_ENTRY_FIELDS = frozenset(
    {"protected", "display_name", "priority_category", "staff_confirmed"}
)


class Queue(ResponseModel):
    # Returned by GET/PATCH/pause/resume/close and published to Firestore `live_queues/{id}`.

    id: str
    service_point_id: str
    clinic_id: str
    service_date: dt.date
    display_prefix: str = Field(examples=["A"])
    status: QueueStatus
    concurrent_servers: int | None = Field(default=None, ge=1)
    counter_labels: list[str] | None = None
    note: str | None = Field(default=None, max_length=140)
    opened_by: str | None = None
    opened_at: AwareDatetime | None = None
    closed_at: AwareDatetime | None = None
    pause_reason: str | None = None
    now_serving: str | None = Field(
        default=None, description="Display number of the last called ticket."
    )
    waiting_count: int = Field(ge=0)
    base_service_seconds: int | None = Field(
        default=None,
        description="Current B(sp), or the default during cold start.",
    )
    entries: list[QueueEntry]
    updated_at: AwareDatetime


# queue requests
class OpenQueueRequest(RequestModel):
    display_prefix: DisplayPrefix = "A"
    concurrent_servers: int | None = Field(
        default=None,
        ge=1,
        le=20,
        description="Defaults to the service point's value.",
    )
    counter_labels: list[CounterLabel] | None = Field(default=None, max_length=20)
    note: SafeText | None = Field(default=None, max_length=140)


class UpdateQueueRequest(PatchModel):
    concurrent_servers: int | None = Field(default=None, ge=1, le=20)
    counter_labels: list[CounterLabel] | None = Field(default=None, max_length=20)
    note: SafeText | None = Field(default=None, max_length=140)


class PauseQueueRequest(RequestModel):
    reason: SafeText | None = Field(default=None, max_length=140)


class CloseQueueRequest(RequestModel):
    cancel_remaining: bool = False
    message: SafeText | None = Field(
        default=None, max_length=140, description="Sent to patients whose tickets are cancelled."
    )


class CallNextRequest(RequestModel):
    counter_label: CounterLabel | None = Field(
        default=None, description="Shown to the patient, e.g. 'Room 3'."
    )
