import datetime as dt

import pytest
from fastapi.testclient import TestClient

from core.auth import build_caller, get_caller
from main import app
from schemas import AppError, ErrorCode, Role
from schemas.db.documents import PatientDoc, PatientPriorityDoc
from services import firebase_auth_service, patient_service, token_service
from services.firebase_auth_service import FirebaseUser

REGISTER = {"email": "thandi@example.com", "password": "correct horse", "display_name": "Thandi"}


@pytest.fixture
def client():
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture
def firebase(monkeypatch):
    # Stands in for the Firebase email and password REST calls.
    accounts: dict[str, tuple[str, str]] = {}
    deleted: list[str] = []

    async def sign_up(email, password):
        if email in accounts:
            raise AppError(ErrorCode.EMAIL_IN_USE, detail="EMAIL_EXISTS")
        accounts[email] = (f"uid_{len(accounts) + 1}", password)
        return FirebaseUser(uid=accounts[email][0], email=email)

    async def sign_in(email, password):
        if accounts.get(email, (None, None))[1] != password:
            raise AppError(ErrorCode.UNAUTHENTICATED, "Incorrect email or password.")
        return FirebaseUser(uid=accounts[email][0], email=email)

    monkeypatch.setattr(firebase_auth_service, "sign_up_with_password", sign_up)
    monkeypatch.setattr(firebase_auth_service, "sign_in_with_password", sign_in)
    monkeypatch.setattr(patient_service, "delete_auth_user", deleted.append)
    return {"accounts": accounts, "deleted": deleted}


def sign_in_as(uid="uid_1", role=Role.patient):
    app.dependency_overrides[get_caller] = lambda: build_caller(uid, role)


class TestRegister:
    def test_creates_the_account_and_returns_a_patient_token(self, client, firebase, patients):
        body = REGISTER | {"language": "zu", "notification_channel": "sms", "phone": "082 123 4567", "priority_request": "pregnant"}

        response = client.post("/v1/auth/patients/register", json=body)

        assert response.status_code == 201
        data = response.json()
        claims = token_service.decode_access_token(data["access_token"])
        assert claims.role is Role.patient
        assert claims.uid == data["patient"]["id"] == "uid_1"
        assert data["patient"]["language"] == "zu"
        assert data["patient"]["notification_channel"] == "sms"
        assert data["patient"]["phone_last4"] == "4567"
        assert data["patient"]["priority"] == {
            "category": "pregnant", "verified": False, "verified_at": None, "valid_until": None, "active": False,
        }
        stored = patients["uid_1"]
        assert stored["email"] == "thandi@example.com"
        assert stored["phone_ciphertext"] != b"+27821234567"

    def test_defaults_to_english_push_and_no_priority(self, client, firebase):
        data = client.post("/v1/auth/patients/register", json=REGISTER).json()["patient"]

        assert data["language"] == "en"
        assert data["notification_channel"] == "push"
        assert data["priority"]["category"] == "none"

    def test_urgent_cannot_be_saved(self, client, firebase):
        response = client.post("/v1/auth/patients/register", json=REGISTER | {"priority_request": "urgent"})

        assert response.status_code == 422

    def test_sms_needs_a_phone(self, client, firebase):
        response = client.post("/v1/auth/patients/register", json=REGISTER | {"notification_channel": "sms"})

        assert response.status_code == 422

    def test_a_taken_email_is_reported_without_mentioning_staff(self, client, firebase):
        client.post("/v1/auth/patients/register", json=REGISTER)

        response = client.post("/v1/auth/patients/register", json=REGISTER)

        assert response.status_code == 400
        assert response.json()["error"]["code"] == "EMAIL_IN_USE"
        assert "staff" not in response.json()["error"]["message"].lower()

    def test_a_failed_profile_write_removes_the_firebase_account(self, client, firebase, monkeypatch):
        def broken(*args):
            raise RuntimeError("firestore down")

        monkeypatch.setattr(patient_service, "_create_sync", broken)

        response = client.post("/v1/auth/patients/register", json=REGISTER)

        assert response.status_code == 500
        assert firebase["deleted"] == ["uid_1"]


