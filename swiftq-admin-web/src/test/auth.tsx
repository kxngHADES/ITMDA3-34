// Test helper: puts the screen inside a signed in session, using the real AuthProvider.
import type { ReactNode } from "react";
import type { Role } from "@/lib/api/types";
import { AuthProvider, useAuth } from "@/lib/auth/AuthProvider";
import { setSession } from "@/lib/auth/session";
import type { FakeApi } from "./fakeApi";

export function signInAs(api: FakeApi, role: Role = "it_admin", clinicId: string | null = null) {
  const user = { id: "u1", email: "admin@swiftq.co.za", role, full_name: "Test Admin", clinic_id: clinicId };
  setSession("test-token", user);
  api.on(({ url, method }) =>
    method === "POST" && url.pathname === "/v1/auth/refresh"
      ? { body: { access_token: "test-token", token_type: "Bearer", expires_in: 604800, user } }
      : undefined,
  );
}

function Gate({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  return state.status === "ready" ? <>{children}</> : null;
}

export function WithAuth({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <Gate>{children}</Gate>
    </AuthProvider>
  );
}
