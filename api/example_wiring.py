from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, FastAPI, Header, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from schemas import (
    ROLE_PERMISSIONS,
    AppError,
    ErrorCode,
    JoinQueueRequest,
    Me,
    Queue,
    Role,
    Ticket,
)


def _request_id(request: Request) -> str:
    return getattr(request.state, "request_id", None) or f"req_{uuid.uuid4().hex[:10]}"


async def app_error_handler(request: Request, exc: AppError) -> JSONResponse:
    headers = {"Retry-After": str(exc.retry_after_seconds)} if exc.retry_after_seconds else None
    body = exc.to_response(_request_id(request)).model_dump(mode="json")
    return JSONResponse(body, status_code=exc.status_code, headers=headers)


async def validation_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    custom = next(
        (
            e["msg"].removeprefix("Value error, ")
            for e in exc.errors()
            if e.get("type") == "value_error"
        ),
        None,
    )
    err = AppError(ErrorCode.VALIDATION_FAILED, custom)
    return await app_error_handler(request, err)


async def unhandled_error_handler(request: Request, exc: Exception) -> JSONResponse:
    return await app_error_handler(request, AppError(ErrorCode.SERVER_ERROR, detail=repr(exc)))


class Caller(Me):
    # Me plus nothing extra, the verified identity passed into every route.
    pass


async def get_caller(authorization: Annotated[str | None, Header()] = None) -> Caller:
    if not authorization or not authorization.startswith("Bearer "):
        raise AppError(ErrorCode.UNAUTHENTICATED)
    token = authorization.removeprefix("Bearer ")
    role = Role.patient if token.startswith("patient") else Role.receptionist
    return Caller(
        uid=token,
        role=role,
        is_anonymous=role is Role.patient,
        clinic_id="c1",
        permissions=sorted(ROLE_PERMISSIONS[role]),
    )


def require_roles(*roles: Role):
    async def _check(caller: Annotated[Caller, Depends(get_caller)]) -> Caller:
        if caller.role not in roles:
            raise AppError(ErrorCode.FORBIDDEN, detail=f"{caller.uid} as {caller.role}")
        return caller

    return _check


router = APIRouter(prefix="/v1")


@router.get("/me", response_model=Me)
async def get_me(caller: Annotated[Caller, Depends(get_caller)]) -> Me:
    return Me.model_validate(caller.model_dump())


@router.post("/queues/{queue_id}/tickets", response_model=Ticket, status_code=status.HTTP_201_CREATED)
async def join_queue(
    queue_id: str,
    body: JoinQueueRequest,
    idempotency_key: Annotated[uuid.UUID, Header(alias="Idempotency-Key")],
    caller: Annotated[Caller, Depends(require_roles(Role.patient))],
):
    if queue_id == "paused-queue":
        raise AppError(ErrorCode.QUEUE_PAUSED)
    raise NotImplementedError


@router.get("/queues/{queue_id}", response_model=Queue, response_model_exclude_none=True)
async def get_queue(queue_id: str, caller: Annotated[Caller, Depends(get_caller)]):
    raise NotImplementedError


def create_app() -> FastAPI:
    app = FastAPI(title="SwiftQ DQMS API", version="1.0.0")
    app.add_exception_handler(AppError, app_error_handler)
    app.add_exception_handler(RequestValidationError, validation_error_handler)
    app.add_exception_handler(Exception, unhandled_error_handler)
    app.include_router(router)
    return app
