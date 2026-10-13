import asyncio
import logging
from datetime import UTC, datetime

from firebase_admin import messaging
from google.cloud.firestore_v1.base_query import FieldFilter

from core.firebase import get_app, get_firestore
from schemas import PushTokenRequest

logger = logging.getLogger(__name__)

PUSH_TOKENS_COLLECTION = "push_tokens"


def _doc_id(uid: str, device_id: str) -> str:
    return f"{uid}_{device_id}"


def _register_sync(uid: str, body: PushTokenRequest) -> None:
    get_firestore().collection(PUSH_TOKENS_COLLECTION).document(_doc_id(uid, body.device_id)).set(
        {
            "uid": uid,
            "device_id": body.device_id,
            "token": body.token,
            "platform": body.platform,
            "updated_at": datetime.now(UTC),
        }
    )


def _remove_sync(uid: str, device_id: str) -> None:
    get_firestore().collection(PUSH_TOKENS_COLLECTION).document(_doc_id(uid, device_id)).delete()


def _tokens_sync(uid: str) -> list[tuple[str, str]]:
    query = get_firestore().collection(PUSH_TOKENS_COLLECTION).where(filter=FieldFilter("uid", "==", uid))
    return [(s.id, s.to_dict()["token"]) for s in query.stream()]


def _send_sync(uid: str, title: str, body: str, data: dict[str, str]) -> tuple[int, str | None]:
    tokens = _tokens_sync(uid)
    if not tokens:
        return 0, "no registered device"
    messages = [
        messaging.Message(token=token, notification=messaging.Notification(title=title, body=body), data=data)
        for _, token in tokens
    ]
    response = messaging.send_each(messages, app=get_app())
    for (doc_id, _), result in zip(tokens, response.responses, strict=True):
        if isinstance(result.exception, messaging.UnregisteredError):
            # The app was uninstalled or the token rotated, so it is dropped.
            get_firestore().collection(PUSH_TOKENS_COLLECTION).document(doc_id).delete()
    if response.success_count:
        return response.success_count, None
    first = next((r.exception for r in response.responses if r.exception), None)
    return 0, f"FCM rejected the message: {type(first).__name__}"


async def register_token(uid: str, body: PushTokenRequest) -> None:
    await asyncio.to_thread(_register_sync, uid, body)


async def remove_token(uid: str, device_id: str) -> None:
    await asyncio.to_thread(_remove_sync, uid, device_id)


async def send_push(
    uid: str, title: str, body: str, data: dict[str, str] | None = None
) -> tuple[bool, str | None]:
    # (True, None) when at least one of the user's devices accepted the message, else (False, the reason).
    try:
        sent, error = await asyncio.to_thread(_send_sync, uid, title, body, data or {})
    except Exception as exc:
        logger.exception("Push to %s failed", uid)
        return False, f"{type(exc).__name__}"
    return sent > 0, error
