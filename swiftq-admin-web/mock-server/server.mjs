// A small fake SwiftQ API so the admin app can be tried out without the real backend.
//
//   npm run mock               starts it as an IT admin on http://localhost:4010
//   npm run mock -- manager    starts it as a manager of the first clinic
//
// It follows the SwiftQ Admin API Reference: POST /v1/auth/login and /v1/auth/refresh,
// the same paths, error format and role rules. Sign in with ANY email and password.
// It does not check tokens, and data lives in memory and resets on restart.
// Do not use it for final screenshots or testing. Use the real shared API for those.

import http from "node:http";

const ROLE = process.argv[2] || process.env.MOCK_ROLE || "it_admin";
if (!["it_admin", "manager"].includes(ROLE)) {
  console.error(`Role must be it_admin or manager, not "${ROLE}".`);
  process.exit(1);
}
const PORT = Number(process.env.PORT) || 4010;
const ME_ID = ROLE === "manager" ? "s-mgr" : "s-admin";

let seq = 100;
const newId = (prefix) => `${prefix}-${++seq}`;
const ago = (minutes) => new Date(Date.now() - minutes * 60_000).toISOString();

// ---------------------------------------------------------------- seed data
const hours = [
  ...["mon", "tue", "wed", "thu", "fri"].map((day) => ({ day, opens: "07:00", closes: "16:00" })),
  { day: "sat", opens: "08:00", closes: "12:00" },
];
const clinics = [
  { id: "c1", name: "Soshanguve Block B Clinic", address: "Block B, Soshanguve, Pretoria", is_open: true, is_active: true, opening_hours: hours },
  { id: "c2", name: "Mamelodi East Clinic", address: "Tsamaya Road, Mamelodi East", is_open: false, is_active: true, opening_hours: hours.slice(0, 5) },
];
const servicePoints = [
  { id: "sp1", clinic_id: "c1", name: "General consultation", icon: "stethoscope", is_active: true, queue_id: "q1", concurrent_servers: 2 },
  { id: "sp2", clinic_id: "c1", name: "Pharmacy", icon: "pill", is_active: true, queue_id: null, concurrent_servers: 1 },
  { id: "sp3", clinic_id: "c2", name: "General consultation", icon: "stethoscope", is_active: true, queue_id: null, concurrent_servers: 1 },
];
const queueConfigs = {
  q1: {
    priority_weights: { elderly: 3, pregnant: 3, disability: 3, urgent: 6 },
    priority_max_jump_k: 10, starvation_threshold_w_minutes: 100, late_requeue_penalty_p: 5,
    default_base_time_seconds: 900, ema_alpha: 0.2, min_samples_for_ema: 5,
  },
};
const staff = [
  { id: "s-admin", email: "ayanda@swiftq.test", display_name: "Ayanda Mokoena", role: "it_admin", clinic_id: "c1", is_active: true, created_at: ago(60 * 24 * 40), last_sign_in_at: ago(5) },
  { id: "s-mgr", email: "mpho@swiftq.test", display_name: "Mpho Dlamini", role: "manager", clinic_id: "c1", is_active: true, created_at: ago(60 * 24 * 38), last_sign_in_at: ago(90) },
  { id: "s-nurse1", email: "thandi@swiftq.test", display_name: "Thandi Nkosi", role: "nurse", clinic_id: "c1", is_active: true, created_at: ago(60 * 24 * 30), last_sign_in_at: ago(25) },
  { id: "s-recep1", email: "sipho@swiftq.test", display_name: "Sipho Khumalo", role: "receptionist", clinic_id: "c1", is_active: true, created_at: ago(60 * 24 * 30), last_sign_in_at: ago(12) },
  { id: "s-nurse2", email: "lerato@swiftq.test", display_name: "Lerato Mahlangu", role: "nurse", clinic_id: "c2", is_active: true, created_at: ago(60 * 24 * 20), last_sign_in_at: ago(60 * 26) },
  { id: "s-old", email: "johan@swiftq.test", display_name: "Johan Pretorius", role: "receptionist", clinic_id: "c2", is_active: false, created_at: ago(60 * 24 * 90), last_sign_in_at: ago(60 * 24 * 12) },
].map((s) => ({ ...s, updated_at: s.created_at }));

