import asyncio
import time
import uuid

import pytest
from fastapi.testclient import TestClient
from redis.exceptions import RedisError

import core.redis
from core.auth import build_caller, get_caller
from core.config import settings
from main import app
from schemas import AppError, ErrorCode, Role
from services import idempotency_service as idem
from services import queue_service
from services import rate_limit_service as rl

pytestmark = pytest.mark.redis


def redis_is_up() -> bool:
    async def ping():
        try:
            return await asyncio.wait_for(core.redis.get_redis().ping(), 2)
        except Exception:
            return False
        finally:
            await core.redis.close_redis()

    return asyncio.run(ping())


@pytest.fixture(autouse=True)
def isolated(monkeypatch):
    if not redis_is_up():
        pytest.skip("no Redis server on REDIS_URL")
    # Each test uses its own key space and removes it afterwards.
    run_id = uuid.uuid4().hex[:8]
    monkeypatch.setattr(rl, "KEY_PREFIX", f"rltest{run_id}")
    monkeypatch.setattr(settings, "RATE_LIMIT_ENABLED", True)
    monkeypatch.setattr(idem, "_key", lambda uid, key: f"idemtest{run_id}:{uid}:{key}")
    monkeypatch.setattr(idem, "_lock_key", lambda uid, key: f"idemtest{run_id}:lock:{uid}:{key}")
    yield run_id

    async def cleanup():
        redis = core.redis.get_redis()
        for pattern in (f"rltest{run_id}:*", f"idemtest{run_id}:*"):
            async for found in redis.scan_iter(pattern):
                await redis.delete(found)
        await core.redis.close_redis()

    asyncio.run(cleanup())


def run(coro):
    async def wrapped():
        try:
            return await coro
        finally:
            await core.redis.close_redis()

    return asyncio.run(wrapped())


class TestRateLimit:
    def test_burst_is_allowed_then_blocked_with_retry_after(self, monkeypatch):
        monkeypatch.setitem(rl.LIMITS, "join", rl.Limit(capacity=5, per_minute=5))

        async def hammer():
            for _ in range(5):
                await rl.check("join", "device1")
            with pytest.raises(AppError) as exc:
                await rl.check("join", "device1")
            return exc.value

        error = run(hammer())

        assert error.code is ErrorCode.RATE_LIMITED
        assert error.status_code == 429
        # 5 per minute refills one token every 12 seconds
        assert 1 <= error.retry_after_seconds <= 12

    def test_each_subject_has_its_own_bucket(self, monkeypatch):
        monkeypatch.setitem(rl.LIMITS, "join", rl.Limit(capacity=1, per_minute=1))

        async def two_devices():
            await rl.check("join", "device1")
            await rl.check("join", "device2")

        run(two_devices())

    def test_tokens_refill_over_time(self, monkeypatch):
        monkeypatch.setitem(rl.LIMITS, "join", rl.Limit(capacity=1, per_minute=120))

        async def use_then_wait():
            await rl.check("join", "device1")
            with pytest.raises(AppError):
                await rl.check("join", "device1")
            await asyncio.sleep(0.7)
            await rl.check("join", "device1")

        run(use_then_wait())

    def test_fails_open_when_redis_is_down(self, monkeypatch):
        def broken():
            raise RedisError("down")

        monkeypatch.setattr(rl, "get_redis", broken)
        monkeypatch.setitem(rl.LIMITS, "join", rl.Limit(capacity=1, per_minute=1))

        async def calls():
            for _ in range(3):
                await rl.check("join", "device1")

        asyncio.run(calls())

    def test_disabled_setting_skips_the_check(self, monkeypatch):
        monkeypatch.setattr(settings, "RATE_LIMIT_ENABLED", False)
        monkeypatch.setitem(rl.LIMITS, "join", rl.Limit(capacity=1, per_minute=1))

        async def calls():
            for _ in range(3):
                await rl.check("join", "device1")

        asyncio.run(calls())

    def test_client_address_comes_from_the_proxy_header(self, monkeypatch):
        class Req:
            client = type("C", (), {"host": "10.0.0.1"})()
            headers = {"x-forwarded-for": "6.6.6.6, 41.1.2.3"}

        monkeypatch.setattr(settings, "TRUSTED_PROXY_HOPS", 1)
        assert rl.client_ip(Req()) == "41.1.2.3"
        monkeypatch.setattr(settings, "TRUSTED_PROXY_HOPS", 0)
        assert rl.client_ip(Req()) == "10.0.0.1"


