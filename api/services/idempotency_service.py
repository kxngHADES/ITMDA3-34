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


def request_hash(*parts: Any) -> str:
    raw = json.dumps(parts, sort_keys=True, default=str)
    return hashlib.sha256(raw.encode()).hexdigest()


def _key(uid: str, key: str) -> str:
    return f"idem:{uid}:{key}"


async def find(uid: str, key: str, req_hash: str) -> IdempotencyRecord | None:
    # Same key with a different payload is a client bug, so it is rejected rather than replayed.
    try:
        raw = await get_redis().get(_key(uid, key))
    except RedisError:
        logger.warning("Redis unavailable, idempotency lookup skipped", exc_info=True)
        return None
    if raw is None:
        return None
    record = IdempotencyRecord.model_validate_json(raw)
    if record.request_hash != req_hash:
        raise AppError(ErrorCode.IDEMPOTENCY_KEY_REUSED, detail=f"key {key} reused by {uid}")
    return record


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
