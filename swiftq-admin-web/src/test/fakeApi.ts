// A tiny fake API for component tests: you describe the answers, it records the calls.
type Handler = (req: { url: URL; method: string; body: unknown }) => { status?: number; body?: unknown } | undefined;

export interface FakeApi {
  calls: { method: string; path: string; query: Record<string, string>; body: unknown }[];
  on: (handler: Handler) => void;
  restore: () => void;
}

export function installFakeApi(): FakeApi {
  const original = globalThis.fetch;
  const handlers: Handler[] = [];
  const calls: FakeApi["calls"] = [];

  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input));
    const method = init?.method ?? "GET";
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ method, path: url.pathname, query: Object.fromEntries(url.searchParams), body });
    for (const handler of [...handlers].reverse()) {
      const answer = handler({ url, method, body });
      if (answer) {
        const status = answer.status ?? 200;
        if (status === 204) return new Response(null, { status });
        return new Response(JSON.stringify(answer.body ?? {}), { status, headers: { "Content-Type": "application/json" } });
      }
    }
    return new Response(JSON.stringify({ error: { code: "NOT_FOUND", message: `No fake answer for ${method} ${url.pathname}`, retryable: false, request_id: "t" } }), { status: 404 });
  }) as typeof fetch;

  return { calls, on: (h) => handlers.push(h), restore: () => { globalThis.fetch = original; } };
}

export const page = <T,>(items: T[]) => ({ items, next_cursor: null, has_more: false });
