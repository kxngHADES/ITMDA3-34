import type { StaffSession } from "@/lib/api/auth";

const KEY = "swiftq.staff.session";

export function saveSession(session: StaffSession): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    // storage unavailable
  }
}

export function getSession(): StaffSession | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as StaffSession;
    if (session.expiresAt && Date.parse(session.expiresAt) <= Date.now()) {
      sessionStorage.removeItem(KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(KEY);
    sessionStorage.removeItem(SERVICE_POINT_KEY);
  } catch {
    // storage unavailable
  }
}

// The service point this device is working at, kept for the browser tab like the session.
const SERVICE_POINT_KEY = "swiftq.staff.servicePoint";

export function getServicePointId(): string | null {
  try {
    return sessionStorage.getItem(SERVICE_POINT_KEY);
  } catch {
    return null;
  }
}

export function saveServicePointId(id: string): void {
  try {
    sessionStorage.setItem(SERVICE_POINT_KEY, id);
  } catch {
    // storage unavailable
  }
}
