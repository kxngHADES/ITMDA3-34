from enum import StrEnum

from schemas.common import RequestModel, ResponseModel, Role


class Permission(StrEnum):
    TICKET_OWN_JOIN = "ticket.own.join"
    TICKET_OWN_CANCEL = "ticket.own.cancel"
    TICKET_OWN_VIEW = "ticket.own.view"
    QUEUE_VIEW = "queue.view"
    QUEUE_CALL_NEXT = "queue.call_next"
    QUEUE_PAUSE = "queue.pause"
    TICKET_PRIORITY_VIEW = "ticket.priority.view"
    ANALYTICS_VIEW = "analytics.view"
    STAFF_MANAGE = "staff.manage"
    SYNC_MANAGE = "sync.manage"


class PushPlatform(StrEnum):
    ANDROID = "android"
    WEB = "web"


class Me(ResponseModel):
    uid: str
    role: Role
    is_anonymous: bool
    clinic_id: str | None = None
    display_name: str | None = None
    permissions: list[Permission]


class PushTokenRequest(RequestModel):
    device_id: str
    token: str
    platform: PushPlatform
