from typing import Annotated

from fastapi import APIRouter, Depends

from core.auth import Caller, get_caller
from schemas import Queue

router = APIRouter(tags=["Queues"])


@router.get("/queues/{queue_id}", response_model=Queue, response_model_exclude_none=True)
async def get_queue(queue_id: str, caller: Annotated[Caller, Depends(get_caller)]):
    raise NotImplementedError
