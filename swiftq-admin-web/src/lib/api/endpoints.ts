// One small typed function per endpoint the admin app calls.
// Paths and roles come from the API contract, section 6 and the plan-task table in section 9.

import { getSessionUser } from "@/lib/auth/session";
import { api } from "./index";
import type {
  AnalyticsSnapshot,
  Clinic,
  ConflictResolution,
  CreateClinicRequest,
  CreateServicePointRequest,
  CreateStaffRequest,
  Page,
  PageQuery,
  QueueConfig,
  ResolveConflictRequest,
  ServicePoint,
  StaffAccount,
  SyncLogEntry,
  SyncLogStatus,
  UpdateStaffRequest,
  Role,
} from "./types";

// The shared API returns plain { items: [...] } lists with no paging. This turns that into the
// Page shape the screens use, so "load more" simply never appears.
const asPage = <T>(items: T[]): Page<T> => ({ items, next_cursor: null, has_more: false });

const id = encodeURIComponent; // ids are opaque strings, so never build a path from one unescaped

// ---- Clinics and service points
// Managers only work inside their own clinic, and the API returns 403 for any other, so the
// list a manager sees is just their own clinic.
export async function listClinics(_q: PageQuery = {}): Promise<Page<Clinic>> {
  const { items } = await api.json<{ items: Clinic[] }>("/v1/clinics");
  const me = getSessionUser();
  const mine = me?.role === "manager" && me.clinic_id ? items.filter((c) => c.id === me.clinic_id) : items;
  return asPage(mine);
}
export const listServicePoints = (clinicId: string) =>
  api.json<{ items: ServicePoint[] }>(`/v1/clinics/${id(clinicId)}/service-points`);
export const createClinic = (body: CreateClinicRequest) =>
  api.json<Clinic>("/v1/admin/clinics", { method: "POST", body });
export const createServicePoint = (clinicId: string, body: CreateServicePointRequest) =>
  api.json<ServicePoint>(`/v1/admin/clinics/${id(clinicId)}/service-points`, { method: "POST", body });
// Editing or deactivating a clinic or service point is not built on the server yet.

// ---- Queue configuration (belongs to a service point, so it can be edited before a queue opens)
export const getQueueConfig = (servicePointId: string) =>
  api.json<QueueConfig>(`/v1/admin/service-points/${id(servicePointId)}/queue-config`);
export const updateQueueConfig = (servicePointId: string, body: QueueConfig) =>
  api.json<QueueConfig>(`/v1/admin/service-points/${id(servicePointId)}/queue-config`, { method: "PUT", body });

// ---- Staff accounts
export interface StaffQuery extends PageQuery {
  clinic_id?: string;
  role?: Exclude<Role, "patient">;
  include_inactive?: boolean;
}
// The API lists every staff member, active or not. The role and "show deactivated" filters
// are applied here, because the API only filters by clinic.
export async function listStaff(q: StaffQuery = {}): Promise<Page<StaffAccount>> {
  const { items } = await api.json<{ items: StaffAccount[] }>("/v1/admin/staff", {
    query: { clinic_id: q.clinic_id },
  });
  return asPage(items.filter((p) => (q.include_inactive || p.is_active) && (!q.role || p.role === q.role)));
}
export const createStaff = (body: CreateStaffRequest) =>
  api.json<StaffAccount>("/v1/admin/staff", { method: "POST", body });
export const updateStaff = (staffId: string, body: UpdateStaffRequest) =>
  api.json<StaffAccount>(`/v1/admin/staff/${id(staffId)}`, { method: "PATCH", body });
/** DELETE only deactivates: the record is kept. Undo it with updateStaff(id, { is_active: true }). */
export const deactivateStaff = (staffId: string) =>
  api.json<void>(`/v1/admin/staff/${id(staffId)}`, { method: "DELETE" });

// ---- Sync log
export interface SyncLogQuery extends PageQuery {
  status?: SyncLogStatus;
  clinic_id?: string;
}
// Newest first. The API has no paging, only a limit of up to 200, so we ask for the most it allows.
export async function listSyncLog(q: SyncLogQuery = {}): Promise<Page<SyncLogEntry>> {
  const { items } = await api.json<{ items: SyncLogEntry[] }>("/v1/admin/sync-log", {
    query: { clinic_id: q.clinic_id, status: q.status, limit: 200 },
  });
  return asPage(items);
}
export const resolveConflict = (entryId: string, resolution: ConflictResolution, note?: string) => {
  const body: ResolveConflictRequest = note ? { resolution, note } : { resolution };
  return api.json<SyncLogEntry>(`/v1/admin/sync-log/${id(entryId)}/resolve`, { method: "POST", body });
};
// The audit log and the error log are not built on the server yet.

// ---- Analytics
export const getAnalytics = (clinicId: string, from: string, to: string) =>
  api.json<{ items: AnalyticsSnapshot[] }>(`/v1/admin/clinics/${id(clinicId)}/analytics`, {
    query: { from, to, format: "json" },
  });
export const downloadAnalyticsCsv = (clinicId: string, from: string, to: string) =>
  api.download(`/v1/admin/clinics/${id(clinicId)}/analytics`, { query: { from, to, format: "csv" } });

// ---- Helper: some dropdowns need every row, so keep asking until has_more is false
export async function fetchAll<T>(
  load: (q: PageQuery) => Promise<Page<T>>,
  limit = 100,
): Promise<T[]> {
  const all: T[] = [];
  let cursor: string | undefined;
  for (let i = 0; i < 50; i++) {
    const page = await load({ limit, cursor });
    all.push(...page.items);
    if (!page.has_more || !page.next_cursor) break;
    cursor = page.next_cursor;
  }
  return all;
}
