import { afterEach, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { installFakeApi, page, type FakeApi } from "@/test/fakeApi";
import { WithAuth, signInAs } from "@/test/auth";
import { AuditScreen } from "./AuditScreen";

const clinics = [
  { id: "c1", name: "Soshanguve Block B Clinic", is_open: true, is_active: true },
  { id: "c2", name: "Mamelodi Clinic", is_open: false, is_active: true },
];
const thandi = { id: "s1", email: "thandi@clinic.co.za", display_name: "Thandi Nkosi", role: "nurse", clinic_id: "c1", is_active: true, created_at: "2026-10-01T08:00:00Z" };
const entries = [
  { id: "a1", actor_id: "s1", actor_role: "nurse", action: "ticket.call_next", target_type: "ticket", target_id: "t-123", outcome: "success", created_at: "2026-10-12T08:00:00Z" },
  { id: "a2", actor_id: "s9", actor_role: "manager", action: "staff.create", target_type: "staff", target_id: null, outcome: "denied", created_at: "2026-10-12T07:00:00Z" },
];

let api: FakeApi;
beforeEach(() => {
  api = installFakeApi();
  signInAs(api, "it_admin");
  api.on(({ url, method }) => (method === "GET" && url.pathname === "/v1/clinics" ? { body: page(clinics) } : undefined));
  api.on(({ url, method }) => (method === "GET" && url.pathname === "/v1/admin/staff" ? { body: page([thandi]) } : undefined));
  api.on(({ url, method }) => (method === "GET" && url.pathname === "/v1/admin/audit-log" ? { body: page(entries) } : undefined));
});
afterEach(() => {
  cleanup();
  api.restore();
});

const setup = () => render(<WithAuth><AuditScreen /></WithAuth>);
const auditCalls = () => api.calls.filter((c) => c.path === "/v1/admin/audit-log");

test("lists actions with staff names, plain action text and outcomes", async () => {
  setup();
  const row = (await screen.findByText("Ticket: call next")).closest("tr") as HTMLElement;
  await waitFor(() => assert.ok(within(row).getByText("Thandi Nkosi")));
  assert.ok(within(row).getByText("Done"));
  const denied = screen.getByText("Staff: create").closest("tr") as HTMLElement;
  assert.ok(within(denied).getByText("Not allowed"));
  assert.ok(within(denied).getByText("s9"));
});

test("filters are sent to the API", async () => {
  const user = userEvent.setup();
  setup();
  await screen.findByText("Ticket: call next");
  await user.selectOptions(screen.getByLabelText("Outcome"), "denied");
  await user.selectOptions(screen.getByLabelText("Clinic"), "c2");
  await waitFor(() => {
    const q = auditCalls().at(-1)!.query;
    assert.equal(q.outcome, "denied");
    assert.equal(q.clinic_id, "c2");
  });
});

test("a backwards date range is explained and not sent", async () => {
  const user = userEvent.setup();
  setup();
  await screen.findByText("Ticket: call next");
  await user.type(screen.getByLabelText("From"), "2026-10-12");
  await user.type(screen.getByLabelText("To"), "2026-10-01");
  assert.ok(await screen.findByText("The end date must be on or after the start date."));
  assert.ok(auditCalls().every((c) => !c.query.to || c.query.to >= (c.query.from ?? "")));
});

test("shows an empty state when nothing matches", async () => {
  api.on(({ url }) => (url.pathname === "/v1/admin/audit-log" ? { body: page([]) } : undefined));
  setup();
  assert.ok(await screen.findByText("No actions match"));
});