class TestLogin:
    def test_signs_in_a_registered_patient(self, client, firebase, patients):
        client.post("/v1/auth/patients/register", json=REGISTER)

        response = client.post("/v1/auth/patients/login", json={"email": REGISTER["email"], "password": "correct horse"})

        assert response.status_code == 200
        assert response.json()["patient"]["id"] == "uid_1"
        assert patients["uid_1"]["last_sign_in_at"] is not None

    def test_a_staff_account_cannot_sign_in_as_a_patient(self, client, firebase):
        firebase["accounts"]["nurse@example.com"] = ("staff_uid", "pw123456")

        response = client.post("/v1/auth/patients/login", json={"email": "nurse@example.com", "password": "pw123456"})

        assert response.status_code == 403

    def test_wrong_password(self, client, firebase):
        client.post("/v1/auth/patients/register", json=REGISTER)

        response = client.post("/v1/auth/patients/login", json={"email": REGISTER["email"], "password": "nope"})

        assert response.status_code == 401


class TestRefresh:
    def test_issues_a_new_patient_token(self, client, firebase):
        token = client.post("/v1/auth/patients/register", json=REGISTER).json()["access_token"]

        response = client.post("/v1/auth/patients/refresh", headers={"Authorization": f"Bearer {token}"})

        assert response.status_code == 200
        assert response.json()["patient"]["id"] == "uid_1"

    def test_a_staff_token_is_refused(self, client):
        token = token_service.create_access_token("staff_uid", "n@example.com", Role.nurse, "c1")

        response = client.post("/v1/auth/patients/refresh", headers={"Authorization": f"Bearer {token}"})

        assert response.status_code == 403


class TestProfile:
    def add(self, patients, **priority):
        patients["uid_1"] = PatientDoc(
            id="uid_1", email="t@example.com", display_name="Thandi", priority=PatientPriorityDoc(**priority)
        ).to_firestore()

    def test_reads_the_profile(self, client, patients):
        until = dt.datetime.now(dt.UTC) + dt.timedelta(days=30)
        self.add(patients, category="pregnant", verified=True, verified_at=dt.datetime.now(dt.UTC), valid_until=until)
        sign_in_as()

        data = client.get("/v1/patients/me").json()

        assert data["priority"]["active"] is True
        assert data["priority"]["category"] == "pregnant"

    def test_staff_get_403(self, client):
        sign_in_as("n1", Role.nurse)

        assert client.get("/v1/patients/me").status_code == 403

    def test_a_caller_without_a_profile_gets_403(self, client):
        sign_in_as("anon")

        assert client.get("/v1/patients/me").status_code == 403

    def test_changing_the_category_clears_the_verification(self, client, patients):
        self.add(patients, category="elderly", verified=True, verified_at=dt.datetime.now(dt.UTC))
        sign_in_as()

        data = client.patch("/v1/patients/me", json={"priority_request": "disability"}).json()

        assert data["priority"] == {
            "category": "disability", "verified": False, "verified_at": None, "valid_until": None, "active": False,
        }

    def test_the_same_category_keeps_the_verification(self, client, patients):
        self.add(patients, category="elderly", verified=True, verified_at=dt.datetime.now(dt.UTC))
        sign_in_as()

        data = client.patch("/v1/patients/me", json={"priority_request": "elderly", "language": "af"}).json()

        assert data["priority"]["verified"] is True
        assert data["language"] == "af"

    def test_updates_contact_preferences(self, client, patients):
        self.add(patients)
        sign_in_as()

        data = client.patch("/v1/patients/me", json={"phone": "0821234567", "notification_channel": "whatsapp"}).json()

        assert data["notification_channel"] == "whatsapp"
        assert data["phone_last4"] == "4567"

    def test_sms_without_any_phone_is_refused(self, client, patients):
        self.add(patients)
        sign_in_as()

        response = client.patch("/v1/patients/me", json={"notification_channel": "sms"})

        assert response.status_code == 422

    def test_an_empty_body_is_refused(self, client, patients):
        self.add(patients)
        sign_in_as()

        assert client.patch("/v1/patients/me", json={}).status_code == 422
