from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from core.auth import build_caller, get_caller
from main import app
from schemas import ErrorResponse, Me, Role

IDEMPOTENCY_KEY = "3f1c2a9e-6b7d-4e0a-9f2b-1c8d5e7a4b60"
PUSH_TOKEN = {"device_id": "device-0001", "token": "f" * 40, "platform": "android"}


@pytest.fixture
def client() -> Iterator[TestClient]:
    # Unhandled errors must come back as a response here, not be re-raised into the test.
    with TestClient(app, raise_server_exceptions=False) as test_client:
        yield test_client
    app.dependency_overrides.clear()


def sign_in(role: Role) -> None:
    app.dependency_overrides[get_caller] = lambda: build_caller(
        "uid_1", role, is_anonymous=role is Role.patient, clinic_id="c1"
    )


def error_of(response) -> dict:
    # Every error must use the contract's error shape.
    return ErrorResponse.model_validate(response.json()).error.model_dump(mode="json")


class TestPublicEndpoints:
    def test_root(self, client, settings):
        response = client.get("/")

        assert response.status_code == 200
        assert response.json()["version"] == settings.APP_VERSION

    def test_health(self, client, settings):
        response = client.get("/health")

        assert response.status_code == 200
        assert response.json() == {
            "status": "ok",
            "version": settings.APP_VERSION,
            "environment": settings.ENVIRONMENT,
        }


class TestAuthentication:
    @pytest.mark.parametrize(
        ("method", "path"),
        [
            ("GET", "/v1/me"),
            ("PUT", "/v1/me/push-token"),
            ("DELETE", "/v1/me/push-token?device_id=device-0001"),
            ("GET", "/v1/queues/q1"),
            ("POST", "/v1/queues/q1/tickets"),
        ],
    )
    def test_v1_requires_a_bearer_token(self, client, method, path):
        response = client.request(method, path)

        assert response.status_code == 401
        assert error_of(response)["code"] == "UNAUTHENTICATED"

    @pytest.mark.parametrize("header", ["Basic abc", "Bearer", "token", "Bearer some-token"])
    def test_no_token_is_accepted_yet(self, client, header):
        response = client.get("/v1/me", headers={"Authorization": header})

        assert response.status_code == 401
        assert error_of(response)["code"] == "UNAUTHENTICATED"


class TestMe:
    @pytest.mark.parametrize("role", list(Role))
    def test_returns_the_caller(self, client, role):
        sign_in(role)

        response = client.get("/v1/me")

        assert response.status_code == 200
        me = Me.model_validate(response.json())
        assert me.uid == "uid_1"
        assert me.role is role
        assert me.permissions


class TestErrorShape:
    def test_validation_error(self, client):
        sign_in(Role.patient)

        response = client.put("/v1/me/push-token", json={**PUSH_TOKEN, "platform": "ios"})

        assert response.status_code == 422
        error = error_of(response)
        assert error["code"] == "VALIDATION_FAILED"
        assert error["retryable"] is False
        assert error["request_id"]

    def test_validation_error_uses_the_model_message(self, client):
        sign_in(Role.patient)

        response = client.post(
            "/v1/queues/q1/tickets",
            json={"language": "en", "notification_channel": "sms"},
            headers={"Idempotency-Key": IDEMPOTENCY_KEY},
        )

        assert response.status_code == 422
        assert error_of(response)["message"] == "Add a phone number to get SMS or WhatsApp alerts."

    def test_forbidden_role(self, client):
        sign_in(Role.receptionist)

        response = client.post(
            "/v1/queues/q1/tickets", json={"language": "en"}, headers={"Idempotency-Key": IDEMPOTENCY_KEY}
        )

        assert response.status_code == 403
        assert error_of(response)["code"] == "FORBIDDEN"

    def test_unhandled_error(self, client):
        sign_in(Role.patient)

        response = client.put("/v1/me/push-token", json=PUSH_TOKEN)

        assert response.status_code == 500
        error = error_of(response)
        assert error["code"] == "SERVER_ERROR"
        assert "NotImplementedError" not in response.text
