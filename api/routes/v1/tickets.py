import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, status

from core.auth import Caller, require_roles
from schemas import JoinQueueRequest, Role, Ticket

router = APIRouter(tags=["Tickets"])


@router.post("/queues/{queue_id}/tickets", response_model=Ticket, status_code=status.HTTP_201_CREATED)
async def join_queue(
    queue_id: str,
    body: JoinQueueRequest,
    idempotency_key: Annotated[uuid.UUID, Header(alias="Idempotency-Key")],
    caller: Annotated[Caller, Depends(require_roles(Role.patient))],
):
    raise NotImplementedError
