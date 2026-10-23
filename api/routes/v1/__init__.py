from fastapi import APIRouter

from routes.v1 import admin, auth, live, meta, patients, queues, tickets

router = APIRouter(prefix="/v1")

router.include_router(admin.router)
router.include_router(auth.router)
router.include_router(meta.router)
router.include_router(live.router)
router.include_router(patients.router)
router.include_router(queues.router)
router.include_router(tickets.router)
