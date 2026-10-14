import logging
from dataclasses import dataclass
from typing import Annotated

from fastapi import Depends, Request
from redis.exceptions import RedisError

from core.auth import Caller, get_caller
from core.config import settings
from core.redis import get_redis
from schemas import AppError, ErrorCode

logger = logging.getLogger(__name__)

KEY_PREFIX = "rl"

# Token bucket in one atomic step. Time comes from Redis so servers with different clocks agree.
# Returns {allowed, retry_after_seconds}.
_BUCKET_SCRIPT = """
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts = tonumber(data[2])
if tokens == nil then
  tokens = capacity
  ts = now
end
tokens = math.min(capacity, tokens + (now - ts) / 1000 * rate)
local allowed = 0
local retry = 0
if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
else
  retry = math.ceil((cost - tokens) / rate)
end
redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', KEYS[1], math.ceil(capacity / rate) + 1)
return {allowed, retry}
"""


@dataclass(frozen=True)
class Limit:
    capacity: int  # the burst a caller can use at once
    per_minute: int  # how fast the bucket refills


# Join: 5/min per device (burst 5) plus a per-IP cap. Call next: 60/min per staff account.
# The IP cap is looser because a clinic waiting room shares one connection.
LIMITS: dict[str, Limit] = {
    "join": Limit(capacity=5, per_minute=5),
    "join_ip": Limit(capacity=60, per_minute=60),
    "call_next": Limit(capacity=60, per_minute=60),
}


async def check(name: str, subject: str) -> None:
    # Spends one token for the subject, raises RATE_LIMITED with Retry-After when the bucket is empty.
    # Fails open: if Redis is down the request goes through.
    if not settings.RATE_LIMIT_ENABLED:
        return
    limit = LIMITS[name]
    try:
        script = get_redis().register_script(_BUCKET_SCRIPT)
        allowed, retry_after = await script(
            keys=[f"{KEY_PREFIX}:{name}:{subject}"], args=[limit.capacity, limit.per_minute / 60, 1]
        )
    except RedisError:
        logger.warning("Redis unavailable, %s rate limit skipped", name, exc_info=True)
        return
    if not allowed:
        raise AppError(
            ErrorCode.RATE_LIMITED, retry_after_seconds=max(int(retry_after), 1), detail=f"{name} limit for {subject}"
        )


def client_ip(request: Request) -> str:
    # Behind the host's proxy the real address is the entry the proxy added, counted from the right.
    forwarded = [part.strip() for part in request.headers.get("x-forwarded-for", "").split(",") if part.strip()]
    hops = settings.TRUSTED_PROXY_HOPS
    if forwarded and hops > 0:
        return forwarded[max(len(forwarded) - hops, 0)]
    return request.client.host if request.client else "unknown"


def limit_by_user(name: str):
    # Dependency: one bucket per signed-in account or anonymous device.
    async def _check(caller: Annotated[Caller, Depends(get_caller)]) -> None:
        await check(name, caller.uid)

    return _check


def limit_by_ip(name: str):
    # Dependency: one bucket per client address.
    async def _check(request: Request) -> None:
        await check(name, client_ip(request))

    return _check
