import { test } from "node:test";
import assert from "node:assert/strict";
import { ApiError, buildUrl, createApiClient } from "./client";

const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...headers } });

function setup(responses: (Response | Error)[]) {
  const calls: { url: string; init: RequestInit }[] = [];
  const tokenRequests: boolean[] = [];
  const queue = [...responses];
  const client = createApiClient({
    baseUrl: "https://api.test/",
    getToken: async (force) => {
      tokenRequests.push(force);
      return force ? "fresh-token" : "old-token";
    },
    fetchImpl: (async (url: string, init: RequestInit) => {
      calls.push({ url: String(url), init });
      const next = queue.shift();
      if (!next) throw new Error("no more responses queued");
      if (next instanceof Error) throw next;
      return next;
    }) as typeof fetch,
  });
  return { client, calls, tokenRequests };
}

test("buildUrl joins base and path, and skips empty query values", () => {
  const url = buildUrl("https://api.test/", "/v1/admin/staff", { limit: 25, role: undefined, cursor: "", include_inactive: true });
  assert.equal(url, "https://api.test/v1/admin/staff?limit=25&include_inactive=true");
});

test("sends the token as a Bearer header", async () => {
  const { client, calls } = setup([json({ ok: true })]);
  await client.json("/v1/me");
  const headers = calls[0].init.headers as Record<string, string>;
  assert.equal(headers.Authorization, "Bearer old-token");
});

test("on a 401 it refreshes the token and retries once", async () => {
  const { client, calls, tokenRequests } = setup([
    json({ error: { code: "UNAUTHENTICATED", message: "x", retryable: false, request_id: "r1" } }, 401),
    json({ uid: "u1" }),
  ]);
  const me = await client.json<{ uid: string }>("/v1/me");
  assert.equal(me.uid, "u1");
  assert.equal(calls.length, 2);
  assert.deepEqual(tokenRequests, [false, true]);
  assert.equal((calls[1].init.headers as Record<string, string>).Authorization, "Bearer fresh-token");
});

test("a second 401 is not retried again and becomes an ApiError", async () => {
  const body = { error: { code: "UNAUTHENTICATED", message: "Sign in again.", retryable: false, request_id: "r" } };
  const { client, calls } = setup([json(body, 401), json(body, 401)]);
  await assert.rejects(client.json("/v1/me"), (e: unknown) => e instanceof ApiError && e.status === 401);
  assert.equal(calls.length, 2);
});

test("errors keep the contract's code, message and request id", async () => {
  const { client } = setup([
    json({ error: { code: "EMAIL_IN_USE", message: "That email already has an account.", retryable: false, request_id: "req_9" } }, 400),
  ]);
  await assert.rejects(client.json("/v1/admin/staff", { method: "POST", body: {} }), (e: unknown) => {
    assert.ok(e instanceof ApiError);
    assert.equal(e.code, "EMAIL_IN_USE");
    assert.equal(e.message, "That email already has an account.");
    assert.equal(e.requestId, "req_9");
    return true;
  });
});

test("a 403 is never retried", async () => {
  const { client, calls } = setup([
    json({ error: { code: "FORBIDDEN", message: "You can't do that.", retryable: false, request_id: "r" } }, 403),
  ]);
  await assert.rejects(client.json("/v1/admin/staff"));
  assert.equal(calls.length, 1);
});

test("a 429 exposes Retry-After", async () => {
  const { client } = setup([json({}, 429, { "Retry-After": "30" })]);
  await assert.rejects(client.json("/v1/x"), (e: unknown) => e instanceof ApiError && e.retryAfterSeconds === 30);
});

test("a failed fetch becomes a friendly NETWORK error", async () => {
  const { client } = setup([new TypeError("fetch failed")]);
  await assert.rejects(client.json("/v1/me"), (e: unknown) => e instanceof ApiError && e.code === "NETWORK");
});

test("a 204 returns undefined", async () => {
  const { client } = setup([new Response(null, { status: 204 })]);
  assert.equal(await client.json("/v1/x", { method: "DELETE" }), undefined);
});

test("a missing base URL gives a CONFIG error instead of a crash", async () => {
  const client = createApiClient({ baseUrl: "", getToken: async () => null });
  await assert.rejects(client.json("/v1/me"), (e: unknown) => e instanceof ApiError && e.code === "CONFIG");
});

test("download returns the blob and the filename", async () => {
  const { client } = setup([
    new Response("date,joined\n", { status: 200, headers: { "Content-Disposition": 'attachment; filename="analytics.csv"' } }),
  ]);
  const file = await client.download("/v1/admin/clinics/c1/analytics");
  assert.equal(file.filename, "analytics.csv");
  assert.equal(await file.blob.text(), "date,joined\n");
});
