import hashlib
import uuid
from typing import Annotated

from fastapi import APIRouter, Body, Depends, Header, Response, status

from core.auth import Caller, get_caller, require_roles
from core.clock import service_date
from schemas import (
    CallNextRequest,
    PauseQueueRequest,
    Queue,
    Role,
    ServicePoint,
    Ticket,
    WalkInRequest,
)
from schemas.common import ItemList
from schemas.db.mappers import service_point_to_api
from services import clinic_service, idempotency_service, queue_service

router = APIRouter(tags=["Queues"])

AnyStaff = Annotated[Caller, Depends(require_roles(Role.receptionist, Role.nurse, Role.manager))]
QueueManager = Annotated[Caller, Depends(require_roles(Role.receptionist, Role.manager))]
FrontDesk = Annotated[Caller, Depends(require_roles(Role.receptionist))]


def _etag(queue: Queue) -> str:
    # Timestamps are left out so an unchanged queue keeps the same tag.
    body = queue.model_dump_json(exclude={"updated_at", "opened_at"}, exclude_none=True)
    return f'"{hashlib.sha1(body.encode()).hexdigest()[:16]}"'


@router.get("/clinics/{clinic_id}/service-points", response_model=ItemList[ServicePoint])
async def list_service_points(
    clinic_id: str,
    caller: Annotated[Caller, Depends(get_caller)],
    active: bool = True,
) -> ItemList[ServicePoint]:
    # Patients only see active service points whatever they ask for.
    only_active = active or caller.role is Role.patient
    docs = await clinic_service.list_service_points(clinic_id, only_active)
    today = service_date()
    items = []
    for doc in docs:
        # Joining opens the queue, so today's queue id is always known.
        doc.current_queue_id = queue_service.queue_id_for(clinic_id, doc.id, today)
        items.append(service_point_to_api(doc))
    return ItemList[ServicePoint](items=items)


@router.get("/queues/{queue_id}", response_model=Queue, response_model_exclude_none=True)
async def get_queue(
    queue_id: str,
    response: Response,
    caller: Annotated[Caller, Depends(get_caller)],
    if_none_match: Annotated[str | None, Header()] = None,
):
    queue = await queue_service.get_queue(
        queue_id,
        for_patient=caller.role is Role.patient,
        clinic_scope=None if caller.role is Role.patient else caller.clinic_id,
    )
    etag = _etag(queue)
    if if_none_match == etag:
        return Response(status_code=status.HTTP_304_NOT_MODIFIED, headers={"ETag": etag})
    response.headers["ETag"] = etag
    return queue


@router.post("/queues/{queue_id}/call-next", response_model=Ticket)
async def call_next(
    queue_id: str,
    caller: AnyStaff,
    body: Annotated[CallNextRequest | None, Body()] = None,
) -> Ticket:
    return await queue_service.call_next(queue_id, caller.uid, body, caller.clinic_id)


@router.post("/queues/{queue_id}/pause", response_model=Queue, response_model_exclude_none=True)
async def pause_queue(
    queue_id: str,
    caller: QueueManager,
    body: Annotated[PauseQueueRequest | None, Body()] = None,
) -> Queue:
    return await queue_service.set_paused(queue_id, True, body, caller.clinic_id)


@router.post("/queues/{queue_id}/resume", response_model=Queue, response_model_exclude_none=True)
async def resume_queue(queue_id: str, caller: QueueManager) -> Queue:
    return await queue_service.set_paused(queue_id, False, clinic_scope=caller.clinic_id)


@router.post(
    "/queues/{queue_id}/walk-ins",
    response_model=Ticket,
    status_code=status.HTTP_201_CREATED,
    responses={status.HTTP_200_OK: {"model": Ticket}},
)
async def add_walk_in(
    queue_id: str,
    body: WalkInRequest,
    response: Response,
    idempotency_key: Annotated[uuid.UUID, Header(alias="Idempotency-Key")],
    caller: FrontDesk,
) -> Ticket:
    key = str(idempotency_key)
    req_hash = idempotency_service.request_hash("walk-in", queue_id, body.model_dump(mode="json"))

    replay = await idempotency_service.find(caller.uid, key, req_hash)
    if replay is not None:
        response.status_code = status.HTTP_200_OK
        return Ticket.model_validate(replay.response_body)

    ticket = await queue_service.add_walk_in(queue_id, caller.uid, body, caller.clinic_id)
    response.headers["Location"] = f"/v1/tickets/{ticket.id}"
    await idempotency_service.save(
        caller.uid, key, req_hash, status.HTTP_201_CREATED, ticket.model_dump(mode="json")
    )
    return ticket
