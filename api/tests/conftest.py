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
