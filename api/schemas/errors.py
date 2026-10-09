# Error contact: one JSON shape plain-language messages, stable codes

from __future__ import annotations
from enum import StrEnum
from pydantic import Field
from .common import ResponseModel

class ErrorCode(StrEnum):
    VALIDATION_FAILED = "VALIDATION_FAILED"
    UNAUTHENTICATED = "UNAUTHENTICATED"
    FORBIDDEN = "FORBIDDEN"
    NOT_FOUND = "NOT_FOUND"
    RATE_LIMITED = "RATE_LIMITED"
    SERVER_ERROR = "SERVER_ERROR"
    CLINIC_CLOSED = "CLINIC_CLOSED"
    SERVICE_POINT_INACTIVE = "SERVICE_POINT_INACTIVE"
    QUEUE_PAUSED = "QUEUE_PAUSED"
    QUEUE_EMPTY = "QUEUE_EMPTY"
    INVALID_TRANSITION = "INVALID_TRANSITION"
    ALREADY_CANCELLED = "ALREADY_CANCELLED"
    TICKET_CLOSED = "TICKET_CLOSED"
    IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED"
    CROSS_CLINIC_TRANSFER = "CROSS_CLINIC_TRANSFER"
    CANNOT_DEACTIVATE_SELF = "CANNOT_DEACTIVATE_SELF"
    EMAIL_IN_USE = "EMAIL_IN_USE"
    CONFLICT_ALREADY_RESOLVED = "CONFLICT_ALREADY_RESOLVED"
    DATE_RANGE_TOO_LARGE = "DATE_RANGE_TOO_LARGE"
    CURSOR_EXPIRED = "CURSOR_EXPIRED"
    QUEUE_CLOSED = "QUEUE_CLOSED"
    QUEUE_ALREADY_OPEN = "QUEUE_ALREADY_OPEN"
    QUEUE_NOT_EMPTY = "QUEUE_NOT_EMPTY"
    QUEUE_HAS_TICKETS = "QUEUE_HAS_TICKETS"
    TICKET_STILL_ACTIVE = "TICKET_STILL_ACTIVE"


# code -> (HTTP status, retryable, default plain-language message)
ERROR_CATALOGUE: dict[ErrorCode, tuple[int, bool, str]] = {
    ErrorCode.VALIDATION_FAILED: (422, False, "Some details are missing or not in the right format. Please check and try again."),
    ErrorCode.UNAUTHENTICATED: (401, True, "Please sign in again."),
    ErrorCode.FORBIDDEN: (403, False, "Invalid details."),
    ErrorCode.NOT_FOUND: (404, False, "We couldn't find that. It may have been closed or moved."),
    ErrorCode.RATE_LIMITED: (429, True, "Please wait a moment before trying again."),
    ErrorCode.SERVER_ERROR: (500, True, "We couldn't reach the server. Your place is saved."),
    ErrorCode.CLINIC_CLOSED: (400, False, "This clinic is closed right now. Please check the opening hours."),
    ErrorCode.SERVICE_POINT_INACTIVE: (400, False, "This service isn't available today. Please choose another one."),
    ErrorCode.QUEUE_PAUSED: (400, True, "This queue is paused for a short while. Please try again in a few minutes."),
    ErrorCode.QUEUE_EMPTY: (400, False, "No one is waiting."),
    ErrorCode.INVALID_TRANSITION: (400, False, "That can't be done for this ticket right now. Please refresh."),
    ErrorCode.ALREADY_CANCELLED: (400, False, "This ticket was already cancelled."),
    ErrorCode.TICKET_CLOSED: (400, False, "This ticket is finished and can't be changed."),
    ErrorCode.IDEMPOTENCY_KEY_REUSED: (400, False, "Something went wrong with this request. Please try again."),
    ErrorCode.CROSS_CLINIC_TRANSFER: (400, False, "Tickets can only move to a service at the same clinic."),
    ErrorCode.CANNOT_DEACTIVATE_SELF: (400, False, "You can't deactivate your own account."),
    ErrorCode.EMAIL_IN_USE: (400, False, "A staff account already uses this email address."),
    ErrorCode.CONFLICT_ALREADY_RESOLVED: (400, False, "Someone else has already resolved this. Please refresh."),
    ErrorCode.DATE_RANGE_TOO_LARGE: (400, False, "Please choose a range of 92 days or less."),
    ErrorCode.CURSOR_EXPIRED: (400, False, "Your saved data is out of date. Refreshing everything now."),
    ErrorCode.QUEUE_CLOSED: (400, False, "This queue is closed for today. Please choose another service."),
    ErrorCode.QUEUE_ALREADY_OPEN: (400, False, "A queue is already open for this service today."),
    ErrorCode.QUEUE_NOT_EMPTY: (400, False, "There are still people in this queue. Serve or cancel them first."),
    ErrorCode.QUEUE_HAS_TICKETS: (400, False, "This queue has tickets, so it can't be deleted. Close it instead."),
    ErrorCode.TICKET_STILL_ACTIVE: (400, False, "Leave the queue first, then you can remove this ticket."),
}

class ErrorBody(ResponseModel):
    code: ErrorCode
    message: str = Field(description="Plain-language text safe to show the user. Never field names or codes.")
    retryable: bool
    retry_after_seconds: int | None = None
    request_id: str = Field(description="For support and the error log; not shown to patients.")

class ErrorResponse(ResponseModel):
    error: ErrorBody

class AppError(Exception):
    """Raise from anywhere in the service layer.
    >>> raise AppError(ErrorCode.QUEUE_PAUSED)
    >>> raise AppError(ErrorCode.RATE_LIMITED, retry_after_seconds=12)
    >>> raise AppError(ErrorCode.NOT_FOUND, detail="ticket tkt_123 owner mismatch") # detail -> error log only
    """

    def __init__(self, code: ErrorCode, 
                    message: str | None = None, *, 
                    retry_after_seconds: int | None = None,
                    detail: str | None = None
                 ):
        status, retryable, default_message = ERROR_CATALOGUE[code]
        self.code = code
        self.status_code = status
        self.retryable = retryable
        self.message = message or default_message
        self.retry_after_seconds = retry_after_seconds
        self.detail = detail
        super().__init__(f"{code}: {detail or self.message}")

    def to_response(self, request_id: str) -> ErrorResponse:
        return ErrorResponse(
        error=ErrorBody(
        code=self.code,
        message=self.message,
        retryable=self.retryable,
        retry_after_seconds=self.retry_after_seconds,
        request_id=request_id,
        )
    )