import { afterEach, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { installFakeApi, type FakeApi } from "@/test/fakeApi";
import { clearSession, getSessionToken, login, refreshSession, setSession } from "./session";

const user = { id: "u1", email: "admin@swiftq.co.za", role: "it_admin" as const, full_name: "Thandi Admin", clinic_id: null };
const loginBody = { access_token: "jwt-123", token_type: "Bearer", expires_in: 604800, user };

let api: FakeApi;
beforeEach(() => {
  api = installFakeApi();
  clearSession();
});
afterEach(() => api.restore());

test("login sends the email and password and returns the token and the person", async () => {
  api.on(({ url, method }) => (method === "POST" && url.pathname === "/v1/auth/login" ? { body: loginBody } : undefined));
  const result = await login("admin@swiftq.co.za", "secret");
  assert.equal(result.access_token, "jwt-123");
  assert.equal(result.user.role, "it_admin");
  assert.deepEqual(api.calls[0].body, { email: "admin@swiftq.co.za", password: "secret" });
});

test("login explains a wrong password and a deactivated account in plain language", async () => {
  api.on(({ url }) => (url.pathname === "/v1/auth/login" ? { status: 401, body: {} } : undefined));
  await assert.rejects(() => login("a@b.co", "x"), /email or password isn't right/);
  api.on(({ url }) => (url.pathname === "/v1/auth/login" ? { status: 403, body: {} } : undefined));
  await assert.rejects(() => login("a@b.co", "x"), /deactivated/);
});

test("refresh sends the saved token, and returns null when it is no longer accepted", async () => {
  assert.equal(await refreshSession(), null); // nothing saved, so nothing is sent
  assert.equal(api.calls.length, 0);
  setSession("old-token", user);
  api.on(({ url }) => (url.pathname === "/v1/auth/refresh" ? { body: { ...loginBody, access_token: "new-token" } } : undefined));
  assert.equal((await refreshSession())?.access_token, "new-token");
  api.on(({ url }) => (url.pathname === "/v1/auth/refresh" ? { status: 401, body: {} } : undefined));
  assert.equal(await refreshSession(), null);
  assert.equal(getSessionToken(), "old-token"); // clearing is the caller's job
});
