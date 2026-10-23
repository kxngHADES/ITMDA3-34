import type { Role } from "@/lib/api/types";

// Who may use the admin app at all, and which screen each role may open.
// Per the Admin API Reference, IT admins and managers can call every /admin endpoint.
// A manager only works inside their own clinic, and can only manage receptionists and nurses.
// The API checks the role again on every request. This list only decides what the app shows.

export const ADMIN_APP_ROLES: Role[] = ["it_admin", "manager"];

export type Section = "dashboard" | "staff" | "clinics" | "queue-config" | "sync" | "audit" | "analytics";

export const SECTION_ROLES: Record<Section, Role[]> = {
  dashboard: ["it_admin", "manager"],
  staff: ["it_admin", "manager"],
  clinics: ["it_admin", "manager"],
  "queue-config": ["it_admin", "manager"],
  sync: ["it_admin", "manager"],
  audit: ["it_admin", "manager"],
  analytics: ["it_admin", "manager"],
};

export const ROLE_LABEL: Record<Role, string> = {
  patient: "Patient",
  receptionist: "Receptionist",
  nurse: "Nurse",
  manager: "Manager",
  it_admin: "IT admin",
};

export function isAdminAppRole(role: Role): boolean {
  return ADMIN_APP_ROLES.includes(role);
}

export function canAccess(role: Role, section: Section): boolean {
  return SECTION_ROLES[section].includes(role);
}

export function rolesLabel(section: Section): string {
  return SECTION_ROLES[section].map((r) => ROLE_LABEL[r]).join(" and ");
}

/** The roles this person may give to staff. Managers can only add receptionists and nurses. */
export function assignableRoles(role: Role): Role[] {
  return role === "it_admin" ? ["receptionist", "nurse", "manager", "it_admin"] : ["receptionist", "nurse"];
}
