import asyncio
from typing import Annotated

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from core.auth import Caller, verify_token
from schemas import AppError, Queue, Role
from services import queue_service

router = APIRouter(tags=["Queues"])

STAFF_ROLES = {Role.receptionist, Role.nurse, Role.manager}
CLOSE_UNAUTHENTICATED = 4401
CLOSE_FORBIDDEN = 4403
CLOSE_NOT_FOUND = 4404


async def _caller(websocket: WebSocket, token: str | None) -> Caller | None:
    # Browsers cannot set headers on a WebSocket, so the token may also come as ?token=.
    header = websocket.headers.get("authorization", "")
    raw = token or (header.removeprefix("Bearer ") if header.startswith("Bearer ") else None)
    if not raw:
        return None
    try:
        return await verify_token(raw)
    except AppError:
        return None


def _message(queue: Queue) -> dict:
    return {
        "type": "queue",
        "status": queue.status.value,
        "pause_reason": queue.pause_reason,
        "now_serving": queue.now_serving,
        "waiting_count": queue.waiting_count,
        "queue": queue.model_dump(mode="json", exclude_none=True),
    }


def _fingerprint(queue: Queue) -> str:
    return queue.model_dump_json(exclude={"updated_at"}, exclude_none=True)


async def _push_changes(websocket: WebSocket, queue_id: str, clinic_scope: str | None) -> None:
    last = None
    async for _ in queue_service.changes(queue_id):
        queue = await queue_service.get_queue(queue_id, for_patient=False, clinic_scope=clinic_scope)
        current = _fingerprint(queue)
        if current != last:
            await websocket.send_json(_message(queue))
            last = current


async def _wait_for_disconnect(websocket: WebSocket) -> None:
    # Client messages are ignored, receiving only tells us when the socket closes.
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        return


@router.websocket("/queues/{queue_id}/live")
async def live_queue(
    websocket: WebSocket,
    queue_id: str,
    token: Annotated[str | None, Query()] = None,
) -> None:
    caller = await _caller(websocket, token)
    if caller is None:
        await websocket.close(code=CLOSE_UNAUTHENTICATED)
        return
    if caller.role not in STAFF_ROLES:
        await websocket.close(code=CLOSE_FORBIDDEN)
        return

    try:
        # Fails early with a close code when the queue does not exist or is another clinic's.
        await queue_service.get_queue(queue_id, for_patient=False, clinic_scope=caller.clinic_id)
    except AppError:
        await websocket.close(code=CLOSE_NOT_FOUND)
        return

    await websocket.accept()

    # The first pass of changes() sends the current snapshot straight away.
    pusher = asyncio.create_task(_push_changes(websocket, queue_id, caller.clinic_id))
    listener = asyncio.create_task(_wait_for_disconnect(websocket))
    try:
        await asyncio.wait({pusher, listener}, return_when=asyncio.FIRST_COMPLETED)
    finally:
        for task in (pusher, listener):
            task.cancel()
        await asyncio.gather(pusher, listener, return_exceptions=True)
