import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Response, status

from core.auth import Caller, get_caller, require_roles
from schemas import ConfirmPriorityRequest, JoinQueueRequest, NotificationLogEntry, Role, Ticket, TransferRequest
from schemas.common import ItemList
from schemas.db.mappers import notification_to_api
from services import idempotency_service, notification_manager, queue_service
from services.rate_limit_service import limit_by_ip, limit_by_user

router = APIRouter(tags=["Tickets"])


@router.post(
    "/queues/{queue_id}/tickets",
    response_model=Ticket,
    status_code=status.HTTP_201_CREATED,
    responses={status.HTTP_200_OK: {"model": Ticket}},
    dependencies=[Depends(limit_by_user("join")), Depends(limit_by_ip("join_ip"))],
)
async def join_queue(
    queue_id: str,
    body: JoinQueueRequest,
    response: Response,
    idempotency_key: Annotated[uuid.UUID, Header(alias="Idempotency-Key")],
    caller: Annotated[Caller, Depends(require_roles(Role.patient))],
) -> Ticket:
    key = str(idempotency_key)
    req_hash = idempotency_service.request_hash("join", queue_id, body.model_dump(mode="json"))

    replay = await idempotency_service.find(caller.uid, key, req_hash)
    if replay is not None:
        response.status_code = status.HTTP_200_OK
        return Ticket.model_validate(replay.response_body)

    try:
        ticket, created = await queue_service.join_queue(queue_id, caller.uid, body)
        if created:
            response.headers["Location"] = f"/v1/tickets/{ticket.id}"
        else:
            # The patient already held an active ticket, so nothing new was created.
            response.status_code = status.HTTP_200_OK

        await idempotency_service.save(
            caller.uid, key, req_hash, response.status_code or status.HTTP_201_CREATED, ticket.model_dump(mode="json")
        )
    finally:
        await idempotency_service.release(caller.uid, key)
    return ticket


@router.get(
    "/tickets/{ticket_id}",
    response_model=Ticket,
    response_model_exclude_none=True,
)
async def get_ticket(
    ticket_id: str,
    caller: Annotated[Caller, Depends(require_roles(Role.patient, Role.receptionist, Role.nurse, Role.manager))],
) -> Ticket:
    return await queue_service.get_ticket(ticket_id, caller.uid, caller.clinic_id)


@router.get("/tickets/{ticket_id}/notifications", response_model=ItemList[NotificationLogEntry])
async def list_ticket_notifications(
    ticket_id: str,
    caller: Annotated[Caller, Depends(require_roles(Role.patient, Role.receptionist, Role.nurse, Role.manager))],
) -> ItemList[NotificationLogEntry]:
    # The same visibility as the ticket itself: a patient's own, or any ticket at the staff member's clinic.
    await queue_service.get_ticket(ticket_id, caller.uid, caller.clinic_id)
    docs = await notification_manager.list_for_ticket(ticket_id)
    return ItemList[NotificationLogEntry](items=[notification_to_api(d) for d in docs])


@router.post("/tickets/{ticket_id}/cancel", response_model=Ticket)
async def cancel_ticket(
    ticket_id: str,
    idempotency_key: Annotated[uuid.UUID, Header(alias="Idempotency-Key")],
    caller: Annotated[Caller, Depends(require_roles(Role.patient))],
) -> Ticket:
    key = str(idempotency_key)
    req_hash = idempotency_service.request_hash("cancel", ticket_id)

    replay = await idempotency_service.find(caller.uid, key, req_hash)
    if replay is not None:
        return Ticket.model_validate(replay.response_body)

    try:
        ticket = await queue_service.cancel_ticket(ticket_id, caller.uid)
        await idempotency_service.save(
            caller.uid, key, req_hash, status.HTTP_200_OK, ticket.model_dump(mode="json")
        )
    finally:
        await idempotency_service.release(caller.uid, key)
    return ticket


StaffOnly = Annotated[Caller, Depends(require_roles(Role.receptionist, Role.nurse, Role.manager))]


@router.post("/tickets/{ticket_id}/no-show", response_model=Ticket)
async def mark_no_show(ticket_id: str, caller: StaffOnly) -> Ticket:
    # Goes back in the queue until it reaches the no-show limit, then it is removed.
    return await queue_service.mark_no_show(ticket_id, caller.clinic_id)


@router.post("/tickets/{ticket_id}/complete", response_model=Ticket)
async def complete_ticket(ticket_id: str, caller: StaffOnly) -> Ticket:
    # Marks the consultation done, and its length updates the wait time estimate.
    return await queue_service.complete_ticket(ticket_id, caller.clinic_id)


@router.post("/tickets/{ticket_id}/transfer", response_model=Ticket)
async def transfer_ticket(ticket_id: str, body: TransferRequest, caller: StaffOnly) -> Ticket:
    # Moves the patient to another service at the same clinic, keeping their place by join time.
    return await queue_service.transfer_ticket(ticket_id, body, caller.clinic_id)


@router.post("/tickets/{ticket_id}/confirm-priority", response_model=Ticket)
async def confirm_priority(ticket_id: str, body: ConfirmPriorityRequest, caller: StaffOnly) -> Ticket:
    # A confirmed category moves the ticket into the priority lane.
    return await queue_service.confirm_priority(ticket_id, caller.uid, body, caller.clinic_id)
