from dataclasses import dataclass

import httpx

from core.config import settings
from schemas import AppError, ErrorCode

_BASE_URL = "https://identitytoolkit.googleapis.com/v1/accounts"
_TIMEOUT = httpx.Timeout(10.0)


@dataclass(frozen=True)
class FirebaseUser:
    uid: str
    email: str


async def _post(action: str, payload: dict) -> dict:
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            response = await client.post(
                f"{_BASE_URL}:{action}",
                params={"key": settings.FIREBASE_WEB_API_KEY},
                json=payload,
            )
    except httpx.HTTPError as exc:
        raise AppError(ErrorCode.SERVER_ERROR, detail=f"firebase unreachable: {exc!r}") from exc

    if response.is_success:
        return response.json()

    # Firebase reports the reason as e.g. "INVALID_LOGIN_CREDENTIALS" or "EMAIL_EXISTS : ...".
    reason = response.json().get("error", {}).get("message", "UNKNOWN").split(" ")[0]
    raise _map_error(reason)


def _map_error(reason: str) -> AppError:
    if reason == "EMAIL_EXISTS":
        return AppError(ErrorCode.EMAIL_IN_USE, detail=reason)
    if reason in {"INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND", "USER_DISABLED"}:
        # Same message for every cause so the response does not reveal which emails exist.
        return AppError(ErrorCode.UNAUTHENTICATED, "Incorrect email or password.", detail=reason)
    if reason in {"WEAK_PASSWORD", "INVALID_EMAIL"}:
        return AppError(ErrorCode.VALIDATION_FAILED, detail=reason)
    if reason == "TOO_MANY_ATTEMPTS_TRY_LATER":
        return AppError(ErrorCode.RATE_LIMITED, retry_after_seconds=60, detail=reason)
    return AppError(ErrorCode.SERVER_ERROR, detail=reason)


async def sign_in_with_password(email: str, password: str) -> FirebaseUser:
    data = await _post(
        "signInWithPassword",
        {"email": email, "password": password, "returnSecureToken": True},
    )
    return FirebaseUser(uid=data["localId"], email=data["email"])


async def sign_up_with_password(email: str, password: str) -> FirebaseUser:
    data = await _post(
        "signUp",
        {"email": email, "password": password, "returnSecureToken": True},
    )
    return FirebaseUser(uid=data["localId"], email=data["email"])
