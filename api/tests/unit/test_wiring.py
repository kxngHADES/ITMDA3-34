# The example wiring returns the standard error shape for every failure class.
from fastapi.testclient import TestClient

from example_wiring import create_app

client = TestClient(create_app(), raise_server_exceptions=False)
KEY = {"Idempotency-Key": "3f1c2a9e-6b7d-4e0a-9f2b-1c8d5e7a4b60"}


def _err(r):
    return r.json()["error"]


def test_401_without_token():
    r = client.get("/v1/me")
    assert r.status_code == 401 and _err(r)["code"] == "UNAUTHENTICATED"


def test_me_for_patient():
    r = client.get("/v1/me", headers={"Authorization": "Bearer patient-uid"})
    assert r.status_code == 200 and r.json()["role"] == "patient"
    assert "ticket.own.join" in r.json()["permissions"]


def test_403_generic_message():
    r = client.post(
        "/v1/queues/q1/tickets",
        json={"language": "en"},
        headers={"Authorization": "Bearer staff-uid", **KEY},
    )
    assert r.status_code == 403 and _err(r)["message"] == "Invalid details."


def test_422_plain_language_from_validator():
    r = client.post(
        "/v1/queues/q1/tickets",
        json={"language": "en", "notification_channel": "sms"},
        headers={"Authorization": "Bearer patient-uid", **KEY},
    )
    assert r.status_code == 422
    assert _err(r)["message"] == "Add a phone number to get SMS or WhatsApp alerts."


def test_422_generic_for_schema_errors():
    r = client.post(
        "/v1/queues/q1/tickets",
        json={"language": "english!!"},
        headers={"Authorization": "Bearer patient-uid", **KEY},
    )
    assert r.status_code == 422 and "language" not in _err(r)["message"]


def test_400_business_rule():
    r = client.post(
        "/v1/queues/paused-queue/tickets",
        json={"language": "en"},
        headers={"Authorization": "Bearer patient-uid", **KEY},
    )
    assert r.status_code == 400 and _err(r)["code"] == "QUEUE_PAUSED"
    assert _err(r)["retryable"] is True


def test_500_hides_internals():
    r = client.post(
        "/v1/queues/q1/tickets",
        json={"language": "en"},
        headers={"Authorization": "Bearer patient-uid", **KEY},
    )
    assert r.status_code == 500
    assert _err(r)["message"] == "We couldn't reach the server. Your place is saved."
    assert "NotImplementedError" not in r.text


def test_missing_idempotency_key_is_422():
    r = client.post(
        "/v1/queues/q1/tickets",
        json={"language": "en"},
        headers={"Authorization": "Bearer patient-uid"},
    )
    assert r.status_code == 422
