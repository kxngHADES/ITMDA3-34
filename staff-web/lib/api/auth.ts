import { ApiError, apiRequest } from "./client";

export type StaffRole = "receptionist" | "nurse" | "manager" | "it_admin";

export interface StaffSession {
  token: string;
  name: string;
  email: string;
  role: string;
  clinicId: string | null;
  expiresAt: string;
}

interface AuthResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  user: {
    id: string;
    email: string;
    role: StaffRole;
    full_name: string;
    clinic_id?: string | null;
  };
}

export async function login(creds: { email: string; password: string }): Promise<StaffSession> {
  try {
    const res = await apiRequest<AuthResponse>("/v1/auth/login", {
      method: "POST",
      body: { email: creds.email.trim(), password: creds.password },
    });
    return {
      token: res.access_token,
      name: res.user.full_name,
      email: res.user.email,
      role: res.user.role,
      clinicId: res.user.clinic_id ?? null,
      expiresAt: new Date(Date.now() + res.expires_in * 1000).toISOString(),
    };
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.status === 401 || e.status === 403 || e.status === 422)
        throw new Error("Incorrect email or password.");
      throw e;
    }
    throw new Error("Could not reach the SwiftQ server. Check your connection and try again.");
  }
}
