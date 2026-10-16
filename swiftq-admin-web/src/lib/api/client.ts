// The one place that talks to the SwiftQ API.
//
// What it does for every request (API contract sections 2.1 and 2.3):
//  1. Sends the JWT from the shared API as "Authorization: Bearer <token>".
//  2. On a 401, asks for a fresh token (POST /auth/refresh) and retries ONCE, silently.
//  3. Turns every failure into an ApiError with the contract's error fields,
//     so screens can show error.message and branch on error.code.
//
// It has no Firebase import on purpose: the token getter is passed in. That keeps it
// easy to unit test and easy to lift into the shared web module (plan task S8).

import type { ErrorResponse } from "./types";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly retryable: boolean;
  readonly retryAfterSeconds?: number;
  readonly requestId?: string;

  constructor(init: {
    status: number;
    code: string;
    message: string;
    retryable?: boolean;
    retryAfterSeconds?: number;
    requestId?: string;
  }) {
    super(init.message);
    this.name = "ApiError";
    this.status = init.status;
    this.code = init.code;
    this.retryable = init.retryable ?? false;
    this.retryAfterSeconds = init.retryAfterSeconds;
    this.requestId = init.requestId;
  }
}

export type Query = Record<string, string | number | boolean | undefined | null>;
export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface RequestOptions {
  method?: HttpMethod;
  query?: Query;
  body?: unknown;
  signal?: AbortSignal;
}

export interface ApiClientOptions {
  baseUrl: string;
  /** Returns the current Firebase ID token, or null when nobody is signed in. */
  getToken: (forceRefresh: boolean) => Promise<string | null>;
  fetchImpl?: typeof fetch;
  /** Render's free tier can take up to a minute to wake up, so the default is generous. */
  timeoutMs?: number;
}

const DEFAULT_TIMEOUT_MS = 65_000;

export function buildUrl(baseUrl: string, path: string, query?: Query): string {
  const url = new URL(baseUrl.replace(/\/+$/, "") + path);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === "") continue;
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

function fallbackMessage(status: number): string {
  if (status === 401) return "Your session has ended. Sign in again.";
  if (status === 403) return "You don't have permission to do this.";
  if (status === 404) return "We couldn't find that.";
  if (status === 422) return "Some of the details aren't valid. Check them and try again.";
  if (status === 429) return "Too many requests. Wait a moment, then try again.";
  if (status >= 500) return "Something went wrong on our side. Try again in a moment.";
  return "Something went wrong. Try again.";
}

async function toApiError(res: Response): Promise<ApiError> {
  let body: unknown = null;
  try {
    body = await res.json();
  } catch {
    body = null;
  }
  const err = (body as Partial<ErrorResponse> | null)?.error;
  const retryHeader = Number(res.headers.get("Retry-After"));

  if (err && typeof err.message === "string") {
    return new ApiError({
      status: res.status,
      code: err.code ?? `HTTP_${res.status}`,
      message: err.message,
      retryable: Boolean(err.retryable),
      retryAfterSeconds:
        err.retry_after_seconds ?? (Number.isFinite(retryHeader) && retryHeader > 0 ? retryHeader : undefined),
      requestId: err.request_id,
    });
  }
  return new ApiError({
    status: res.status,
    code: `HTTP_${res.status}`,
    message: fallbackMessage(res.status),
    retryable: res.status >= 500,
    retryAfterSeconds: Number.isFinite(retryHeader) && retryHeader > 0 ? retryHeader : undefined,
  });
}

export function createApiClient(opts: ApiClientOptions) {
  const timeoutMs = opts.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const doFetch: typeof fetch = opts.fetchImpl ?? ((input, init) => fetch(input, init));

  async function send(path: string, o: RequestOptions, forceRefresh: boolean): Promise<Response> {
    if (!opts.baseUrl) {
      throw new ApiError({
        status: 0,
        code: "CONFIG",
        message: "The app isn't connected to the API yet. Ask the team to check the API address setting.",
      });
    }
    const token = await opts.getToken(forceRefresh);
    const headers: Record<string, string> = { Accept: "application/json, text/csv" };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (o.body !== undefined) headers["Content-Type"] = "application/json";

    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeoutMs);
    const onCallerAbort = () => controller.abort();
    o.signal?.addEventListener("abort", onCallerAbort, { once: true });

    try {
      return await doFetch(buildUrl(opts.baseUrl, path, o.query), {
        method: o.method ?? "GET",
        headers,
        body: o.body !== undefined ? JSON.stringify(o.body) : undefined,
        signal: controller.signal,
      });
    } catch (err) {
      if (timedOut) {
        throw new ApiError({
          status: 0,
          code: "TIMEOUT",
          message: "The server is taking a long time to answer. It may be starting up. Try again in a moment.",
          retryable: true,
        });
      }
      if (o.signal?.aborted) throw err; // the screen cancelled this request on purpose
      throw new ApiError({
        status: 0,
        code: "NETWORK",
        message: "We couldn't reach the server. Check your connection and try again.",
        retryable: true,
      });
    } finally {
      clearTimeout(timer);
      o.signal?.removeEventListener("abort", onCallerAbort);
    }
  }

  async function request(path: string, o: RequestOptions = {}): Promise<Response> {
    let res = await send(path, o, false);
    if (res.status === 401) res = await send(path, o, true); // refresh the token once, then retry
    if (!res.ok) throw await toApiError(res);
    return res;
  }

  async function json<T>(path: string, o: RequestOptions = {}): Promise<T> {
    const res = await request(path, o);
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
  }

  /** For file downloads such as the analytics CSV. A plain link can't carry the Bearer token. */
  async function download(path: string, o: RequestOptions = {}): Promise<{ blob: Blob; filename: string | null }> {
    const res = await request(path, o);
    const disposition = res.headers.get("Content-Disposition") ?? "";
    const match = /filename="?([^";]+)"?/i.exec(disposition);
    return { blob: await res.blob(), filename: match ? match[1] : null };
  }

  return { request, json, download };
}

export type ApiClient = ReturnType<typeof createApiClient>;
