from typing import Annotated

from fastapi import APIRouter, Depends, status

from core.auth import Caller, get_caller
from schemas import Me, PushTokenRequest
from services import push_service

router = APIRouter(tags=["Meta"])


@router.get("/me", response_model=Me)
async def get_me(caller: Annotated[Caller, Depends(get_caller)]) -> Me:
    return Me.model_validate(caller.model_dump())


@router.put("/me/push-token", status_code=status.HTTP_204_NO_CONTENT)
async def register_push_token(body: PushTokenRequest, caller: Annotated[Caller, Depends(get_caller)]):
    await push_service.register_token(caller.uid, body)


@router.delete("/me/push-token", status_code=status.HTTP_204_NO_CONTENT)
async def remove_push_token(device_id: str, caller: Annotated[Caller, Depends(get_caller)]):
    await push_service.remove_token(caller.uid, device_id)
