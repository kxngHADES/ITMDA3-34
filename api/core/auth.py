import asyncio
from typing import Annotated

from fastapi import Depends, Header

from schemas import ROLE_PERMISSIONS, AppError, ErrorCode, Me, Role
from services import token_service


class Caller(Me):
    # Me plus nothing extra, the verified identity passed into every route.
    pass


def build_caller(
    uid: str,
    role: Role,
    *,
    is_anonymous: bool = False,
    clinic_id: str | None = None,
    display_name: str | None = None,
) -> Caller:
    # Permissions always come from the role, never from the client.
    return Caller(
        uid=uid,
        role=role,
        is_anonymous=is_anonymous,
        clinic_id=clinic_id,
        display_name=display_name,
        permissions=sorted(ROLE_PERMISSIONS[role]),
    )


async def verify_token(token: str) -> Caller:
    try:
        claims = token_service.decode_access_token(token)
    except AppError:
        # Not one of our own tokens, so try it as a Firebase anonymous token, which is always a patient.
        uid = await asyncio.to_thread(token_service.verify_anonymous_firebase_token, token)
        return build_caller(uid, Role.patient, is_anonymous=True)
    return build_caller(claims.uid, claims.role, display_name=None)


async def get_caller(authorization: Annotated[str | None, Header()] = None) -> Caller:
    if not authorization or not authorization.startswith("Bearer "):
        raise AppError(ErrorCode.UNAUTHENTICATED)
    return await verify_token(authorization.removeprefix("Bearer "))


def require_roles(*roles: Role):
    async def _check(caller: Annotated[Caller, Depends(get_caller)]) -> Caller:
        if caller.role not in roles:
            raise AppError(ErrorCode.FORBIDDEN, detail=f"{caller.uid} as {caller.role}")
        return caller

    return _check
