// Hand-written from SwiftQ API Contract v1.0.0 (openapi-v1.yaml is the source of truth).
// Only the parts the admin app uses are described here.

export type Role = "patient" | "receptionist" | "nurse" | "manager" | "it_admin";
export type StaffRole = Exclude<Role, "patient">;

export interface ErrorBody {
  code: string;
  message: string;
  retryable: boolean;
  retry_after_seconds?: number;
  request_id: string;
}
export interface ErrorResponse {
  error: ErrorBody;
}

// The real API returns plain { items } lists with no paging, so the paging fields are optional.
export interface Page<T> {
  items: T[];
  next_cursor?: string | null;
  has_more?: boolean;
}
export interface PageQuery {
  limit?: number;
  cursor?: string;
}

// Built from the "user" object that POST /auth/login and POST /auth/refresh return.
// clinic_id is null for an IT admin (all clinics) and the clinic's id for a manager.
export interface Me {
  uid: string;
  role: Role;
  clinic_id: string | null;
  display_name: string | null;
  email?: string;
}

export interface LoginUser {
  id: string;
  email: string;
  role: Role;
  full_name: string | null;
  clinic_id: string | null;
}
export interface LoginResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  user: LoginUser;
}

export type Weekday = "mon" | "tue" | "wed" | "thu" | "fri" | "sat" | "sun";
export interface OpeningHoursEntry {
  day: Weekday;
  opens: string; // "07:00"
  closes: string; // "16:00"
}

export interface Clinic {
  id: string;
  name: string;
  address?: string;
  is_open: boolean;
  is_active?: boolean;
  opening_hours?: OpeningHoursEntry[];
  updated_at?: string;
}
export interface CreateClinicRequest {
  name: string;
  address: string;
  opening_hours: OpeningHoursEntry[];
}
export interface UpdateClinicRequest {
  name?: string;
  address?: string;
  opening_hours?: OpeningHoursEntry[];
  is_active?: boolean;
}

export interface ServicePoint {
  id: string;
  clinic_id: string;
  name: string;
  icon?: string;
  is_active: boolean;
  queue_id: string | null;
  concurrent_servers?: number;
  updated_at?: string;
}
export interface CreateServicePointRequest {
  name: string;
  icon: string;
  concurrent_servers?: number;
}
export interface UpdateServicePointRequest {
  name?: string;
  icon?: string;
  concurrent_servers?: number;
  is_active?: boolean;
}

export interface QueueConfig {
  priority_weights: { elderly: number; pregnant: number; disability: number; urgent: number };
  priority_max_jump_k: number;
  starvation_threshold_w_minutes: number;
  late_requeue_penalty_p: number;
  default_base_time_seconds: number;
  ema_alpha?: number;
  min_samples_for_ema?: number;
}

export interface StaffAccount {
  id: string;
  email: string;
  display_name: string;
  role: StaffRole;
  clinic_id: string;
  is_active: boolean;
  created_at: string;
  updated_at?: string;
  last_sign_in_at?: string | null;
}
export interface CreateStaffRequest {
  email: string;
  display_name: string;
  role: StaffRole;
  clinic_id: string;
}
export interface UpdateStaffRequest {
  display_name?: string;
  role?: StaffRole;
  clinic_id?: string;
  is_active?: boolean;
}

export type SyncLogStatus =
  | "applied"
  | "duplicate"
  | "conflict_resolved"
  | "conflict_pending"
  | "resolved"
  | "rejected";
export type SyncOp = "join" | "update_ticket" | "cancel";
export type ConflictResolution = "keep_server" | "apply_client" | "discard";

export interface SyncConflict {
  client_value: Record<string, unknown>;
  server_value: Record<string, unknown>;
  client_updated_at: string;
  server_updated_at: string;
}
export interface SyncLogEntry {
  id: string;
  device_uid: string;
  clinic_id?: string | null;
  idempotency_key?: string;
  entity_type: string;
  entity_id: string | null;
  op: SyncOp;
  status: SyncLogStatus;
  attempt?: number;
  conflict?: SyncConflict | null;
  resolution?: string | null;
  resolved_by?: string | null;
  resolved_at?: string | null;
  created_at: string;
}
export interface ResolveConflictRequest {
  resolution: ConflictResolution;
  note?: string;
}

export interface AuditEntry {
  id: string;
  actor_id: string;
  actor_role: Role;
  action: string;
  target_type?: string;
  target_id?: string;
  outcome: "success" | "denied" | "failed";
  created_at: string;
}

export interface ErrorLogEntry {
  id: string;
  request_id: string;
  status: number;
  code: string;
  method?: string;
  path: string;
  actor_id?: string | null;
  detail?: string;
  created_at: string;
}

export interface AnalyticsSnapshot {
  clinic_id: string;
  date: string; // YYYY-MM-DD
  tickets_joined?: number;
  tickets_served?: number;
  no_shows?: number;
  cancellations?: number;
  avg_wait_minutes?: number;
  p90_wait_minutes?: number;
  peak_hour?: number;
  joins_by_channel?: Record<string, number>;
  calls_per_staff?: { staff_id?: string; calls?: number }[];
}
