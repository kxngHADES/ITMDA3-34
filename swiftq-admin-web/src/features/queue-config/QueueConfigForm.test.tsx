import { afterEach, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ToastProvider } from "@/components/ui/Toast";
import { installFakeApi, type FakeApi } from "@/test/fakeApi";
import { DEFAULT_QUEUE_CONFIG } from "@/lib/validation";
import { QueueConfigForm } from "./QueueConfigForm";

let api: FakeApi;
beforeEach(() => { api = installFakeApi(); });
afterEach(() => { cleanup(); api.restore(); });

const setup = () => render(<ToastProvider><QueueConfigForm servicePointId="q1" initial={DEFAULT_QUEUE_CONFIG} onSaved={() => undefined} /></ToastProvider>);

test("Save stays off until something changes", async () => {
  const user = userEvent.setup();
  setup();
  const save = screen.getByRole("button", { name: "Save settings" }) as HTMLButtonElement;
  assert.equal(save.disabled, true);
  await user.clear(screen.getByLabelText("Late return penalty (places)"));
  await user.type(screen.getByLabelText("Late return penalty (places)"), "7");
  assert.equal(save.disabled, false);
});

test("values below the contract's minimum are explained, and nothing is sent", async () => {
  const user = userEvent.setup();
  setup();
  const field = screen.getByLabelText("Protect waiting patients after (minutes)");
  await user.clear(field);
  await user.type(field, "0");
  await user.tab();
  assert.ok(await screen.findByText("Enter a whole number of minutes, 1 or more."));
  await user.click(screen.getByRole("button", { name: "Save settings" }));
  assert.equal(api.calls.filter((c) => c.method === "PUT").length, 0);
});

test("saving sends the whole configuration with PUT", async () => {
  const user = userEvent.setup();
  api.on(({ method, body }) => (method === "PUT" ? { body } : undefined));
  setup();
  const field = screen.getByLabelText("Late return penalty (places)");
  await user.clear(field);
  await user.type(field, "7");
  await user.click(screen.getByRole("button", { name: "Save settings" }));
  await waitFor(() => assert.equal(api.calls.filter((c) => c.method === "PUT").length, 1));
  const put = api.calls.find((c) => c.method === "PUT");
  assert.equal(put?.path, "/v1/admin/service-points/q1/queue-config");
  assert.deepEqual(put?.body, { ...DEFAULT_QUEUE_CONFIG, late_requeue_penalty_p: 7 });
  assert.ok(await screen.findByText(/Queue settings saved/));
});

test("Reset to defaults puts the contract's defaults back", async () => {
  const user = userEvent.setup();
  setup();
  const field = screen.getByLabelText("Elderly weight") as HTMLInputElement;
  await user.clear(field);
  await user.type(field, "9");
  await user.click(screen.getByRole("button", { name: "Reset to defaults" }));
  assert.equal((screen.getByLabelText("Elderly weight") as HTMLInputElement).value, "3");
});
