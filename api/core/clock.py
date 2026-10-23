import datetime as dt
from zoneinfo import ZoneInfo

SAST = ZoneInfo("Africa/Johannesburg")


def now_sast() -> dt.datetime:
    return dt.datetime.now(SAST)


def service_date(now: dt.datetime | None = None) -> dt.date:
    # The queue day rolls over at midnight in South Africa, not at midnight UTC.
    return (now or now_sast()).astimezone(SAST).date()
