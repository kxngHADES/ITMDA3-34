from typing import Annotated

from fastapi import APIRouter, Depends, status

from core.auth import Caller, require_roles
from core.clock import service_date
from schemas import (
    AppError,
    AssignStaffRequest,
    Clinic,
    CreateClinicRequest,
    CreateServicePointRequest,
    ErrorCode,
    Role,
    ServicePoint,
    StaffAccount,
    StaffRole,
)
from schemas.common import ItemList
from schemas.db.mappers import clinic_to_api, service_point_to_api
from services import clinic_service, queue_service, user_service

router = APIRouter(prefix="/admin", tags=["Admin"])

ClinicAdmin = Annotated[Caller, Depends(require_roles(Role.it_admin, Role.manager))]


@router.post("/clinics", response_model=Clinic, status_code=status.HTTP_201_CREATED)
async def create_clinic(body: CreateClinicRequest, caller: ClinicAdmin) -> Clinic:
    doc = await clinic_service.create_clinic(body)
    return clinic_to_api(doc)


@router.post(
    "/clinics/{clinic_id}/service-points",
    response_model=ServicePoint,
    status_code=status.HTTP_201_CREATED,
)
async def create_service_point(
    clinic_id: str, body: CreateServicePointRequest, caller: ClinicAdmin
) -> ServicePoint:
    doc = await clinic_service.create_service_point(clinic_id, body)
    doc.current_queue_id = queue_service.queue_id_for(clinic_id, doc.id, service_date())
    return service_point_to_api(doc)


def _own_clinic_only(caller: Caller, clinic_id: str) -> None:
    # A manager bound to a clinic only manages that clinic, an it_admin has no clinic and manages any.
    if caller.clinic_id is not None and caller.clinic_id != clinic_id:
        raise AppError(ErrorCode.FORBIDDEN, detail=f"{caller.uid} is not staff at {clinic_id}")


def _staff_account(uid: str, data: dict) -> StaffAccount:
    return StaffAccount(
        id=uid,
        email=data["email"],
        display_name=data.get("full_name", ""),
        role=StaffRole(data["role"]),
        clinic_id=data["clinic_id"],
        is_active=data.get("is_active", True),
        created_at=data["created_at"],
        updated_at=data.get("updated_at"),
        last_sign_in_at=data.get("last_sign_in_at"),
    )


@router.post("/clinics/{clinic_id}/staff", response_model=StaffAccount)
async def add_staff_to_clinic(clinic_id: str, body: AssignStaffRequest, caller: ClinicAdmin) -> StaffAccount:
    _own_clinic_only(caller, clinic_id)
    clinic = await clinic_service.get_clinic(clinic_id)
    if not clinic.is_active:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"clinic {clinic_id} is inactive")
    uid, data = await user_service.assign_clinic(body.user_id, clinic_id)
    return _staff_account(uid, data)


@router.get("/clinics/{clinic_id}/staff", response_model=ItemList[StaffAccount])
async def list_clinic_staff(clinic_id: str, caller: ClinicAdmin) -> ItemList[StaffAccount]:
    _own_clinic_only(caller, clinic_id)
    await clinic_service.get_clinic(clinic_id)
    staff = await user_service.list_clinic_staff(clinic_id)
    return ItemList[StaffAccount](items=[_staff_account(uid, d) for uid, d in staff])
