import asyncio
import logging
import smtplib
from email.message import EmailMessage

from core.config import settings
from services.phone_service import gateway_number

logger = logging.getLogger(__name__)

SMTP_TIMEOUT_SECONDS = 15
# The gateway sends the first 160 characters, longer texts need long messages switched on in WinSMS.
SMS_MAX_CHARS = 160


def gateway_address(phone: str) -> str:
    # +27821234567 or 0821234567 becomes 27821234567@<carrier gateway>.
    return f"{gateway_number(phone)}@{settings.CARRIER_GATEWAY}"


def _build_message(phone: str, message: str) -> EmailMessage:
    msg = EmailMessage()
    msg["From"] = settings.SMTP_USER
    msg["To"] = gateway_address(phone)
    # The subject is only a reference, so the number is kept out of it.
    msg["Subject"] = settings.RESEND_FROM_NAME or "SwiftQ"
    msg.set_content(message)
    return msg


def _send_sync(msg: EmailMessage) -> None:
    with smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT, timeout=SMTP_TIMEOUT_SECONDS) as server:
        server.starttls()
        server.login(settings.SMTP_USER, settings.SMTP_PASS)
        server.send_message(msg)


async def send_sms(phone: str, message: str) -> bool:
    # Returns True once the gateway accepted the email, False if it could not be sent.
    if not (settings.CARRIER_GATEWAY and settings.SMTP_HOST and settings.SMTP_USER):
        logger.warning("SMS not sent, the carrier gateway or SMTP settings are missing")
        return False
    if len(message) > SMS_MAX_CHARS:
        logger.warning("SMS is %d characters, the gateway may cut it at %d", len(message), SMS_MAX_CHARS)
    try:
        msg = _build_message(phone, message)
        await asyncio.to_thread(_send_sync, msg)
    except (ValueError, OSError, smtplib.SMTPException):
        logger.exception("SMS to ...%s failed", phone[-4:])
        return False
    return True
