const ZONE = "Africa/Johannesburg";

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "Never";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "Unknown";
  return new Intl.DateTimeFormat("en-ZA", { dateStyle: "medium", timeStyle: "short", timeZone: ZONE }).format(d);
}

/** "2026-10-12" to "12 Oct". Built by hand so the day never shifts with the browser's time zone. */
export function formatShortDate(ymd: string): string {
  const [, m, d] = ymd.split("-").map(Number);
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  if (!m || !d) return ymd;
  return `${d} ${months[m - 1]}`;
}

/** Local calendar date as YYYY-MM-DD. */
export function toYmd(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function daysAgoYmd(days: number, from = new Date()): string {
  const d = new Date(from);
  d.setDate(d.getDate() - days);
  return toYmd(d);
}

/** Opaque ids are long, so show the start of them. The full id stays in the title attribute. */
export function shortId(id: string | null | undefined): string {
  if (!id) return "None";
  return id.length > 10 ? `${id.slice(0, 8)}...` : id;
}

/** "ticket.call_next" to "Ticket: call next" */
export function humaniseAction(action: string): string {
  const [group, ...rest] = action.split(".");
  const text = rest.join(" ").replace(/_/g, " ");
  const head = group.charAt(0).toUpperCase() + group.slice(1);
  return text ? `${head}: ${text}` : head;
}

export function formatMinutes(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return "No data";
  return `${Math.round(n * 10) / 10} min`;
}
