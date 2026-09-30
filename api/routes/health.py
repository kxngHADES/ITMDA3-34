from fastapi import APIRouter

from core.config import settings

router = APIRouter(tags=["health"])


@router.get("/health")
async def health_check():
    return {"status": "ok", "environment": settings.ENVIRONMENT}
