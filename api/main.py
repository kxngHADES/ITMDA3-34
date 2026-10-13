import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from core.config import settings
from core.firebase import init_firebase, shutdown_firebase
from core.redis import close_redis
from routes import health, root, v1
from schemas import AppError, ErrorCode

logger = logging.getLogger(__name__)


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


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        init_firebase()
    except FileNotFoundError:
        # Keeps /health and the tests working without credentials, auth calls will fail until it is fixed.
        logger.exception("Firebase was not initialised")
    yield
    await close_redis()
    shutdown_firebase()


app = FastAPI(
    lifespan=lifespan,
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    docs_url="/docs" if settings.is_dev else None,
    redoc_url="/redoc" if settings.is_dev else None,
    openapi_url="/openapi.json" if settings.is_dev else None,
)

# Auth uses bearer tokens rather than cookies, so credentials stay off (required when origins is *)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.add_exception_handler(AppError, app_error_handler)
app.add_exception_handler(RequestValidationError, validation_error_handler)
app.add_exception_handler(Exception, unhandled_error_handler)

app.include_router(root.router)
app.include_router(health.router)
app.include_router(v1.router)
