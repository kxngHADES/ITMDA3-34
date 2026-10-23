import { afterEach, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";

// The page has its own Role and Clinic filters, so form fields are looked up inside the dialog.
const dialog = () => within(screen.getByRole("dialog"));
import userEvent from "@testing-library/user-event";
import { ToastProvider } from "@/components/ui/Toast";
import { installFakeApi, page, type FakeApi } from "@/test/fakeApi";
import { WithAuth, signInAs } from "@/test/auth";
import { StaffScreen } from "./StaffScreen";

const clinics = [
  { id: "c1", name: "Soshanguve Block B Clinic", is_open: true, is_active: true },
  { id: "c2", name: "Mamelodi Clinic", is_open: false, is_active: true },
];
const thandi = { id: "s1", email: "thandi@clinic.co.za", display_name: "Thandi Nkosi", role: "nurse", clinic_id: "c1", is_active: true, created_at: "2026-10-01T08:00:00Z", last_sign_in_at: null };

let api: FakeApi;
beforeEach(() => {
  api = installFakeApi();
  signInAs(api, "it_admin");
  api.on(({ url, method }) => (method === "GET" && url.pathname === "/v1/clinics" ? { body: page(clinics) } : undefined));
  api.on(({ url, method }) => (method === "GET" && url.pathname === "/v1/admin/staff" ? { body: page([thandi]) } : undefined));
});
afterEach(() => {
  cleanup();
  api.restore();
});

const setup = () => render(<ToastProvider><WithAuth><StaffScreen /></WithAuth></ToastProvider>);

test("lists staff with the clinic name instead of the id", async () => {
  setup();
  const row = (await screen.findByText("Thandi Nkosi")).closest("tr") as HTMLElement;
  assert.ok(within(row).getByText("Nurse"));
  assert.ok(within(row).getByText("Soshanguve Block B Clinic"));
  assert.ok(within(row).getByText("Active"));
});

test("the add form explains what is missing, then sends the right request", async () => {
  const user = userEvent.setup();
  api.on(({ url, method, body }) =>
    method === "POST" && url.pathname === "/v1/admin/staff" ? { status: 201, body: { ...thandi, id: "s2", ...(body as object) } } : undefined,
  );
  setup();
  await screen.findByText("Thandi Nkosi");
  await user.click(screen.getByRole("button", { name: "Add staff member" }));

  // Submitting an empty form shows every problem in plain language and sends nothing.
  await user.click(screen.getAllByRole("button", { name: "Add staff member" }).at(-1) as HTMLElement);
  assert.ok(await screen.findByText("Enter the person's name."));
  assert.ok(screen.getByText("Enter an email address."));
  assert.ok(screen.getByText("Choose a role."));
  assert.ok(screen.getByText("Choose a clinic."));
  assert.equal(api.calls.filter((c) => c.method === "POST" && c.path === "/v1/admin/staff").length, 0);

  await user.type(dialog().getByLabelText("Full name"), "  Sipho Dlamini ");
  await user.type(dialog().getByLabelText("Email address"), "sipho@clinic.co.za");
  await user.selectOptions(dialog().getByLabelText("Role"), "receptionist");
  await user.selectOptions(dialog().getByLabelText("Clinic"), "c2");
  await user.click(screen.getAllByRole("button", { name: "Add staff member" }).at(-1) as HTMLElement);

  await waitFor(() => assert.equal(api.calls.filter((c) => c.method === "POST" && c.path === "/v1/admin/staff").length, 1));
  const post = api.calls.find((c) => c.method === "POST" && c.path === "/v1/admin/staff");
  assert.deepEqual(post?.body, { email: "sipho@clinic.co.za", display_name: "Sipho Dlamini", role: "receptionist", clinic_id: "c2" });
  assert.ok(await screen.findByText(/Sipho Dlamini was added/));
});

test("an API error such as EMAIL_IN_USE is shown inside the form", async () => {
  const user = userEvent.setup();
  api.on(({ url, method }) =>
    method === "POST" && url.pathname === "/v1/admin/staff"
      ? { status: 400, body: { error: { code: "EMAIL_IN_USE", message: "A staff account already uses this email address.", retryable: false, request_id: "r" } } }
      : undefined,
  );
  setup();
  await screen.findByText("Thandi Nkosi");
  await user.click(screen.getByRole("button", { name: "Add staff member" }));
  await user.type(dialog().getByLabelText("Full name"), "Sipho");
  await user.type(dialog().getByLabelText("Email address"), "thandi@clinic.co.za");
  await user.selectOptions(dialog().getByLabelText("Role"), "nurse");
  await user.selectOptions(dialog().getByLabelText("Clinic"), "c1");
  await user.click(screen.getAllByRole("button", { name: "Add staff member" }).at(-1) as HTMLElement);
  const alert = await screen.findByText("A staff account already uses this email address.");
  assert.equal(alert.getAttribute("role"), "alert");
});

test("deactivating asks first, then calls DELETE", async () => {
  const user = userEvent.setup();
  api.on(({ url, method }) =>
    method === "DELETE" && url.pathname === "/v1/admin/staff/s1" ? { status: 204 } : undefined,
  );
  setup();
  await user.click(await screen.findByRole("button", { name: "Deactivate Thandi Nkosi" }));
  assert.equal(api.calls.filter((c) => c.method === "DELETE").length, 0); // nothing yet
  assert.ok(screen.getByText(/They will no longer be able to sign in/));
  await user.click(screen.getByRole("button", { name: "Deactivate account" }));
  await waitFor(() => assert.equal(api.calls.filter((c) => c.method === "DELETE").length, 1));
  assert.ok(await screen.findByText(/Thandi Nkosi was deactivated/));
});

test("editing sends only the fields that changed", async () => {
  const user = userEvent.setup();
  api.on(({ url, method, body }) =>
    method === "PATCH" && url.pathname === "/v1/admin/staff/s1" ? { body: { ...thandi, ...(body as object) } } : undefined,
  );
  setup();
  await user.click(await screen.findByRole("button", { name: "Edit Thandi Nkosi" }));
  await user.selectOptions(dialog().getByLabelText("Role"), "manager");
  assert.ok(screen.getByText(/signs this person out of every device/));
  await user.click(screen.getByRole("button", { name: "Save changes" }));
  await waitFor(() => assert.equal(api.calls.filter((c) => c.method === "PATCH").length, 1));
  assert.deepEqual(api.calls.find((c) => c.method === "PATCH")?.body, { role: "manager" });
});

test("a manager can only add receptionists and nurses, and cannot edit an IT admin", async () => {
  const user = userEvent.setup();
  api.restore();
  api = installFakeApi();
  signInAs(api, "manager", "c1");
  const boss = { ...thandi, id: "s9", display_name: "Big Boss", email: "boss@clinic.co.za", role: "it_admin" };
  api.on(({ url, method }) => (method === "GET" && url.pathname === "/v1/clinics" ? { body: page(clinics) } : undefined));
  api.on(({ url, method }) => (method === "GET" && url.pathname === "/v1/admin/staff" ? { body: page([thandi, boss]) } : undefined));
  setup();
  await screen.findByText("Thandi Nkosi");
  assert.ok(screen.getByRole("button", { name: "Edit Thandi Nkosi" }));
  assert.equal(screen.queryByRole("button", { name: "Edit Big Boss" }), null);
  // A manager only ever sees their own clinic in the list.
  assert.equal(screen.queryByText("Mamelodi Clinic"), null);
  await user.click(screen.getByRole("button", { name: "Add staff member" }));
  const roles = within(dialog().getByLabelText("Role")).getAllByRole("option").map((o) => o.textContent);
  assert.deepEqual(roles, ["Choose a role", "Receptionist", "Nurse"]);
});
