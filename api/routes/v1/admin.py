from typing import Annotated

from fastapi import APIRouter, Depends, status

from core.auth import Caller, require_roles
from core.clock import service_date
from schemas import Clinic, CreateClinicRequest, CreateServicePointRequest, Role, ServicePoint
from schemas.db.mappers import clinic_to_api, service_point_to_api
from services import clinic_service, queue_service

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
