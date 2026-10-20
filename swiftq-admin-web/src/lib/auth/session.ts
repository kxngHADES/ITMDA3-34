// Holds the JWT and the signed in person's details that the shared API sends back at login.
// They live in memory and in sessionStorage, so a page refresh keeps you signed in
// but closing the tab signs you out.

import { buildUrl } from "@/lib/api/client";
import type { LoginResponse, LoginUser, Me } from "@/lib/api/types";

const BASE = () => process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
const TOKEN_KEY = "swiftq_token";
const USER_KEY = "swiftq_user";

let memoryToken: string | null = null;
let memoryUser: LoginUser | null = null;

function read(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}
function write(key: string, value: string | null): void {
  if (typeof window === "undefined") return;
  try {
    if (value) window.sessionStorage.setItem(key, value);
    else window.sessionStorage.removeItem(key);
  } catch {
    // Storage can be blocked. The values still work from memory.
  }
}

export function getSessionToken(): string | null {
  if (!memoryToken) memoryToken = read(TOKEN_KEY);
  return memoryToken;
}

export function getSessionUser(): LoginUser | null {
  if (!memoryUser) {
    const raw = read(USER_KEY);
    if (raw) {
      try {
        memoryUser = JSON.parse(raw) as LoginUser;
      } catch {
        memoryUser = null;
      }
    }
  }
  return memoryUser;
}

export function setSession(token: string | null, user: LoginUser | null): void {
  memoryToken = token;
  memoryUser = user;
  write(TOKEN_KEY, token);
  write(USER_KEY, user ? JSON.stringify(user) : null);
}

export function clearSession(): void {
  setSession(null, null);
}

export function userToMe(user: LoginUser): Me {
  return { uid: user.id, role: user.role, clinic_id: user.clinic_id, display_name: user.full_name, email: user.email };
}

async function post(path: string, body: unknown, token?: string): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;
  try {
    return await fetch(buildUrl(BASE(), path), {
      method: "POST",
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error("We couldn't reach the server. It may be starting up. Try again in a moment.");
  }
}

async function readLogin(res: Response): Promise<LoginResponse> {
  const body = (await res.json().catch(() => null)) as LoginResponse | null;
  if (!body || typeof body.access_token !== "string" || !body.user) {
    throw new Error("The server answered, but did not send what we expected. Tell your team.");
  }
  return body;
}

/** POST /v1/auth/login. Returns the token and the person. Throws a plain language Error. */
export async function login(email: string, password: string): Promise<LoginResponse> {
  const res = await post("/v1/auth/login", { email, password });
  if (res.status === 401) throw new Error("That email or password isn't right. Check them and try again.");
  if (res.status === 403) throw new Error("This account has been deactivated. Ask your IT admin for help.");
  if (res.status === 429) throw new Error("Too many attempts. Wait a few minutes, then try again.");
  if (res.status === 422) throw new Error("Enter a valid email address and your password.");
  if (!res.ok) throw new Error("We couldn't sign you in. Try again in a moment.");
  return readLogin(res);
}

/**
 * POST /v1/auth/refresh with the current token. Role, clinic and active status are read again
 * by the server, so this also checks that a saved session is still good.
 * Returns null if the token is no longer accepted. Throws only when the server can't be reached.
 */
export async function refreshSession(): Promise<LoginResponse | null> {
  const token = getSessionToken();
  if (!token) return null;
  const res = await post("/v1/auth/refresh", undefined, token);
  if (res.status === 401 || res.status === 403) return null;
  if (!res.ok) throw new Error("We couldn't reach the server. Try again in a moment.");
  return readLogin(res);
}
