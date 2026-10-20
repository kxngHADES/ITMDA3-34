import { createApiClient } from "./client";
import { clearSession, getSessionToken, getSessionUser, refreshSession, setSession } from "@/lib/auth/session";

type TokenProvider = (forceRefresh: boolean) => Promise<string | null>;

// On a 401 the client asks for a fresh token once (POST /auth/refresh) and retries.
// If the refresh fails, the session ends and the person signs in again.
// Tests swap this out with setTokenProvider.
let tokenProvider: TokenProvider = async (forceRefresh) => {
  if (!forceRefresh) return getSessionToken();
  try {
    const fresh = await refreshSession();
    if (!fresh) {
      clearSession();
      return null;
    }
    setSession(fresh.access_token, fresh.user ?? getSessionUser());
    return fresh.access_token;
  } catch {
    return null;
  }
};

export function setTokenProvider(provider: TokenProvider): void {
  tokenProvider = provider;
}

// The single client every screen uses.
export const api = createApiClient({
  baseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "",
  getToken: (forceRefresh) => tokenProvider(forceRefresh),
});

export { ApiError } from "./client";