class TestRateLimitedRoutes:
    @pytest.fixture
    def client(self):
        with TestClient(app, raise_server_exceptions=False) as c:
            yield c
        app.dependency_overrides.clear()

    def test_call_next_returns_429_with_retry_after(self, client, monkeypatch):
        app.dependency_overrides[get_caller] = lambda: build_caller("nurse1", Role.nurse, clinic_id="c1")
        monkeypatch.setitem(rl.LIMITS, "call_next", rl.Limit(capacity=2, per_minute=60))

        async def call_next(queue_id, uid, body, clinic_id):
            raise AppError(ErrorCode.QUEUE_EMPTY)

        monkeypatch.setattr(queue_service, "call_next", call_next)

        codes = [client.post("/v1/queues/q/call-next").status_code for _ in range(3)]
        limited = client.post("/v1/queues/q/call-next")

        assert codes[:2] == [400, 400]  # allowed through, the queue is simply empty
        assert codes[2] == 429
        assert limited.headers["Retry-After"] == "1"
        assert limited.json()["error"]["code"] == "RATE_LIMITED"

    def test_join_is_limited_per_device(self, client, monkeypatch):
        app.dependency_overrides[get_caller] = lambda: build_caller("device1", Role.patient, is_anonymous=True)
        monkeypatch.setitem(rl.LIMITS, "join", rl.Limit(capacity=1, per_minute=5))
        body = {"language": "en"}
        headers = {"Idempotency-Key": str(uuid.uuid4())}

        async def join(queue_id, uid, request):
            raise AppError(ErrorCode.QUEUE_PAUSED)

        monkeypatch.setattr(queue_service, "join_queue", join)

        first = client.post("/v1/queues/q/tickets", json=body, headers=headers)
        second = client.post("/v1/queues/q/tickets", json=body, headers=headers)

        assert first.status_code == 400  # reached the handler
        assert second.status_code == 429


class TestIdempotencyStore:
    def test_replays_the_saved_result(self):
        async def flow():
            assert await idem.find("u1", "k1", "hash") is None
            await idem.save("u1", "k1", "hash", 201, {"id": "t1"})
            await idem.release("u1", "k1")
            return await idem.find("u1", "k1", "hash")

        record = run(flow())

        assert (record.status_code, record.response_body) == (201, {"id": "t1"})

    def test_same_key_with_another_payload_is_rejected(self):
        async def flow():
            await idem.find("u1", "k1", "hash")
            await idem.save("u1", "k1", "hash", 201, {"id": "t1"})
            await idem.release("u1", "k1")
            await idem.find("u1", "k1", "different")

        with pytest.raises(AppError) as exc:
            run(flow())

        assert exc.value.code is ErrorCode.IDEMPOTENCY_KEY_REUSED

    def test_a_retry_during_the_request_waits_for_its_result(self):
        order = []

        async def slow_request():
            assert await idem.find("u1", "k1", "hash") is None
            order.append("first started")
            await asyncio.sleep(0.5)
            await idem.save("u1", "k1", "hash", 201, {"id": "t1"})
            await idem.release("u1", "k1")
            order.append("first saved")

        async def retry():
            await asyncio.sleep(0.1)
            record = await idem.find("u1", "k1", "hash")
            order.append("retry replayed")
            return record

        async def both():
            _, record = await asyncio.gather(slow_request(), retry())
            return record

        started = time.monotonic()
        record = run(both())

        assert order == ["first started", "first saved", "retry replayed"]
        assert record.response_body == {"id": "t1"}
        assert time.monotonic() - started < 2

    def test_a_failed_request_lets_the_retry_through(self):
        async def flow():
            assert await idem.find("u1", "k1", "hash") is None
            await idem.release("u1", "k1")  # the work failed, nothing was saved
            started = time.monotonic()
            again = await idem.find("u1", "k1", "hash")
            return again, time.monotonic() - started

        again, waited = run(flow())

        assert again is None
        assert waited < 1

    def test_stored_with_a_day_of_expiry(self, isolated):
        async def flow():
            await idem.save("u1", "k1", "hash", 201, {"id": "t1"})
            return await core.redis.get_redis().ttl(f"idemtest{isolated}:u1:k1")

        assert 0 < run(flow()) <= idem.TTL_SECONDS
