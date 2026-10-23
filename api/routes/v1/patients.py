from typing import Annotated

from fastapi import APIRouter, Depends

from core.auth import Caller, require_roles
from schemas import Role
from schemas.db.mappers import patient_to_api
from schemas.patients import PatientProfile, UpdatePatientRequest
from services import patient_service

router = APIRouter(prefix="/patients", tags=["Patients"])

# Anonymous patients have no profile, so they get a 403 here like any other caller without one.
Patient = Annotated[Caller, Depends(require_roles(Role.patient))]


@router.get("/me", response_model=PatientProfile)
async def get_profile(caller: Patient) -> PatientProfile:
    return patient_to_api(await patient_service.get_active_patient(caller.uid))


@router.patch("/me", response_model=PatientProfile)
async def update_profile(body: UpdatePatientRequest, caller: Patient) -> PatientProfile:
    return patient_to_api(await patient_service.update_patient(caller.uid, body))
