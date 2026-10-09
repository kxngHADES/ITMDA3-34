from fastapi import APIRouter

from routes.v1 import meta, queues, tickets

router = APIRouter(prefix="/v1")

router.include_router(meta.router)
router.include_router(queues.router)
router.include_router(tickets.router)
