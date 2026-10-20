import type { OpeningHoursEntry, Weekday } from "@/lib/api/types";

export const WEEKDAYS: { key: Weekday; label: string; short: string }[] = [
  { key: "mon", label: "Monday", short: "Mon" },
  { key: "tue", label: "Tuesday", short: "Tue" },
  { key: "wed", label: "Wednesday", short: "Wed" },
  { key: "thu", label: "Thursday", short: "Thu" },
  { key: "fri", label: "Friday", short: "Fri" },
  { key: "sat", label: "Saturday", short: "Sat" },
  { key: "sun", label: "Sunday", short: "Sun" },
];

export interface HoursRow {
  day: Weekday;
  open: boolean;
  opens: string;
  closes: string;
}

/** Form rows for the opening hours editor. A new clinic starts Monday to Friday, 07:00 to 16:00. */
export function toRows(entries?: OpeningHoursEntry[]): HoursRow[] {
  return WEEKDAYS.map(({ key }) => {
    const found = entries?.find((e) => e.day === key);
    if (found) return { day: key, open: true, opens: found.opens, closes: found.closes };
    const weekday = !entries && key !== "sat" && key !== "sun";
    return { day: key, open: weekday, opens: "07:00", closes: "16:00" };
  });
}

/** Only open days are sent to the API. */
export function toEntries(rows: HoursRow[]): OpeningHoursEntry[] {
  return rows.filter((r) => r.open).map((r) => ({ day: r.day, opens: r.opens, closes: r.closes }));
}

/** "Mon to Fri 07:00 to 16:00; Sat 08:00 to 12:00" */
export function summariseHours(entries?: OpeningHoursEntry[]): string {
  if (!entries || entries.length === 0) return "No opening hours set";
  const byDay = new Map(entries.map((e) => [e.day, e]));
  const parts: string[] = [];
  let i = 0;
  while (i < WEEKDAYS.length) {
    const first = byDay.get(WEEKDAYS[i].key);
    if (!first) {
      i += 1;
      continue;
    }
    let j = i;
    while (j + 1 < WEEKDAYS.length) {
      const next = byDay.get(WEEKDAYS[j + 1].key);
      if (next && next.opens === first.opens && next.closes === first.closes) j += 1;
      else break;
    }
    const days = i === j ? WEEKDAYS[i].short : `${WEEKDAYS[i].short} to ${WEEKDAYS[j].short}`;
    parts.push(`${days} ${first.opens} to ${first.closes}`);
    i = j + 1;
  }
  return parts.join("; ");
}
