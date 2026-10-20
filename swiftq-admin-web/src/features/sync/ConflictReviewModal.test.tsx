import { afterEach, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { installFakeApi, type FakeApi } from "@/test/fakeApi";
import type { SyncLogEntry } from "@/lib/api/types";
import { ConflictReviewModal } from "./ConflictReviewModal";

const entry: SyncLogEntry = {
  id: "e1", device_uid: "device-abcdef123456", entity_type: "ticket", entity_id: "ticket-0001-xyz", op: "update_ticket",
  status: "conflict_pending", created_at: "2026-10-12T08:00:00Z",
  conflict: {
    client_value: { language: "zu", display_name: "Sipho" },
    server_value: { language: "en", display_name: "Sipho" },
    client_updated_at: "2026-10-12T07:59:00Z",
    server_updated_at: "2026-10-12T07:58:00Z",
  },
};

let api: FakeApi;
beforeEach(() => { api = installFakeApi(); });
afterEach(() => { cleanup(); api.restore(); });

test("shows the two copies, marks the field that differs, and needs a choice before resolving", async () => {
  const user = userEvent.setup();
  render(<ConflictReviewModal entry={entry} onClose={() => undefined} onResolved={() => undefined} onStale={() => undefined} />);
  const differing = screen.getByText("language").closest("tr") as HTMLElement;
  assert.ok(differing.className.includes("differs"));
  assert.ok(!(screen.getByText("display_name").closest("tr") as HTMLElement).className.includes("differs"));
  assert.ok(screen.getByText(/The newer change is the device's/));

  await user.click(screen.getByRole("button", { name: "Resolve conflict" }));
  assert.ok(await screen.findByText("Choose what to do with this conflict."));
  assert.equal(api.calls.length, 0);
});

test("resolving sends the choice and the note", async () => {
  const user = userEvent.setup();
  let resolved = "";
  api.on(({ method, url, body }) => (method === "POST" && url.pathname === "/v1/admin/sync-log/e1/resolve" ? { body: { ...entry, status: "resolved", ...(body as object) } } : undefined));
  render(<ConflictReviewModal entry={entry} onClose={() => undefined} onResolved={(m) => { resolved = m; }} onStale={() => undefined} />);
  await user.click(within(screen.getByRole("dialog")).getByLabelText(/Use the device's change/));
  await user.type(screen.getByLabelText(/Note/), "Patient confirmed Zulu on the phone");
  await user.click(screen.getByRole("button", { name: "Resolve conflict" }));
  await waitFor(() => assert.equal(resolved, "Conflict resolved."));
  assert.deepEqual(api.calls[0].body, { resolution: "apply_client", note: "Patient confirmed Zulu on the phone" });
});

test("if someone else already resolved it, the message is shown and the list is told to refresh", async () => {
  const user = userEvent.setup();
  let stale = false;
  api.on(({ method }) =>
    method === "POST" ? { status: 400, body: { error: { code: "CONFLICT_ALREADY_RESOLVED", message: "Someone else already resolved this conflict.", retryable: false, request_id: "r" } } } : undefined,
  );
  render(<ConflictReviewModal entry={entry} onClose={() => undefined} onResolved={() => undefined} onStale={() => { stale = true; }} />);
  await user.click(screen.getByLabelText(/Keep the server copy/));
  await user.click(screen.getByRole("button", { name: "Resolve conflict" }));
  assert.ok(await screen.findByText("Someone else already resolved this conflict."));
  assert.equal(stale, true);
});
