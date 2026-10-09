from fastapi import APIRouter

from core.config import settings

router = APIRouter(tags=["root"])


@router.get("/")
async def root():
    return {"name": settings.APP_NAME, "status": "running", "environment": settings.ENVIRONMENT}
