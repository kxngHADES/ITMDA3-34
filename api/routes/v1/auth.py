from typing import Annotated

from fastapi import APIRouter, Depends, Header, status

from core.auth import Caller, require_roles
from schemas import AppError, ErrorCode, Role
from schemas.auth import AuthResponse, AuthUser, LoginRequest, RegisterRequest
from schemas.db.documents import PatientDoc
from schemas.db.mappers import patient_to_api
from schemas.patients import PatientAuthResponse, PatientLoginRequest, PatientRegisterRequest
from services import firebase_auth_service, patient_service, token_service, user_service
from services.rate_limit_service import limit_by_ip

router = APIRouter(prefix="/auth", tags=["Auth"])


def _auth_response(user: AuthUser) -> AuthResponse:
    token = token_service.create_access_token(user.id, user.email, Role(user.role.value), user.clinic_id)
    return AuthResponse(
        access_token=token,
        expires_in=token_service.expires_in_seconds(),
        user=user,
    )


@router.post("/login", response_model=AuthResponse)
async def login(body: LoginRequest) -> AuthResponse:
    firebase_user = await firebase_auth_service.sign_in_with_password(body.email, body.password)
    user = await user_service.get_active_user(firebase_user.uid)
    return _auth_response(user)


@router.post("/register", response_model=AuthResponse, status_code=status.HTTP_201_CREATED)
async def register(
    body: RegisterRequest,
    caller: Annotated[Caller, Depends(require_roles(Role.it_admin))],
) -> AuthResponse:
    # Only an it_admin can create accounts, whatever role the new account gets.
    firebase_user = await firebase_auth_service.sign_up_with_password(body.email, body.password)
    user = await user_service.create_user(
        firebase_user.uid, firebase_user.email, body.full_name, body.role, body.clinic_id
    )
    return _auth_response(user)


@router.post("/refresh", response_model=AuthResponse)
async def refresh(authorization: Annotated[str | None, Header()] = None) -> AuthResponse:
    if not authorization or not authorization.startswith("Bearer "):
        raise AppError(ErrorCode.UNAUTHENTICATED)
    claims = token_service.decode_access_token(authorization.removeprefix("Bearer "))
    # Role and active status are re-read so a changed role or a deactivated account takes effect.
    user = await user_service.get_active_user(claims.uid)
    return _auth_response(user)


# Patient accounts live in their own collection, so the staff routes above are unchanged.


def _patient_auth_response(patient: PatientDoc) -> PatientAuthResponse:
    token = token_service.create_access_token(patient.id, patient.email, Role.patient)
    return PatientAuthResponse(
        access_token=token,
        expires_in=token_service.expires_in_seconds(),
        patient=patient_to_api(patient),
    )


@router.post(
    "/patients/register",
    response_model=PatientAuthResponse,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(limit_by_ip("patient_auth_ip"))],
)
async def register_patient(body: PatientRegisterRequest) -> PatientAuthResponse:
    patient = await patient_service.register(body)
    return _patient_auth_response(patient)


@router.post(
    "/patients/login",
    response_model=PatientAuthResponse,
    dependencies=[Depends(limit_by_ip("patient_auth_ip"))],
)
async def login_patient(body: PatientLoginRequest) -> PatientAuthResponse:
    firebase_user = await firebase_auth_service.sign_in_with_password(body.email, body.password)
    patient = await patient_service.get_active_patient(firebase_user.uid, signing_in=True)
    return _patient_auth_response(patient)


@router.post("/patients/refresh", response_model=PatientAuthResponse)
async def refresh_patient(authorization: Annotated[str | None, Header()] = None) -> PatientAuthResponse:
    if not authorization or not authorization.startswith("Bearer "):
        raise AppError(ErrorCode.UNAUTHENTICATED)
    claims = token_service.decode_access_token(authorization.removeprefix("Bearer "))
    if claims.role is not Role.patient:
        raise AppError(ErrorCode.FORBIDDEN, detail=f"{claims.uid} as {claims.role} on patient refresh")
    patient = await patient_service.get_active_patient(claims.uid)
    return _patient_auth_response(patient)