const conflict = (client, server, clientAgo, serverAgo) => ({
  client_value: client, server_value: server, client_updated_at: ago(clientAgo), server_updated_at: ago(serverAgo),
});
const syncLog = [
  { id: "sl1", device_uid: "dev-7c1f90ab22e4", clinic_id: "c1", entity_type: "ticket", entity_id: "tk-0001-aa91", op: "update_ticket", status: "conflict_pending", attempt: 2, created_at: ago(14),
    conflict: conflict({ language: "zu", notification_channel: "sms", display_name: "Sipho" }, { language: "en", notification_channel: "sms", display_name: "Sipho" }, 15, 16) },
  { id: "sl2", device_uid: "dev-3b88d5e1f0c7", clinic_id: "c1", entity_type: "ticket", entity_id: "tk-0002-bb17", op: "update_ticket", status: "conflict_pending", attempt: 1, created_at: ago(47),
    conflict: conflict({ priority_request: "pregnant" }, { priority_request: "none" }, 49, 44) },
  { id: "sl3", device_uid: "dev-a0e4417c9d02", clinic_id: "c2", entity_type: "ticket", entity_id: "tk-0003-cc52", op: "cancel", status: "conflict_pending", attempt: 3, created_at: ago(130),
    conflict: conflict({ status: "cancelled" }, { status: "called" }, 135, 128) },
  { id: "sl4", device_uid: "dev-7c1f90ab22e4", clinic_id: "c1", entity_type: "ticket", entity_id: "tk-0004-dd03", op: "join", status: "applied", attempt: 1, created_at: ago(200) },
  { id: "sl5", device_uid: "dev-3b88d5e1f0c7", clinic_id: "c1", entity_type: "ticket", entity_id: "tk-0005-ee44", op: "join", status: "applied", attempt: 1, created_at: ago(260) },
  { id: "sl6", device_uid: "dev-3b88d5e1f0c7", clinic_id: "c1", entity_type: "ticket", entity_id: "tk-0005-ee44", op: "join", status: "duplicate", attempt: 2, created_at: ago(259) },
  { id: "sl7", device_uid: "dev-91ab33cc0e15", clinic_id: "c2", entity_type: "ticket", entity_id: "tk-0006-ff20", op: "update_ticket", status: "conflict_resolved", attempt: 1, resolution: "last_write_wins", created_at: ago(400) },
  { id: "sl8", device_uid: "dev-91ab33cc0e15", clinic_id: "c2", entity_type: "ticket", entity_id: null, op: "join", status: "rejected", attempt: 1, created_at: ago(520) },
  { id: "sl9", device_uid: "dev-a0e4417c9d02", clinic_id: "c1", entity_type: "ticket", entity_id: "tk-0007-aa66", op: "update_ticket", status: "resolved", attempt: 2, resolution: "keep_server", resolved_by: "s-admin", resolved_at: ago(900), created_at: ago(1000) },
];
for (let i = 0; i < 25; i++) {
  syncLog.push({ id: `slx${i}`, device_uid: `dev-${(i % 4) + 1}0aa55bb77cc`, clinic_id: i % 3 ? "c1" : "c2", entity_type: "ticket", entity_id: `tk-1${i}-xy`, op: i % 5 ? "join" : "cancel", status: "applied", attempt: 1, created_at: ago(1100 + i * 37) });
}
const errorLog = [
  { id: "er1", request_id: "req_01J9X3AA", status: 500, code: "SERVER_ERROR", method: "POST", path: "/v1/sync/push", actor_id: "dev-7c1f90ab22e4", detail: "firestore.exceptions.DeadlineExceeded: 504 Deadline Exceeded while writing live_queues/q1", created_at: ago(35) },
  { id: "er2", request_id: "req_01J9X3AB", status: 403, code: "FORBIDDEN", method: "POST", path: "/v1/admin/staff", actor_id: "s-nurse1", detail: "RBAC denial: role nurse is not allowed to call createStaff", created_at: ago(180) },
  { id: "er3", request_id: "req_01J9X3AC", status: 429, code: "RATE_LIMITED", method: "POST", path: "/v1/queues/q1/tickets", actor_id: "dev-3b88d5e1f0c7", detail: "token bucket empty for device dev-3b88d5e1f0c7 (5 per minute)", created_at: ago(260) },
  { id: "er4", request_id: "req_01J9X3AD", status: 500, code: "SERVER_ERROR", method: "GET", path: "/v1/queues/q1", actor_id: null, detail: "redis.exceptions.ConnectionError: rate limit store unreachable (fail-open applied)", created_at: ago(700) },
  { id: "er5", request_id: "req_01J9X3AE", status: 401, code: "UNAUTHENTICATED", method: "GET", path: "/v1/me", actor_id: null, detail: "ID token expired", created_at: ago(900) },
];
const actions = [
  ["ticket.call_next", "ticket", "success"], ["ticket.serve", "ticket", "success"], ["ticket.no_show", "ticket", "success"],
  ["ticket.walk_in", "ticket", "success"], ["queue.pause", "queue", "success"], ["queue.resume", "queue", "success"],
  ["ticket.confirm_priority", "ticket", "success"], ["staff.create", "staff", "denied"], ["ticket.dismiss", "ticket", "failed"],
];
const actors = staff.filter((s) => s.id !== "s-admin" && s.id !== "s-mgr");
const auditLog = Array.from({ length: 60 }, (_, i) => {
  const [action, target_type, outcome] = actions[(i * 7) % actions.length];
  const actor = actors[i % actors.length];
  return { id: `au${i}`, actor_id: actor.id, actor_role: actor.role, action, target_type, target_id: `${target_type}-${1000 + i}-x9`, outcome: i % 11 === 0 ? outcome : "success", created_at: ago(10 + i * 41) };
});

