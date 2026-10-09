from fastapi import APIRouter

from core.config import settings

router = APIRouter(tags=["root"])


@router.get("/")
async def root():
    return {
        "name": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "status": "running",
        "environment": settings.ENVIRONMENT,
    }
