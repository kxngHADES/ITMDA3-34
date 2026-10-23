# Caller identity, push tokens and the NotificationLog (Meta tag + ticket notifications).

from __future__ import annotations
from datetime import datetime
from enum import StrEnum
from typing import Literal
from pydantic import AwareDatetime, Field
from .common import NotificationChannel, RequestModel, ResponseModel, Role
class Permission(StrEnum):
    """Derived from Ch.3 Table 11 so clients can show or hide menus. The server never trusts
    this list from a client — it re-derives it from the verified token on every request."""
    ticket_own_join = "ticket.own.join"
    ticket_own_cancel = "ticket.own.cancel"
    ticket_own_view = "ticket.own.view"
    queue_view = "queue.view"
    queue_call_next = "queue.call_next"
    queue_pause = "queue.pause"
    ticket_priority_view = "ticket.priority.view"
    analytics_view = "analytics.view"
    staff_manage = "staff.manage"
    sync_manage = "sync.manage"

# Used by GET /v1/me and by the RBAC dependency.
ROLE_PERMISSIONS: dict[Role, frozenset[Permission]] = {
    Role.patient: frozenset({Permission.ticket_own_join, Permission.ticket_own_cancel,
    Permission.ticket_own_view, Permission.queue_view}),
    Role.receptionist: frozenset({Permission.queue_view, Permission.queue_call_next, Permission.queue_pause,
    Permission.ticket_priority_view}),
    Role.nurse: frozenset({Permission.queue_view, Permission.queue_call_next,
    Permission.ticket_priority_view}),
    Role.manager: frozenset({Permission.queue_view, Permission.queue_call_next, Permission.queue_pause,
    Permission.ticket_priority_view, Permission.analytics_view}),
    Role.it_admin: frozenset({Permission.queue_view, Permission.staff_manage, Permission.sync_manage}),
}

class Me(ResponseModel):
    uid: str
    role: Role
    is_anonymous: bool
    clinic_id: str | None = None
    display_name: str | None = None
    permissions: list[Permission]

class PushTokenRequest(RequestModel):
    device_id: str = Field(min_length=8, max_length=64, description="Stable install id (same one used by sync).")
    token: str = Field(min_length=20, max_length=4096, description="FCM registration token.")
    platform: Literal["android", "web"]

class NotificationKind(StrEnum):
    joined = "joined"
    almost_your_turn = "almost_your_turn"
    called = "called"
    recalled = "recalled"
    queue_paused = "queue_paused"
    queue_closed = "queue_closed"
    priority_requested = "priority_requested"

class NotificationStatus(StrEnum):
    queued = "queued"
    sent = "sent"
    delivered = "delivered"
    failed = "failed"

class NotificationLogEntry(ResponseModel):
    id: str
    ticket_id: str
    channel: NotificationChannel
    kind: NotificationKind
    status: NotificationStatus
    fallback_of: str | None = Field(default=None, description="Id of the failed push this SMS replaced.")
    error: str | None = None
    created_at: AwareDatetime