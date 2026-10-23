"use client";

// Keeps track of who is signed in and what they are allowed to do.
//
// Signing in sends the email and password to the shared API (POST /auth/login), which sends
// back a JWT and the person's details, including their role. When the page is reloaded we
// ask the API to refresh the token (POST /auth/refresh), which also reads the role again.

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import type { LoginUser, Me } from "@/lib/api/types";
import { errorMessage } from "@/lib/errors";
import { isAdminAppRole } from "./permissions";
import { clearSession, getSessionToken, login, refreshSession, setSession, userToMe } from "./session";

export type AuthState =
  | { status: "loading" }
  | { status: "signed-out" }
  | { status: "denied"; me?: Me } // signed in, but not an IT admin or manager
  | { status: "error"; message: string } // signed in, but the API could not be reached
  | { status: "ready"; me: Me };

interface AuthContextValue {
  state: AuthState;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  retry: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function stateFor(user: LoginUser): AuthState {
  const me = userToMe(user);
  return isAdminAppRole(me.role) ? { status: "ready", me } : { status: "denied", me };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });
  const latest = useRef(0); // ignore answers from older checks if a newer one has started

  // Restore a saved session after a page load. The refresh call proves the token still works.
  const restore = useCallback(async () => {
    const run = ++latest.current;
    if (!getSessionToken()) {
      setState({ status: "signed-out" });
      return;
    }
    setState({ status: "loading" });
    try {
      const fresh = await refreshSession();
      if (run !== latest.current) return;
      if (!fresh) {
        clearSession();
        setState({ status: "signed-out" });
        return;
      }
      setSession(fresh.access_token, fresh.user);
      setState(stateFor(fresh.user));
    } catch (e) {
      if (run === latest.current) setState({ status: "error", message: errorMessage(e) });
    }
  }, []);

  useEffect(() => {
    void restore();
  }, [restore]);

  const signIn = useCallback(async (email: string, password: string) => {
    const result = await login(email.trim(), password);
    latest.current++;
    setSession(result.access_token, result.user);
    setState(stateFor(result.user));
  }, []);

  const signOut = useCallback(async () => {
    latest.current++;
    clearSession();
    setState({ status: "signed-out" });
  }, []);

  const value = useMemo(() => ({ state, signIn, signOut, retry: () => void restore() }), [state, signIn, signOut, restore]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}

/** The signed in person. Only call this on screens that sit behind the guard. */
export function useMe(): Me {
  const { state } = useAuth();
  if (state.status !== "ready") throw new Error("useMe was called before sign in finished");
  return state.me;
}
