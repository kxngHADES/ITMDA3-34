from fastapi import APIRouter

from core.config import settings

router = APIRouter(tags=["Meta"])


@router.get("/health")
async def health_check():
    return {"status": "ok", "version": settings.APP_VERSION, "environment": settings.ENVIRONMENT}
