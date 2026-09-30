from fastapi import FastAPI

from core.config import settings
from routes import health

app = FastAPI(
    title=settings.APP_NAME,
    docs_url="/docs" if settings.is_dev else None,
    redoc_url="/redoc" if settings.is_dev else None,
    openapi_url="/openapi.json" if settings.is_dev else None,
)

app.include_router(health.router)
