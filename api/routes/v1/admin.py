from typing import Annotated

from fastapi import APIRouter, Depends, Query, Response, status

from core.auth import Caller, require_roles
from core.clock import service_date
from schemas import (
    AnalyticsQuery,
    AnalyticsSnapshot,
    AppError,
    AssignStaffRequest,
    Clinic,
    CreateClinicRequest,
    CreateServicePointRequest,
    CreateStaffRequest,
    ErrorCode,
    QueueConfig,
    ResolveConflictRequest,
    Role,
    ServicePoint,
    StaffAccount,
    StaffRole,
    SyncLogEntry,
    SyncLogStatus,
    UpdateStaffRequest,
)
from schemas.common import ItemList
from schemas.db.mappers import clinic_to_api, service_point_to_api, sync_log_to_api
from services import analytics_service, clinic_service, queue_service, sync_log_service, user_service

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
        clinic_id=data.get("clinic_id"),
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


# Staff CRUD
# A manager runs one clinic's front line, only an it_admin may create or change managers and admins.
MANAGER_ROLES = frozenset({StaffRole.receptionist, StaffRole.nurse})


def _may_grant(caller: Caller, role: StaffRole) -> None:
    if caller.role is not Role.it_admin and role not in MANAGER_ROLES:
        raise AppError(ErrorCode.FORBIDDEN, detail=f"{caller.uid} cannot grant {role}")


def _may_manage(caller: Caller, data: dict) -> None:
    _own_clinic_only(caller, data.get("clinic_id"))
    _may_grant(caller, StaffRole(data["role"]))


@router.post("/staff", response_model=StaffAccount, status_code=status.HTTP_201_CREATED)
async def create_staff(body: CreateStaffRequest, caller: ClinicAdmin) -> StaffAccount:
    _own_clinic_only(caller, body.clinic_id)
    _may_grant(caller, body.role)
    clinic = await clinic_service.get_clinic(body.clinic_id)
    if not clinic.is_active:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"clinic {body.clinic_id} is inactive")
    uid, data = await user_service.create_staff(body)
    await user_service.send_staff_invite(body.email, body.display_name, body.role, clinic.name)
    return _staff_account(uid, data)


@router.get("/staff", response_model=ItemList[StaffAccount])
async def list_staff(caller: ClinicAdmin, clinic_id: str | None = None) -> ItemList[StaffAccount]:
    # A bound manager always sees their own clinic, whatever they ask for.
    if clinic_id is not None:
        _own_clinic_only(caller, clinic_id)
    staff = await user_service.list_staff(caller.clinic_id or clinic_id)
    return ItemList[StaffAccount](items=[_staff_account(uid, d) for uid, d in staff])


@router.get("/staff/{staff_id}", response_model=StaffAccount)
async def get_staff(staff_id: str, caller: ClinicAdmin) -> StaffAccount:
    uid, data = await user_service.get_staff(staff_id)
    _own_clinic_only(caller, data.get("clinic_id"))
    return _staff_account(uid, data)


@router.patch("/staff/{staff_id}", response_model=StaffAccount)
async def update_staff(staff_id: str, body: UpdateStaffRequest, caller: ClinicAdmin) -> StaffAccount:
    _, current = await user_service.get_staff(staff_id)
    _may_manage(caller, current)
    if staff_id == caller.uid:
        if body.is_active is False:
            raise AppError(ErrorCode.CANNOT_DEACTIVATE_SELF)
        if body.role is not None and body.role != current["role"]:
            raise AppError(ErrorCode.FORBIDDEN, detail=f"{caller.uid} cannot change their own role")
    if body.role is not None:
        _may_grant(caller, body.role)
    if body.clinic_id is not None and body.clinic_id != current.get("clinic_id"):
        _own_clinic_only(caller, body.clinic_id)
        if not (await clinic_service.get_clinic(body.clinic_id)).is_active:
            raise AppError(ErrorCode.NOT_FOUND, detail=f"clinic {body.clinic_id} is inactive")
    uid, data = await user_service.update_staff(staff_id, body.model_dump(exclude_unset=True))
    return _staff_account(uid, data)


@router.delete("/staff/{staff_id}", status_code=status.HTTP_204_NO_CONTENT)
async def deactivate_staff(staff_id: str, caller: ClinicAdmin) -> Response:
    # The account is kept for the audit trail and ticket history, it just can no longer sign in.
    if staff_id == caller.uid:
        raise AppError(ErrorCode.CANNOT_DEACTIVATE_SELF)
    _, current = await user_service.get_staff(staff_id)
    _may_manage(caller, current)
    await user_service.update_staff(staff_id, {"is_active": False})
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# Queue configuration
async def _scoped_service_point(caller: Caller, service_point_id: str):
    service_point = await clinic_service.get_service_point(service_point_id)
    _own_clinic_only(caller, service_point.clinic_id)
    return service_point


@router.get("/service-points/{service_point_id}/queue-config", response_model=QueueConfig)
async def get_queue_config(service_point_id: str, caller: ClinicAdmin) -> QueueConfig:
    return (await _scoped_service_point(caller, service_point_id)).config


@router.put("/service-points/{service_point_id}/queue-config", response_model=QueueConfig)
async def put_queue_config(service_point_id: str, body: QueueConfig, caller: ClinicAdmin) -> QueueConfig:
    await _scoped_service_point(caller, service_point_id)
    return (await clinic_service.update_service_point_config(service_point_id, body)).config


# Sync log
@router.get("/sync-log", response_model=ItemList[SyncLogEntry])
async def list_sync_log(
    caller: ClinicAdmin,
    clinic_id: str | None = None,
    status_filter: Annotated[SyncLogStatus | None, Query(alias="status")] = None,
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
) -> ItemList[SyncLogEntry]:
    if clinic_id is not None:
        _own_clinic_only(caller, clinic_id)
    entries = await sync_log_service.list_entries(caller.clinic_id or clinic_id, status_filter, limit)
    return ItemList[SyncLogEntry](items=[sync_log_to_api(e) for e in entries])


@router.post("/sync-log/{entry_id}/resolve", response_model=SyncLogEntry)
async def resolve_sync_conflict(entry_id: str, body: ResolveConflictRequest, caller: ClinicAdmin) -> SyncLogEntry:
    entry = await sync_log_service.resolve_conflict(entry_id, body, caller.uid, caller.clinic_id)
    return sync_log_to_api(entry)


# Analytics
@router.get("/clinics/{clinic_id}/analytics", response_model=ItemList[AnalyticsSnapshot])
async def clinic_analytics(clinic_id: str, query: Annotated[AnalyticsQuery, Query()], caller: ClinicAdmin):
    _own_clinic_only(caller, clinic_id)
    await clinic_service.get_clinic(clinic_id)
    snapshots = await analytics_service.daily_snapshots(clinic_id, query.from_, query.to)
    if query.format == "csv":
        filename = f"analytics-{clinic_id}-{query.from_}-{query.to}.csv"
        return Response(
            analytics_service.to_csv(snapshots),
            media_type="text/csv",
            headers={"Content-Disposition": f'attachment; filename="{filename}"'},
        )
    return ItemList[AnalyticsSnapshot](items=snapshots)
