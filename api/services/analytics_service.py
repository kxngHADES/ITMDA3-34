import asyncio
import csv
import datetime as dt
import io
import math
from collections import Counter
from typing import Any

from google.cloud.firestore_v1.base_query import FieldFilter

from core.clock import SAST
from core.firebase import get_firestore
from schemas import AnalyticsSnapshot, AppError, CallsPerStaff, ErrorCode, TicketStatus
from services.queue_service import QUEUES_COLLECTION

MAX_RANGE_DAYS = 92
CSV_COLUMNS = [
    "date",
    "clinic_id",
    "tickets_joined",
    "tickets_served",
    "no_shows",
    "cancellations",
    "avg_wait_minutes",
    "p90_wait_minutes",
    "peak_hour",
    "joins_app",
    "joins_sms",
    "joins_staff_assisted",
    "calls_per_staff",
]


def check_range(start: dt.date, end: dt.date) -> None:
    if (end - start).days + 1 > MAX_RANGE_DAYS:
        raise AppError(ErrorCode.DATE_RANGE_TOO_LARGE)


def _load_queues_sync(clinic_id: str, start: dt.date, end: dt.date) -> list[dict[str, Any]]:
    query = get_firestore().collection(QUEUES_COLLECTION).where(filter=FieldFilter("clinic_id", "==", clinic_id))
    queues = (s.to_dict() for s in query.stream())
    # service_date is stored as an ISO string, which sorts the same as the date.
    return [q for q in queues if start.isoformat() <= str(q.get("service_date")) <= end.isoformat()]


def _percentile(sorted_values: list[float], fraction: float) -> float:
    # Nearest rank, so the value is always one that really happened.
    return sorted_values[max(math.ceil(fraction * len(sorted_values)) - 1, 0)]


def snapshot_for_day(clinic_id: str, day: dt.date, queues: list[dict[str, Any]]) -> AnalyticsSnapshot:
    # Waits are joined_at to called_at, tickets that were never called have no wait.
    tickets = [t for q in queues for t in q.get("tickets", {}).values()]
    waits = sorted(
        (t["called_at"] - t["joined_at"]).total_seconds() / 60
        for t in tickets
        if t.get("called_at") and t.get("joined_at")
    )
    hours = Counter(t["joined_at"].astimezone(SAST).hour for t in tickets if t.get("joined_at"))
    calls = Counter(t["called_by"] for t in tickets if t.get("called_by"))
    return AnalyticsSnapshot(
        clinic_id=clinic_id,
        date=day,
        tickets_joined=len(tickets),
        tickets_served=sum(1 for t in tickets if t["status"] == TicketStatus.served.value),
        no_shows=sum(1 for t in tickets if t["status"] == TicketStatus.no_show.value),
        cancellations=sum(1 for t in tickets if t["status"] == TicketStatus.cancelled.value),
        avg_wait_minutes=round(sum(waits) / len(waits), 1) if waits else None,
        p90_wait_minutes=round(_percentile(waits, 0.9), 1) if waits else None,
        # Ties go to the earlier hour.
        peak_hour=min(hours, key=lambda h: (-hours[h], h)) if hours else None,
        joins_by_channel=dict(Counter(t["channel"] for t in tickets)),
        calls_per_staff=[CallsPerStaff(staff_id=s, calls=n) for s, n in sorted(calls.items())],
    )


async def daily_snapshots(clinic_id: str, start: dt.date, end: dt.date) -> list[AnalyticsSnapshot]:
    # One row per day in the range, including days nobody joined.
    check_range(start, end)
    queues = await asyncio.to_thread(_load_queues_sync, clinic_id, start, end)
    by_day: dict[str, list[dict[str, Any]]] = {}
    for q in queues:
        by_day.setdefault(str(q["service_date"]), []).append(q)
    days = (start + dt.timedelta(days=i) for i in range((end - start).days + 1))
    return [snapshot_for_day(clinic_id, d, by_day.get(d.isoformat(), [])) for d in days]


def _safe_cell(value: object) -> object:
    # Spreadsheet apps run cells that start with these as formulas, so none of ours may.
    text = "" if value is None else str(value)
    return "'" + text if text[:1] in ("=", "+", "-", "@", "\t", "\r") and not isinstance(value, (int, float)) else value


def to_csv(snapshots: list[AnalyticsSnapshot]) -> str:
    out = io.StringIO()
    writer = csv.writer(out, lineterminator="\r\n")
    writer.writerow(CSV_COLUMNS)
    for s in snapshots:
        channels = s.joins_by_channel or {}
        writer.writerow(
            _safe_cell(v)
            for v in (
                s.date.isoformat(),
                s.clinic_id,
                s.tickets_joined,
                s.tickets_served,
                s.no_shows,
                s.cancellations,
                s.avg_wait_minutes,
                s.p90_wait_minutes,
                s.peak_hour,
                channels.get("app", 0),
                channels.get("sms", 0),
                channels.get("staff_assisted", 0),
                ";".join(f"{c.staff_id}:{c.calls}" for c in s.calls_per_staff or []),
            )
        )
    return out.getvalue()
