import asyncio
import string
from collections.abc import Iterator

from core.firebase import get_firestore
from schemas import (
    DEFAULT_QUEUE_CONFIG,
    AppError,
    CreateClinicRequest,
    CreateServicePointRequest,
    ErrorCode,
)
from schemas.db.documents import ClinicDoc, QueueConfigDoc, ServicePointDoc

CLINICS_COLLECTION = "clinics"


def _create_clinic_sync(body: CreateClinicRequest) -> ClinicDoc:
    ref = get_firestore().collection(CLINICS_COLLECTION).document()
    doc = ClinicDoc(
        id=ref.id,
        name=body.name,
        address=body.address,
        opening_hours=body.opening_hours,
    )
    ref.set(doc.to_firestore())
    return doc


def _get_clinic_sync(clinic_id: str) -> ClinicDoc | None:
    snapshot = get_firestore().collection(CLINICS_COLLECTION).document(clinic_id).get()
    return ClinicDoc.from_firestore(snapshot.id, snapshot.to_dict()) if snapshot.exists else None


async def create_clinic(body: CreateClinicRequest) -> ClinicDoc:
    return await asyncio.to_thread(_create_clinic_sync, body)


async def get_clinic(clinic_id: str) -> ClinicDoc:
    doc = await asyncio.to_thread(_get_clinic_sync, clinic_id)
    if doc is None:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"clinic {clinic_id} does not exist")
    return doc


SERVICE_POINTS_COLLECTION = "service_points"
_LETTERS = string.ascii_uppercase


def prefix_candidates() -> Iterator[str]:
    # A to Z, then AA to ZZ, matching the two letter limit on display prefixes.
    yield from _LETTERS
    for first in _LETTERS:
        for second in _LETTERS:
            yield first + second


def next_free_prefix(taken: set[str]) -> str:
    return next(p for p in prefix_candidates() if p not in taken)


def _create_service_point_sync(clinic_id: str, body: CreateServicePointRequest) -> ServicePointDoc:
    db = get_firestore()
    existing = db.collection(SERVICE_POINTS_COLLECTION).where("clinic_id", "==", clinic_id).stream()
    prefix = next_free_prefix({s.to_dict().get("display_prefix", "A") for s in existing})
    ref = db.collection(SERVICE_POINTS_COLLECTION).document()
    doc = ServicePointDoc(
        id=ref.id,
        clinic_id=clinic_id,
        name=body.name,
        icon=body.icon,
        display_prefix=prefix,
        concurrent_servers=body.concurrent_servers,
        config=QueueConfigDoc.model_validate(DEFAULT_QUEUE_CONFIG.model_dump()),
    )
    ref.set(doc.to_firestore())
    return doc


def _get_service_point_sync(service_point_id: str) -> ServicePointDoc | None:
    snapshot = get_firestore().collection(SERVICE_POINTS_COLLECTION).document(service_point_id).get()
    return ServicePointDoc.from_firestore(snapshot.id, snapshot.to_dict()) if snapshot.exists else None


def _list_service_points_sync(clinic_id: str, active_only: bool) -> list[ServicePointDoc]:
    query = get_firestore().collection(SERVICE_POINTS_COLLECTION).where("clinic_id", "==", clinic_id)
    docs = [ServicePointDoc.from_firestore(s.id, s.to_dict()) for s in query.stream()]
    docs = [d for d in docs if d.is_active or not active_only]
    return sorted(docs, key=lambda d: d.display_prefix)


async def create_service_point(clinic_id: str, body: CreateServicePointRequest) -> ServicePointDoc:
    clinic = await get_clinic(clinic_id)
    if not clinic.is_active:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"clinic {clinic_id} is inactive")
    return await asyncio.to_thread(_create_service_point_sync, clinic_id, body)


async def get_service_point(service_point_id: str) -> ServicePointDoc:
    doc = await asyncio.to_thread(_get_service_point_sync, service_point_id)
    if doc is None:
        raise AppError(ErrorCode.NOT_FOUND, detail=f"service point {service_point_id} does not exist")
    return doc


async def list_service_points(clinic_id: str, active_only: bool = True) -> list[ServicePointDoc]:
    await get_clinic(clinic_id)
    return await asyncio.to_thread(_list_service_points_sync, clinic_id, active_only)
