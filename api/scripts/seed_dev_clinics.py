import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from google.cloud.firestore_v1.base_query import FieldFilter  # noqa: E402
from core.firebase import get_firestore, init_firebase  # noqa: E402
from schemas.clinics import OpeningHoursSlot  # noqa: E402
from schemas import DEFAULT_QUEUE_CONFIG  # noqa: E402
from schemas.db.documents import ClinicDoc, QueueConfigDoc, ServicePointDoc  # noqa: E402
from services.clinic_service import SERVICE_POINTS_COLLECTION  # noqa: E402

WEEKDAYS = ["mon", "tue", "wed", "thu", "fri"]


def _hours(opens: str, closes: str) -> list[OpeningHoursSlot]:
    return [OpeningHoursSlot(day=d, opens=opens, closes=closes) for d in WEEKDAYS]


# Fixed ids so re-running updates the same documents instead of adding duplicates.
DEV_CLINICS = [
    ClinicDoc(
        id="dev_laudium_chc",
        name="Laudium Community Health Centre",
        address="Cnr Bengal & 25th Avenue, Laudium, Pretoria, 0037",
        opening_hours=_hours("07:00", "16:00"),
    ),
    ClinicDoc(
        id="dev_olievenhout_ext13",
        name="Olievenhout Ext 13 Clinic",
        address="Olievenhoutbosch Ext 13, Centurion, 0175",
        opening_hours=[
            OpeningHoursSlot(day=d, opens="00:00", closes="23:59")
            for d in ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
        ],
    ),
]

# (id suffix, name); every service uses the ticket icon, priority (elderly, pregnant, disability,
# urgent) is picked on the ticket when joining, so it is not a service.
# Queue ids are split on dashes, so clinic and service point ids must not contain any.
LEGACY_CLINIC_IDS = ["dev-laudium-chc", "dev-olievenhout-ext13"]
TICKET_ICON = "ticket"
DEV_SERVICES = [
    ("general", "General consultation"),
    ("chronic", "Chronic medication"),
    ("immunisation", "Child immunisation"),
    ("family_planning", "Family planning"),
]


def _service_points(clinic: ClinicDoc) -> list[ServicePointDoc]:
    return [
        ServicePointDoc(
            id=f"{clinic.id}_{suffix}",
            clinic_id=clinic.id,
            name=name,
            icon=TICKET_ICON,
            display_prefix="ABCDEFGH"[i],
            config=QueueConfigDoc.model_validate(DEFAULT_QUEUE_CONFIG.model_dump()),
        )
        for i, (suffix, name) in enumerate(DEV_SERVICES)
    ]


def main() -> None:
    init_firebase()
    db = get_firestore()
    for legacy_id in LEGACY_CLINIC_IDS:
        db.collection("clinics").document(legacy_id).delete()
        legacy = db.collection(SERVICE_POINTS_COLLECTION).where(filter=FieldFilter("clinic_id", "==", legacy_id))
        for old in legacy.stream():
            old.reference.delete()
    for clinic in DEV_CLINICS:
        db.collection("clinics").document(clinic.id).set(clinic.to_firestore())
        print(f"seeded {clinic.id}: {clinic.name}")
        current = _service_points(clinic)
        keep = {sp.id for sp in current}
        stale = db.collection(SERVICE_POINTS_COLLECTION).where(filter=FieldFilter("clinic_id", "==", clinic.id))
        for old in stale.stream():
            if old.id not in keep:
                old.reference.delete()
                print(f"  removed old service {old.id}")
        for sp in current:
            db.collection(SERVICE_POINTS_COLLECTION).document(sp.id).set(sp.to_firestore())
            print(f"  service {sp.id}: {sp.display_prefix} {sp.name}")


if __name__ == "__main__":
    main()
