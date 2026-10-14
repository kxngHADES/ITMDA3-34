import base64
import hashlib
from functools import lru_cache

from cryptography.fernet import Fernet, InvalidToken

from core.config import settings


@lru_cache
def _fernet() -> Fernet:
    if settings.PHONE_ENCRYPTION_KEY:
        return Fernet(settings.PHONE_ENCRYPTION_KEY.encode())
    # No dedicated key, so one is derived from the JWT secret to keep contact details off disk in plain text.
    return Fernet(base64.urlsafe_b64encode(hashlib.sha256(settings.JWT_SECRET_KEY.encode()).digest()))


def encrypt(value: str) -> bytes:
    return _fernet().encrypt(value.encode())


def decrypt(token: bytes) -> str | None:
    # None when the value was written with a different key.
    try:
        return _fernet().decrypt(bytes(token)).decode()
    except InvalidToken:
        return None
