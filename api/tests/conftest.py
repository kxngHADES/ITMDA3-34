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


class _Snapshot:
    def __init__(self, data):
        self.exists = data is not None
        self._data = data

    def to_dict(self):
        return dict(self._data) if self._data is not None else None


class _DocRef:
    def __init__(self, store, doc_id):
        self._store, self._id = store, doc_id

    def get(self):
        return _Snapshot(self._store.get(self._id))

    def set(self, data):
        self._store[self._id] = dict(data)

    def update(self, data):
        self._store[self._id] = self._store[self._id] | data


@pytest.fixture(autouse=True)
def patients(monkeypatch):
    # An in-memory patients collection, so joins look up profiles without Firestore. Empty means anonymous.
    from services import patient_service

    store: dict[str, dict] = {}
    monkeypatch.setattr(patient_service, "_ref", lambda uid: _DocRef(store, uid))
    monkeypatch.setattr(patient_service, "set_role_claim", lambda uid, role: None)
    return store


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
