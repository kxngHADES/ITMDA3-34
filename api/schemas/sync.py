# Offline outbox push and change pull.
from __future__ import annotations
from enum import StrEnum
from typing import Any
from pydantic import UUID4, AwareDatetime, Field, model_validator
from .clinics import Clinic, ServicePoint
from .common import RequestModel, ResponseModel, SyncOp
from .errors import ErrorBody
from .queues import QueueConfig
from .tickets import JoinQueueRequest, Ticket, UpdateTicketRequest

class EmptyPayload(RequestModel):
    # Payload for `cancel` — must be `{}`.
    pass

class OutboxItem(RequestModel):
    """One row of the device's OutboxEntry table, replayed in order.

    `payload` is parsed into the right model for `op` before validation, so a malformed
    payload fails this item only (the sync handler turns it into outcome=rejected).
    """
    idempotency_key: UUID4
    op: SyncOp
    queue_id: str | None = Field(default=None, description="Required for join.")
    ticket_id: str | None = Field(default=None, description="Required for update_ticket and cancel (or local_ref).")
    local_ref: str | None = Field(default=None, description="Device-local ticket id for tickets created offline.")
    client_updated_at: AwareDatetime
    attempt: int | None = Field(default=None, ge=1, description="Retry count from the outbox.")
    payload: JoinQueueRequest | UpdateTicketRequest | EmptyPayload

    @model_validator(mode="before")
    @classmethod
    def _parse_payload_by_op(cls, data: Any):
        if isinstance(data, dict) and isinstance(data.get("payload"), dict):
            model = {
                SyncOp.join.value: JoinQueueRequest,
                SyncOp.update_ticket.value: UpdateTicketRequest,
                SyncOp.cancel.value: EmptyPayload,
            }.get(str(data.get("op")))
            if model is not None:
                data = {**data, "payload": model.model_validate(data["payload"])}
        return data

    @model_validator(mode="after")
    def _targets(self):
        if self.op is SyncOp.join and not self.queue_id:
            raise ValueError("queue_id is required for join.")
        if self.op in (SyncOp.update_ticket, SyncOp.cancel) and not (self.ticket_id or self.local_ref):
            raise ValueError("ticket_id or local_ref is required.")
        return self

class SyncPushRequest(RequestModel):
    device_id: str = Field(min_length=8, max_length=64, description="Stable install id; per-device rate-limit bucket.")
    items: list[OutboxItem] = Field(min_length=1, max_length=50)

class SyncOutcome(StrEnum):
    applied = "applied"
    duplicate = "duplicate"
    conflict_resolved = "conflict_resolved"
    conflict_pending = "conflict_pending"
    rejected = "rejected"

class SyncItemResult(ResponseModel):
    idempotency_key: UUID4
    local_ref: str | None = None
    outcome: SyncOutcome
    ticket: Ticket | None = None
    error: ErrorBody | None = None

class SyncPushResponse(ResponseModel):
    results: list[SyncItemResult] = Field(description="Same order as the request.")
    server_time: AwareDatetime

class QueueConfigWithId(QueueConfig):
    queue_id: str | None = None

class SyncPullResponse(ResponseModel):
    next_cursor: str
    has_more: bool
    tickets: list[Ticket]
    clinics: list[Clinic]
    service_points: list[ServicePoint]
    queue_configs: list[QueueConfigWithId] | None = Field(default=None, description="Staff only.")
    server_time: AwareDatetime
