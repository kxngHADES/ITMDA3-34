import { afterEach, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ToastProvider } from "@/components/ui/Toast";
import { installFakeApi, page, type FakeApi } from "@/test/fakeApi";
import { AnalyticsScreen } from "./AnalyticsScreen";

const snapshot = (date: string, joined: number, served: number, avg: number) => ({
  clinic_id: "c1", date, tickets_joined: joined, tickets_served: served, no_shows: 1, cancellations: 0,
  avg_wait_minutes: avg, p90_wait_minutes: avg * 2, peak_hour: 9, joins_by_channel: { app: joined },
  calls_per_staff: [{ staff_id: "staff-0001-abc", calls: served }],
});

let api: FakeApi;
beforeEach(() => {
  api = installFakeApi();
  api.on(({ method, url }) => (method === "GET" && url.pathname === "/v1/clinics" ? { body: page([{ id: "c1", name: "Soshanguve Block B Clinic", is_open: true }]) } : undefined));
  api.on(({ method, url }) =>
    method === "GET" && url.pathname === "/v1/admin/clinics/c1/analytics"
      ? { body: { items: [snapshot("2026-10-12", 40, 30, 10), snapshot("2026-10-13", 20, 10, 40)] } }
      : undefined,
  );
});
afterEach(() => { cleanup(); api.restore(); });

const setup = () => render(<ToastProvider><AnalyticsScreen /></ToastProvider>);

test("opens with the first clinic and shows totals worked out from the days", async () => {
  setup();
  await screen.findByText("Patients joined");
  // Each total sits next to its label, so look it up through the label.
  const stat = (label: string) => within(screen.getByText(label).parentElement as HTMLElement);
  assert.ok(stat("Patients joined").getByText("60")); // 40 + 20
  assert.ok(stat("Patients served").getByText("40")); // 30 + 10
  assert.ok(stat("Average wait").getByText("17.5 min")); // weighted by served: (10*30 + 40*10) / 40
  const call = api.calls.find((c) => c.path === "/v1/admin/clinics/c1/analytics");
  assert.equal(call?.query.format, "json");
});

test("a range over 92 days is refused with an explanation, and nothing is requested", async () => {
  const user = userEvent.setup();
  setup();
  await screen.findByText("Patients joined");
  const before = api.calls.filter((c) => c.path.endsWith("/analytics")).length;
  const from = screen.getByLabelText("From");
  await user.clear(from);
  await user.type(from, "2026-01-01");
  const to = screen.getByLabelText("To");
  await user.clear(to);
  await user.type(to, "2026-10-01");
  await user.click(screen.getByRole("button", { name: "Show results" }));
  assert.ok(await screen.findByText("Choose a range of 92 days or less."));
  assert.equal(api.calls.filter((c) => c.path.endsWith("/analytics")).length, before);
});

test("a day range with no data shows an empty state, not a broken chart", async () => {
  const user = userEvent.setup();
  api.on(({ method, url }) => (method === "GET" && url.pathname === "/v1/admin/clinics/c1/analytics" ? { body: { items: [] } } : undefined));
  setup();
  assert.ok(await screen.findByText("No data for these dates"));
  await user.click(screen.getByRole("button", { name: "Export CSV" }));
  await waitFor(() => assert.ok(api.calls.some((c) => c.query.format === "csv")));
});
