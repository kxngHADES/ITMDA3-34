import asyncio
import hashlib
import json
import logging
from typing import Any

from redis.exceptions import RedisError

from core.redis import get_redis
from schemas import AppError, ErrorCode
from schemas.db.documents import IdempotencyRecord

logger = logging.getLogger(__name__)

TTL_SECONDS = 24 * 60 * 60
# While a request with a key is running, a second one with the same key waits for its result.
LOCK_SECONDS = 30
LOCK_WAIT_SECONDS = 5.0
LOCK_POLL_SECONDS = 0.1


def request_hash(*parts: Any) -> str:
    raw = json.dumps(parts, sort_keys=True, default=str)
    return hashlib.sha256(raw.encode()).hexdigest()


def _key(uid: str, key: str) -> str:
    return f"idem:{uid}:{key}"


def _lock_key(uid: str, key: str) -> str:
    return f"idem-lock:{uid}:{key}"


def _checked(raw: str, uid: str, key: str, req_hash: str) -> IdempotencyRecord:
    # Same key with a different payload is a client bug, so it is rejected rather than replayed.
    record = IdempotencyRecord.model_validate_json(raw)
    if record.request_hash != req_hash:
        raise AppError(ErrorCode.IDEMPOTENCY_KEY_REUSED, detail=f"key {key} reused by {uid}")
    return record


async def find(uid: str, key: str, req_hash: str) -> IdempotencyRecord | None:
    # Returns the saved result to replay. None means the caller should do the work, and it then holds
    # the key until release(), so a retry that arrives mid-flight waits for the result instead of repeating it.
    try:
        redis = get_redis()
        raw = await redis.get(_key(uid, key))
        if raw is not None:
            return _checked(raw, uid, key, req_hash)
        if await redis.set(_lock_key(uid, key), "1", nx=True, ex=LOCK_SECONDS):
            return None
        waited = 0.0
        while waited < LOCK_WAIT_SECONDS:
            await asyncio.sleep(LOCK_POLL_SECONDS)
            waited += LOCK_POLL_SECONDS
            raw = await redis.get(_key(uid, key))
            if raw is not None:
                return _checked(raw, uid, key, req_hash)
        # The other request is taking too long or failed, so this one goes ahead.
        return None
    except RedisError:
        logger.warning("Redis unavailable, idempotency lookup skipped", exc_info=True)
        return None


async def release(uid: str, key: str) -> None:
    # Lets the next request with this key through, whether or not the work succeeded.
    try:
        await get_redis().delete(_lock_key(uid, key))
    except RedisError:
        logger.warning("Redis unavailable, idempotency lock left to expire", exc_info=True)


async def save(uid: str, key: str, req_hash: str, status_code: int, body: dict[str, Any]) -> None:
    record = IdempotencyRecord(
        uid=uid, request_hash=req_hash, status_code=status_code, response_body=body
    )
    try:
        # NX keeps the first result if two requests with the same key race.
        await get_redis().set(
            _key(uid, key), record.model_dump_json(), nx=True, ex=TTL_SECONDS
        )
    except RedisError:
        logger.warning("Redis unavailable, idempotency record not stored", exc_info=True)
