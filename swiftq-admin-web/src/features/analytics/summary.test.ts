import { test } from "node:test";
import assert from "node:assert/strict";
import { summarise } from "./summary";

const day = (date: string, extra: object) => ({ clinic_id: "c1", date, ...extra });

test("totals, weighted average wait, worst p90, peak hours, channels and staff", () => {
  const s = summarise([
    day("2026-10-12", {
      tickets_joined: 40, tickets_served: 30, no_shows: 3, cancellations: 2,
      avg_wait_minutes: 10, p90_wait_minutes: 25, peak_hour: 9,
      joins_by_channel: { app: 30, staff_assisted: 10 },
      calls_per_staff: [{ staff_id: "s1", calls: 20 }, { staff_id: "s2", calls: 10 }],
    }),
    day("2026-10-13", {
      tickets_joined: 20, tickets_served: 10, no_shows: 1, cancellations: 0,
      avg_wait_minutes: 40, p90_wait_minutes: 60, peak_hour: 9,
      joins_by_channel: { app: 15, sms: 5 },
      calls_per_staff: [{ staff_id: "s1", calls: 10 }],
    }),
  ]);
  assert.equal(s.joined, 60);
  assert.equal(s.served, 40);
  assert.equal(s.noShows, 4);
  assert.equal(s.cancellations, 2);
  assert.equal(s.avgWaitMinutes, (10 * 30 + 40 * 10) / 40); // 17.5
  assert.equal(s.worstP90Minutes, 60);
  assert.equal(s.peakHourDays[9], 2);
  assert.deepEqual(s.channels[0], { channel: "app", joins: 45 });
  assert.deepEqual(s.callsByStaff[0], { staffId: "s1", calls: 30 });
});

test("empty range gives zeros and no averages", () => {
  const s = summarise([]);
  assert.equal(s.joined, 0);
  assert.equal(s.avgWaitMinutes, null);
  assert.equal(s.worstP90Minutes, null);
});
