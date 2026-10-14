from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from core.config import Settings, get_settings
from main import app


@pytest.fixture(scope="session")
def settings() -> Settings:
    return get_settings()


@pytest.fixture
def client() -> Iterator[TestClient]:
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


@pytest.fixture(autouse=True)
def no_background_notifications(monkeypatch):
    # Queue changes start notification tasks, which would reach Firestore, FCM, SMTP and Resend.
    from services import queue_service

    monkeypatch.setattr(queue_service, "_spawn", lambda coro: coro.close())


@pytest.fixture(autouse=True)
def redis_stores(request, monkeypatch):
    # Rate limits and idempotency locks are switched off unless the test is marked redis, and those use a real Redis.
    import core.redis
    from core.config import settings as app_settings
    from services import idempotency_service

    if request.node.get_closest_marker("redis") is None:

        async def no_release(uid, key):
            return None

        monkeypatch.setattr(app_settings, "RATE_LIMIT_ENABLED", False)
        monkeypatch.setattr(idempotency_service, "release", no_release)
    yield
    # A client belongs to the event loop it was made in, so each test starts without one.
    core.redis._client = None
