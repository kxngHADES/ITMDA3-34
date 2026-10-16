import type { AnalyticsSnapshot } from "@/lib/api/types";

export interface AnalyticsSummary {
  joined: number;
  served: number;
  noShows: number;
  cancellations: number;
  /** Average wait across the range, weighted by how many tickets were served each day. */
  avgWaitMinutes: number | null;
  worstP90Minutes: number | null;
  /** peakHourDays[h] = number of days where hour h was the busiest hour. */
  peakHourDays: number[];
  channels: { channel: string; joins: number }[];
  callsByStaff: { staffId: string; calls: number }[];
}

const sum = (xs: (number | undefined)[]): number => xs.reduce<number>((t, x) => t + (x ?? 0), 0);

export function summarise(items: AnalyticsSnapshot[]): AnalyticsSummary {
  const peakHourDays = Array.from({ length: 24 }, () => 0);
  for (const s of items) {
    if (typeof s.peak_hour === "number" && s.peak_hour >= 0 && s.peak_hour <= 23) peakHourDays[s.peak_hour] += 1;
  }

  let weighted = 0;
  let weights = 0;
  let plain = 0;
  let plainDays = 0;
  for (const s of items) {
    if (typeof s.avg_wait_minutes !== "number") continue;
    plain += s.avg_wait_minutes;
    plainDays += 1;
    const served = s.tickets_served ?? 0;
    if (served > 0) {
      weighted += s.avg_wait_minutes * served;
      weights += served;
    }
  }
  const avgWaitMinutes = weights > 0 ? weighted / weights : plainDays > 0 ? plain / plainDays : null;

  const p90s = items.map((s) => s.p90_wait_minutes).filter((n): n is number => typeof n === "number");

  const channelTotals = new Map<string, number>();
  const staffTotals = new Map<string, number>();
  for (const s of items) {
    for (const [channel, n] of Object.entries(s.joins_by_channel ?? {})) {
      channelTotals.set(channel, (channelTotals.get(channel) ?? 0) + n);
    }
    for (const row of s.calls_per_staff ?? []) {
      const key = row.staff_id ?? "unknown";
      staffTotals.set(key, (staffTotals.get(key) ?? 0) + (row.calls ?? 0));
    }
  }

  return {
    joined: sum(items.map((s) => s.tickets_joined)),
    served: sum(items.map((s) => s.tickets_served)),
    noShows: sum(items.map((s) => s.no_shows)),
    cancellations: sum(items.map((s) => s.cancellations)),
    avgWaitMinutes,
    worstP90Minutes: p90s.length ? Math.max(...p90s) : null,
    peakHourDays,
    channels: [...channelTotals].map(([channel, joins]) => ({ channel, joins })).sort((a, b) => b.joins - a.joins),
    callsByStaff: [...staffTotals].map(([staffId, calls]) => ({ staffId, calls })).sort((a, b) => b.calls - a.calls),
  };
}
