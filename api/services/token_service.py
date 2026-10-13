from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

import jwt
from firebase_admin import auth as firebase_auth
from firebase_admin.exceptions import FirebaseError

from core.config import settings
from core.firebase import get_app
from schemas import AppError, ErrorCode, Role


@dataclass(frozen=True)
class TokenClaims:
    uid: str
    email: str
    role: Role
    expires_at: datetime


def expires_in_seconds() -> int:
    return settings.JWT_ACCESS_TOKEN_EXPIRE_MINUTES * 60


def create_access_token(uid: str, email: str, role: Role) -> str:
    now = datetime.now(UTC)
    payload = {
        "sub": uid,
        "email": email,
        "role": role.value,
        "iat": now,
        "exp": now + timedelta(minutes=settings.JWT_ACCESS_TOKEN_EXPIRE_MINUTES),
    }
    return jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def decode_access_token(token: str) -> TokenClaims:
    try:
        payload = jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM],
            options={"require": ["sub", "role", "exp"]},
        )
        return TokenClaims(
            uid=payload["sub"],
            email=payload.get("email", ""),
            role=Role(payload["role"]),
            expires_at=datetime.fromtimestamp(payload["exp"], UTC),
        )
    except (jwt.PyJWTError, ValueError) as exc:
        raise AppError(ErrorCode.UNAUTHENTICATED, detail=f"invalid token: {exc}") from exc


def verify_anonymous_firebase_token(token: str) -> str:
    # Returns the uid of a valid Firebase anonymous ID token, anything else is rejected.
    try:
        claims = firebase_auth.verify_id_token(token, app=get_app())
    except (ValueError, FirebaseError) as exc:
        raise AppError(ErrorCode.UNAUTHENTICATED, detail=f"invalid token: {exc}") from exc
    if claims.get("firebase", {}).get("sign_in_provider") != "anonymous":
        raise AppError(ErrorCode.UNAUTHENTICATED, detail="only anonymous Firebase tokens are accepted")
    return claims["uid"]


def refresh_access_token(token: str) -> str:
    # Only a still valid token can be refreshed, an expired one means signing in again.
    claims = decode_access_token(token)
    return create_access_token(claims.uid, claims.email, claims.role)
