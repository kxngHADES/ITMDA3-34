// Form validation as plain functions: values in, error messages out.
// Keeping them separate from the screens means they can be unit tested (npm test)
// and the limits match the API contract's schemas, so people see the problem before the server does.

import type { QueueConfig, StaffRole } from "@/lib/api/types";
import type { HoursRow } from "@/lib/hours";

export type Errors = Record<string, string>;

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const isValidEmail = (v: string): boolean => EMAIL_RE.test(v.trim());

export function parseWholeNumber(v: string): number | null {
  const t = v.trim();
  return /^-?\d+$/.test(t) ? Number(t) : null;
}
export function parseDecimal(v: string): number | null {
  const t = v.trim();
  return /^-?\d+(\.\d+)?$/.test(t) ? Number(t) : null;
}

// ---- Staff accounts (CreateStaffRequest / UpdateStaffRequest)
export interface StaffFormValues {
  email: string;
  displayName: string;
  role: StaffRole | "";
  clinicId: string;
}

export function validateStaffForm(v: StaffFormValues, mode: "create" | "edit"): Errors {
  const e: Errors = {};
  const name = v.displayName.trim();
  if (!name) e.displayName = "Enter the person's name.";
  else if (name.length > 60) e.displayName = "Use 60 characters or fewer.";
  if (mode === "create") {
    if (!v.email.trim()) e.email = "Enter an email address.";
    else if (!isValidEmail(v.email)) e.email = "Enter a valid email address, like name@clinic.co.za.";
  }
  if (!v.role) e.role = "Choose a role.";
  if (!v.clinicId) e.clinicId = "Choose a clinic.";
  return e;
}

// ---- Clinics (CreateClinicRequest) and service points (CreateServicePointRequest)
export interface ClinicFormValues {
  name: string;
  address: string;
  hours: HoursRow[];
}

export function validateClinicForm(v: ClinicFormValues): Errors {
  const e: Errors = {};
  const name = v.name.trim();
  const address = v.address.trim();
  if (!name) e.name = "Enter the clinic name.";
  else if (name.length > 80) e.name = "Use 80 characters or fewer.";
  if (!address) e.address = "Enter the clinic address.";
  else if (address.length > 200) e.address = "Use 200 characters or fewer.";

  const open = v.hours.filter((r) => r.open);
  if (open.length === 0) e.hours = "Choose at least one day the clinic is open.";
  for (const row of open) {
    if (!row.opens || !row.closes) e[`hours.${row.day}`] = "Enter an opening and a closing time.";
    else if (row.closes <= row.opens) e[`hours.${row.day}`] = "Closing time must be after opening time.";
  }
  return e;
}

export interface ServicePointFormValues {
  name: string;
  icon: string;
  servers: string;
}

export function validateServicePointForm(v: ServicePointFormValues): Errors {
  const e: Errors = {};
  const name = v.name.trim();
  const icon = v.icon.trim();
  if (!name) e.name = "Enter a name, like General consultation.";
  else if (name.length > 60) e.name = "Use 60 characters or fewer.";
  if (!icon) e.icon = "Enter an icon name, like stethoscope.";
  else if (icon.length > 40) e.icon = "Use 40 characters or fewer.";
  const servers = parseWholeNumber(v.servers);
  if (servers === null || servers < 1 || servers > 20) e.servers = "Enter a whole number from 1 to 20.";
  return e;
}

// ---- Queue configuration (QueueConfig, API contract Table 8)
export interface QueueConfigFormValues {
  elderly: string;
  pregnant: string;
  disability: string;
  urgent: string;
  maxJump: string;
  starvationMinutes: string;
  latePenalty: string;
  baseSeconds: string;
  emaAlpha: string;
  minSamples: string;
}

export const DEFAULT_QUEUE_CONFIG: QueueConfig = {
  priority_weights: { elderly: 3, pregnant: 3, disability: 3, urgent: 6 },
  priority_max_jump_k: 10,
  starvation_threshold_w_minutes: 100,
  late_requeue_penalty_p: 5,
  default_base_time_seconds: 900,
  ema_alpha: 0.2,
  min_samples_for_ema: 5,
};

export function configToForm(c: QueueConfig): QueueConfigFormValues {
  return {
    elderly: String(c.priority_weights.elderly),
    pregnant: String(c.priority_weights.pregnant),
    disability: String(c.priority_weights.disability),
    urgent: String(c.priority_weights.urgent),
    maxJump: String(c.priority_max_jump_k),
    starvationMinutes: String(c.starvation_threshold_w_minutes),
    latePenalty: String(c.late_requeue_penalty_p),
    baseSeconds: String(c.default_base_time_seconds),
    emaAlpha: String(c.ema_alpha ?? DEFAULT_QUEUE_CONFIG.ema_alpha),
    minSamples: String(c.min_samples_for_ema ?? DEFAULT_QUEUE_CONFIG.min_samples_for_ema),
  };
}

export function validateQueueConfig(v: QueueConfigFormValues): { errors: Errors; value?: QueueConfig } {
  const errors: Errors = {};
  const whole = (key: keyof QueueConfigFormValues, min: number, text: string): number => {
    const n = parseWholeNumber(v[key]);
    if (n === null || n < min) {
      errors[key] = text;
      return 0;
    }
    return n;
  };
  const zeroUp = "Enter a whole number, 0 or more.";
  const elderly = whole("elderly", 0, zeroUp);
  const pregnant = whole("pregnant", 0, zeroUp);
  const disability = whole("disability", 0, zeroUp);
  const urgent = whole("urgent", 0, zeroUp);
  const maxJump = whole("maxJump", 0, zeroUp);
  const starvation = whole("starvationMinutes", 1, "Enter a whole number of minutes, 1 or more.");
  const latePenalty = whole("latePenalty", 0, zeroUp);
  const baseSeconds = whole("baseSeconds", 60, "Enter at least 60 seconds.");
  const minSamples = whole("minSamples", 1, "Enter a whole number, 1 or more.");
  const alpha = parseDecimal(v.emaAlpha);
  if (alpha === null || alpha < 0.01 || alpha > 1) errors.emaAlpha = "Enter a number from 0.01 to 1.";

  if (Object.keys(errors).length > 0) return { errors };
  return {
    errors,
    value: {
      priority_weights: { elderly, pregnant, disability, urgent },
      priority_max_jump_k: maxJump,
      starvation_threshold_w_minutes: starvation,
      late_requeue_penalty_p: latePenalty,
      default_base_time_seconds: baseSeconds,
      ema_alpha: alpha as number,
      min_samples_for_ema: minSamples,
    },
  };
}

// ---- Date range for analytics (the API allows at most 92 days)
const MS_PER_DAY = 86_400_000;
export function validateDateRange(from: string, to: string, maxDays = 92): string | null {
  if (!from || !to) return "Choose a start and an end date.";
  const a = Date.parse(`${from}T00:00:00Z`);
  const b = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(a) || Number.isNaN(b)) return "Choose a start and an end date.";
  if (b < a) return "The end date can't be before the start date.";
  const days = Math.round((b - a) / MS_PER_DAY) + 1;
  if (days > maxDays) return `Choose a range of ${maxDays} days or less.`;
  return null;
}

// ---- Date and time range for the audit log
export function validateDateTimeRange(from: string, to: string): string | null {
  if (!from || !to) return null;
  if (new Date(to).getTime() < new Date(from).getTime()) return "The end can't be before the start.";
  return null;
}
