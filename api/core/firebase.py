from pathlib import Path

import firebase_admin
from firebase_admin import auth, credentials, firestore
from google.cloud.firestore import Client as FirestoreClient

from core.config import settings


def get_credentials() -> credentials.Certificate:
    path = Path(settings.FIREBASE_SERVICE_ACCOUNT_PATH)
    if not path.is_file():
        raise FileNotFoundError(f"Firebase service account file not found: {path}")
    return credentials.Certificate(str(path))


def init_firebase() -> firebase_admin.App:
    # Safe to call more than once, an already initialised app is reused.
    try:
        return firebase_admin.get_app()
    except ValueError:
        return firebase_admin.initialize_app(
            get_credentials(), {"projectId": settings.FIREBASE_PROJECT_ID}
        )


def get_app() -> firebase_admin.App:
    return firebase_admin.get_app()


def shutdown_firebase() -> None:
    try:
        firebase_admin.delete_app(firebase_admin.get_app())
    except ValueError:
        pass


def get_firestore() -> FirestoreClient:
    return firestore.client(get_app())


def set_role_claim(uid: str, role: str) -> None:
    auth.set_custom_user_claims(uid, {"role": role}, app=get_app())


def create_auth_user(email: str, display_name: str) -> str:
    # Raises auth.EmailAlreadyExistsError when the address is taken.
    return auth.create_user(email=email, display_name=display_name, app=get_app()).uid


def delete_auth_user(uid: str) -> None:
    auth.delete_user(uid, app=get_app())


def set_auth_disabled(uid: str, disabled: bool) -> None:
    auth.update_user(uid, disabled=disabled, app=get_app())


def update_auth_display_name(uid: str, display_name: str) -> None:
    auth.update_user(uid, display_name=display_name, app=get_app())


def revoke_refresh_tokens(uid: str) -> None:
    auth.revoke_refresh_tokens(uid, app=get_app())


def password_setup_link(email: str) -> str:
    return auth.generate_password_reset_link(email, app=get_app())