// ---------------------------------------------------------------- helpers
class HttpError extends Error {
  constructor(status, code, message, retryable = false) { super(message); Object.assign(this, { status, code, retryable }); }
}
const bad = (code, message) => new HttpError(400, code, message);
const invalid = (message) => new HttpError(422, "VALIDATION_FAILED", message);
const notFound = () => new HttpError(404, "NOT_FOUND", "We couldn't find that.");

function paginate(items, query) {
  const limit = Math.min(100, Math.max(1, Number(query.get("limit")) || 25));
  const start = query.get("cursor") ? Number(Buffer.from(query.get("cursor"), "base64").toString()) || 0 : 0;
  const slice = items.slice(start, start + limit);
  const end = start + slice.length;
  const has_more = end < items.length;
  return { items: slice, next_cursor: has_more ? Buffer.from(String(end)).toString("base64") : null, has_more };
}

function audit(action, target_type, target_id) {
  auditLog.unshift({ id: newId("au"), actor_id: ME_ID, actor_role: ROLE, action, target_type, target_id, outcome: "success", created_at: new Date().toISOString() });
}

function rng(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6d2b79f5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
function snapshots(clinicId, from, to) {
  const out = [];
  const people = staff.filter((s) => s.clinic_id === clinicId && s.is_active && ["nurse", "receptionist"].includes(s.role));
  for (let d = new Date(`${from}T00:00:00Z`); d <= new Date(`${to}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + 1)) {
    const date = d.toISOString().slice(0, 10);
    const r = rng(Number(date.replaceAll("-", "")) + clinicId.length * 97);
    const day = d.getUTCDay();
    if (day === 0) continue; // closed on Sundays
    const joined = Math.round(30 + r() * 70);
    const noShows = Math.round(r() * 6), cancellations = Math.round(r() * 5);
    const served = Math.max(0, joined - noShows - cancellations);
    const avg = Math.round((8 + r() * 38) * 10) / 10;
    const app = Math.round(joined * 0.6), sms = Math.round(joined * 0.15);
    out.push({
      clinic_id: clinicId, date, tickets_joined: joined, tickets_served: served, no_shows: noShows, cancellations,
      avg_wait_minutes: avg, p90_wait_minutes: Math.round(avg * 1.8 * 10) / 10, peak_hour: 8 + Math.floor(r() * 4),
      joins_by_channel: { app, sms, staff_assisted: Math.max(0, joined - app - sms) },
      calls_per_staff: people.map((p, i) => ({ staff_id: p.id, calls: Math.round(served / Math.max(1, people.length) + (i ? -2 : 2)) })),
    });
  }
  return out;
}

const clinicOf = (id) => clinics.find((c) => c.id === id) ?? (() => { throw notFound(); })();
const staffOf = (id) => staff.find((s) => s.id === id) ?? (() => { throw notFound(); })();

function checkClinicBody(b, partial) {
  if (!partial || b.name !== undefined) if (typeof b.name !== "string" || !b.name.trim() || b.name.length > 80) throw invalid("Enter a clinic name of 80 characters or fewer.");
  if (!partial || b.address !== undefined) if (typeof b.address !== "string" || !b.address.trim() || b.address.length > 200) throw invalid("Enter an address of 200 characters or fewer.");
  if (!partial || b.opening_hours !== undefined) if (!Array.isArray(b.opening_hours) || b.opening_hours.length === 0) throw invalid("Choose at least one opening day.");
}
function checkServicePointBody(b, partial) {
  if (!partial || b.name !== undefined) if (typeof b.name !== "string" || !b.name.trim() || b.name.length > 60) throw invalid("Enter a name of 60 characters or fewer.");
  if (!partial || b.icon !== undefined) if (typeof b.icon !== "string" || !b.icon.trim() || b.icon.length > 40) throw invalid("Enter an icon name of 40 characters or fewer.");
  if (b.concurrent_servers !== undefined) if (!Number.isInteger(b.concurrent_servers) || b.concurrent_servers < 1 || b.concurrent_servers > 20) throw invalid("Staff serving at once must be from 1 to 20.");
}
function checkConfig(c) {
  const w = c?.priority_weights;
  const whole = (v, min) => Number.isInteger(v) && v >= min;
  if (!w || !["elderly", "pregnant", "disability", "urgent"].every((k) => whole(w[k], 0))) throw invalid("Priority weights must be whole numbers, 0 or more.");
  if (!whole(c.priority_max_jump_k, 0)) throw invalid("The jump limit must be a whole number, 0 or more.");
  if (!whole(c.starvation_threshold_w_minutes, 1)) throw invalid("The protection time must be at least 1 minute.");
  if (!whole(c.late_requeue_penalty_p, 0)) throw invalid("The late penalty must be a whole number, 0 or more.");
  if (!whole(c.default_base_time_seconds, 60)) throw invalid("The starting consultation time must be at least 60 seconds.");
  if (c.ema_alpha !== undefined && !(c.ema_alpha >= 0.01 && c.ema_alpha <= 1)) throw invalid("Smoothing must be from 0.01 to 1.");
  if (c.min_samples_for_ema !== undefined && !whole(c.min_samples_for_ema, 1)) throw invalid("Minimum consultations must be 1 or more.");
}

// ---------------------------------------------------------------- routes
const ANY = null, ADMIN_OR_MGR = ["it_admin", "manager"];
const routes = [];
const route = (method, path, roles, handler) => routes.push({ method, roles, handler, re: new RegExp(`^${path.replace(/:(\w+)/g, "(?<$1>[^/]+)")}$`) });

route("GET", "/health", "public", () => ({ status: "ok" }));
const MY_CLINIC = ROLE === "manager" ? "c1" : null; // an IT admin works across all clinics
const loginBody = () => ({
  access_token: `mock-token-${Math.random().toString(36).slice(2, 10)}`, token_type: "Bearer", expires_in: 604800,
  user: { id: ME_ID, email: staffOf(ME_ID).email, role: ROLE, full_name: staffOf(ME_ID).display_name, clinic_id: MY_CLINIC },
});
route("POST", "/v1/auth/login", "public", ({ body }) => {
  if (typeof body.email !== "string" || !body.email.trim() || typeof body.password !== "string" || !body.password) throw invalid("Enter an email address and a password.");
  return loginBody();
});
route("POST", "/v1/auth/refresh", ANY, () => loginBody());
route("GET", "/v1/clinics", ANY, () => ({ items: clinics }));
route("GET", "/v1/clinics/:id/service-points", ANY, ({ params }) => ({ items: servicePoints.filter((s) => s.clinic_id === clinicOf(params.id).id) }));
route("POST", "/v1/admin/clinics", ADMIN_OR_MGR, ({ body }) => {
  checkClinicBody(body, false);
  const clinic = { id: newId("c"), name: body.name.trim(), address: body.address.trim(), opening_hours: body.opening_hours, is_open: false, is_active: true };
  clinics.push(clinic);
  return { status: 201, body: clinic };
});
route("POST", "/v1/admin/clinics/:id/service-points", ADMIN_OR_MGR, ({ params, body }) => {
  clinicOf(params.id); checkServicePointBody(body, false);
  const sp = { id: newId("sp"), clinic_id: params.id, name: body.name.trim(), icon: body.icon.trim(), is_active: true, queue_id: null, concurrent_servers: body.concurrent_servers ?? 1 };
  servicePoints.push(sp);
  return { status: 201, body: sp };
});
const defaultConfig = () => ({
  priority_weights: { elderly: 3, pregnant: 3, disability: 3, urgent: 6 },
  priority_max_jump_k: 10, starvation_threshold_w_minutes: 100, late_requeue_penalty_p: 5,
  default_base_time_seconds: 900, ema_alpha: 0.2, min_samples_for_ema: 5,
});
const spOf = (id) => servicePoints.find((x) => x.id === id) ?? (() => { throw notFound(); })();
const mustBeMine = (clinicId) => { if (MY_CLINIC && clinicId !== MY_CLINIC) throw new HttpError(403, "FORBIDDEN", "You don't have permission to do this."); };
route("GET", "/v1/admin/service-points/:id/queue-config", ADMIN_OR_MGR, ({ params }) => {
  const sp = spOf(params.id); mustBeMine(sp.clinic_id);
  return queueConfigs[sp.queue_id] ?? queueConfigs[`sp:${sp.id}`] ?? defaultConfig();
});
route("PUT", "/v1/admin/service-points/:id/queue-config", ADMIN_OR_MGR, ({ params, body }) => {
  const sp = spOf(params.id); mustBeMine(sp.clinic_id);
  checkConfig(body); queueConfigs[`sp:${sp.id}`] = body;
  return body;
});

const ROLES = ["receptionist", "nurse", "manager", "it_admin"];
const mgrMayManage = (role) => { if (MY_CLINIC && !["receptionist", "nurse"].includes(role)) throw new HttpError(403, "FORBIDDEN", "You don't have permission to do this."); };
route("GET", "/v1/admin/staff", ADMIN_OR_MGR, ({ query }) => {
  const wanted = MY_CLINIC ?? query.get("clinic_id");
  return { items: (wanted ? staff.filter((x) => x.clinic_id === wanted) : staff).slice().sort((x, y) => x.email.localeCompare(y.email)) };
});
route("POST", "/v1/admin/staff", ADMIN_OR_MGR, ({ body }) => {
  const email = String(body.email ?? "").trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw invalid("Enter a valid email address.");
  if (typeof body.display_name !== "string" || !body.display_name.trim() || body.display_name.length > 60) throw invalid("Enter a name of 60 characters or fewer.");
  if (!ROLES.includes(body.role)) throw invalid("Choose a valid role.");
  mgrMayManage(body.role); clinicOf(body.clinic_id); mustBeMine(body.clinic_id);
  if (staff.some((x) => x.email === email)) throw bad("EMAIL_IN_USE", "A staff account already uses this email address.");
  const person = { id: newId("s"), email, display_name: body.display_name.trim(), role: body.role, clinic_id: body.clinic_id, is_active: true, created_at: new Date().toISOString(), updated_at: new Date().toISOString(), last_sign_in_at: null };
  staff.unshift(person);
  return { status: 201, body: person };
});
route("GET", "/v1/admin/staff/:id", ADMIN_OR_MGR, ({ params }) => { const person = staffOf(params.id); mustBeMine(person.clinic_id); return person; });
route("PATCH", "/v1/admin/staff/:id", ADMIN_OR_MGR, ({ params, body }) => {
  const person = staffOf(params.id); mustBeMine(person.clinic_id); mgrMayManage(person.role);
  if (Object.keys(body).length === 0) throw invalid("Send at least one field.");
  if (body.role && !ROLES.includes(body.role)) throw invalid("Choose a valid role.");
  if (body.role) { mgrMayManage(body.role); if (person.id === ME_ID) throw new HttpError(403, "FORBIDDEN", "You can't change your own role."); }
  if (body.is_active === false && person.id === ME_ID) throw bad("CANNOT_DEACTIVATE_SELF", "You can't deactivate your own account.");
  if (body.clinic_id) { clinicOf(body.clinic_id); mustBeMine(body.clinic_id); }
  Object.assign(person, body, { updated_at: new Date().toISOString() });
  return person;
});
route("DELETE", "/v1/admin/staff/:id", ADMIN_OR_MGR, ({ params }) => {
  const person = staffOf(params.id); mustBeMine(person.clinic_id); mgrMayManage(person.role);
  if (person.id === ME_ID) throw bad("CANNOT_DEACTIVATE_SELF", "You can't deactivate your own account.");
  person.is_active = false; person.updated_at = new Date().toISOString();
  return { status: 204, body: null };
});

route("GET", "/v1/admin/sync-log", ADMIN_OR_MGR, ({ query }) => {
  let list = MY_CLINIC ? syncLog.filter((e) => e.clinic_id === MY_CLINIC) : syncLog;
  if (query.get("status")) list = list.filter((e) => e.status === query.get("status"));
  if (query.get("clinic_id")) list = list.filter((e) => e.clinic_id === query.get("clinic_id"));
  return { items: list.slice(0, Math.min(200, Number(query.get("limit")) || 50)) };
});
route("POST", "/v1/admin/sync-log/:id/resolve", ADMIN_OR_MGR, ({ params, body }) => {
  const entry = syncLog.find((e) => e.id === params.id) ?? (() => { throw notFound(); })();
  if (!["keep_server", "apply_client", "discard"].includes(body.resolution)) throw invalid("Choose how to resolve the conflict.");
  if (typeof body.note === "string" && body.note.length > 280) throw invalid("Use 280 characters or fewer.");
  if (entry.status !== "conflict_pending") throw bad("CONFLICT_ALREADY_RESOLVED", "This conflict was already resolved.");
  Object.assign(entry, { status: "resolved", resolution: body.resolution, resolved_by: ME_ID, resolved_at: new Date().toISOString() });
  return entry;
});
route("GET", "/v1/admin/clinics/:id/analytics", ADMIN_OR_MGR, ({ params, query }) => {
  clinicOf(params.id); mustBeMine(params.id);
  const from = query.get("from"), to = query.get("to");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from ?? "") || !/^\d{4}-\d{2}-\d{2}$/.test(to ?? "")) throw invalid("Send from and to as dates, like 2026-10-01.");
  const days = (Date.parse(to) - Date.parse(from)) / 86_400_000 + 1;
  if (!(days >= 1)) throw bad("DATE_RANGE_TOO_LARGE", "The end date can't be before the start date.");
  if (days > 92) throw bad("DATE_RANGE_TOO_LARGE", "Choose a range of 92 days or less.");
  const items = snapshots(params.id, from, to);
  if (query.get("format") === "csv") {
    const head = "date,tickets_joined,tickets_served,no_shows,cancellations,avg_wait_minutes,p90_wait_minutes,peak_hour";
    const rows = items.map((s) => [s.date, s.tickets_joined, s.tickets_served, s.no_shows, s.cancellations, s.avg_wait_minutes, s.p90_wait_minutes, s.peak_hour].join(","));
    return { raw: [head, ...rows].join("\n") + "\n", type: "text/csv", headers: { "Content-Disposition": `attachment; filename="swiftq-analytics-${params.id}-${from}-to-${to}.csv"` } };
  }
  return { items };
});

// ---------------------------------------------------------------- server
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
  "Access-Control-Allow-Headers": "Authorization,Content-Type,Idempotency-Key,If-None-Match",
  "Access-Control-Expose-Headers": "Content-Disposition,Retry-After",
};

function send(res, status, body, extra = {}) {
  const raw = typeof body === "string";
  res.writeHead(status, { ...CORS, "Content-Type": raw ? extra["Content-Type"] ?? "text/plain" : "application/json", ...extra });
  res.end(status === 204 ? undefined : raw ? body : JSON.stringify(body));
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") return send(res, 204, "");
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const requestId = `req_mock_${Math.random().toString(36).slice(2, 8)}`;
  try {
    const match = routes.map((r) => ({ r, m: r.method === req.method ? r.re.exec(url.pathname) : null })).find((x) => x.m);
    if (!match) throw notFound();
    const { r, m } = match;

    if (r.roles !== "public") {
      if (!/^Bearer .+/.test(req.headers.authorization ?? "")) throw new HttpError(401, "UNAUTHENTICATED", "Your session has ended. Sign in again.");
      if (r.roles !== ANY && !r.roles.includes(ROLE)) throw new HttpError(403, "FORBIDDEN", "You don't have permission to do this.");
    }
    let body = {};
    if (["POST", "PUT", "PATCH"].includes(req.method)) {
      const chunks = [];
      for await (const c of req) chunks.push(c);
      const text = Buffer.concat(chunks).toString();
      try { body = text ? JSON.parse(text) : {}; } catch { throw invalid("The request body isn't valid JSON."); }
    }
    await new Promise((r2) => setTimeout(r2, 120)); // a little delay, so loading states are visible
    const out = r.handler({ params: m.groups ?? {}, query: url.searchParams, body });
    if (out?.raw !== undefined) return send(res, 200, out.raw, { "Content-Type": out.type, ...out.headers });
    if (out && typeof out.status === "number" && "body" in out) return send(res, out.status, out.body);
    return send(res, 200, out);
  } catch (e) {
    if (e instanceof HttpError) {
      console.log(`${req.method} ${url.pathname} -> ${e.status} ${e.code}`);
      return send(res, e.status, { error: { code: e.code, message: e.message, retryable: e.retryable, request_id: requestId } });
    }
    console.error(e);
    return send(res, 500, { error: { code: "SERVER_ERROR", message: "Something went wrong on our side. Try again in a moment.", retryable: true, request_id: requestId } });
  }
});

server.listen(PORT, () => {
  console.log(`SwiftQ mock API running on http://localhost:${PORT}`);
  console.log(`Signed-in role: ${ROLE} (${staffOf(ME_ID).display_name}). Sign in with any email and password.`);
  console.log("Sign in on the website with any email and password.");
  console.log("Stop it with Ctrl+C. Data resets each time it starts.");
});
