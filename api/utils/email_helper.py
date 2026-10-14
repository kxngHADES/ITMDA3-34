import asyncio
from typing import Any

import resend

from core.config import get_settings

_configured = False


def _configure() -> None:
    """Set resend.api_key from settings once per process. Idempotent, so no lock needed."""
    global _configured
    if not _configured:
        resend.api_key = get_settings().RESEND_API_KEY
        _configured = True


def _default_sender(from_email: str | None, from_name: str | None) -> str:
    settings = get_settings()
    name = from_name or settings.RESEND_FROM_NAME
    email = from_email or settings.RESEND_FROM_EMAIL
    return f"{name} <{email}>"


async def send_email(
    *,
    to: str | list[str],
    subject: str,
    html: str,
    from_email: str | None = None,
    from_name: str | None = None,
    reply_to: str | list[str] | None = None,
    cc: str | list[str] | None = None,
    bcc: str | list[str] | None = None,
    attachments: list[dict[str, Any]] | None = None,
    scheduled_at: str | None = None,
) -> resend.Emails.SendResponse:
    """Send a single email. `from_email`/`from_name` default to RESEND_FROM_EMAIL/_NAME settings."""
    _configure()
    params: resend.Emails.SendParams = {
        "from": _default_sender(from_email, from_name),
        "to": [to] if isinstance(to, str) else to,
        "subject": subject,
        "html": html,
    }
    if reply_to is not None:
        params["reply_to"] = reply_to
    if cc is not None:
        params["cc"] = cc
    if bcc is not None:
        params["bcc"] = bcc
    if attachments is not None:
        params["attachments"] = attachments
    if scheduled_at is not None:
        params["scheduled_at"] = scheduled_at
    return await asyncio.to_thread(resend.Emails.send, params)


async def send_batch_emails(
    emails: list[resend.Emails.SendParams],
) -> resend.Batch.SendResponse:
    """Send up to 100 emails in one call. Each dict is shaped like `send_email`'s params
    (to/subject/html required); "from" defaults to RESEND_FROM_EMAIL/RESEND_FROM_NAME if omitted.
    """
    _configure()
    params = [{"from": _default_sender(None, None), **email} for email in emails]
    return await asyncio.to_thread(resend.Batch.send, params)


async def get_email(email_id: str) -> resend.emails._email.Email:
    """Retrieve a single sent/scheduled email by ID."""
    _configure()
    return await asyncio.to_thread(resend.Emails.get, email_id=email_id)


async def update_email(email_id: str, scheduled_at: str) -> resend.Emails.UpdateEmailResponse:
    """Reschedule an email that hasn't sent yet (`scheduled_at` is an ISO 8601 timestamp)."""
    _configure()
    params: resend.Emails.UpdateParams = {"id": email_id, "scheduled_at": scheduled_at}
    return await asyncio.to_thread(resend.Emails.update, params)


async def cancel_email(email_id: str) -> resend.Emails.CancelScheduledEmailResponse:
    """Cancel a scheduled email before it sends."""
    _configure()
    return await asyncio.to_thread(resend.Emails.cancel, email_id=email_id)


async def share_email(email_id: str) -> resend.Emails.ShareEmailResponse:
    """Generate a shareable preview link for a sent email."""
    _configure()
    return await asyncio.to_thread(resend.Emails.share, email_id=email_id)


async def list_emails() -> resend.Emails.ListResponse:
    """List sent emails."""
    _configure()
    return await asyncio.to_thread(resend.Emails.list)


async def list_email_attachments(
    email_id: str,
) -> resend.emails._attachments.Attachments.ListResponse:
    """List attachments on a sent email."""
    _configure()
    return await asyncio.to_thread(resend.Emails.Attachments.list, email_id=email_id)


async def get_email_metrics(
    *,
    start_date: str,
    end_date: str,
    dimensions: list[str] | None = None,
    broadcast_id: list[str] | None = None,
) -> resend.Emails.MetricsResponse:
    """Retrieve sending metrics (opens, clicks, bounces, etc.) for a date range (YYYY-MM-DD)."""
    _configure()
    params: resend.Emails.MetricsParams = {"start_date": start_date, "end_date": end_date}
    if dimensions is not None:
        params["dimensions"] = dimensions
    if broadcast_id is not None:
        params["broadcast_id"] = broadcast_id
    return await asyncio.to_thread(resend.Emails.metrics, params)
