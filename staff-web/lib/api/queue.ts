import { ApiError, apiRequest } from "./client";
import type {
  PriorityCategory,
  QueueView,
  TicketStatus,
  TicketView,
} from "@/lib/queue/types";

interface ApiServicePoint {
  id: string;
  name: string;
  is_active: boolean;
  queue_id: string | null;
}

interface ApiQueueEntry {
  ticket_id: string;
  display_number: string;
  position: number;
  status: TicketStatus;
  is_priority: boolean;
  late_flag: boolean;
  joined_at?: string | null;
  protected?: boolean | null;
  display_name?: string | null;
  priority_category?: PriorityCategory | null;
  staff_confirmed?: boolean | null;
}

interface ApiQueue {
  id: string;
  display_prefix: string;
  status: QueueView["status"];
  pause_reason?: string | null;
  now_serving?: string | null;
  base_service_seconds?: number | null;
  concurrent_servers?: number | null;
  entries: ApiQueueEntry[];
  updated_at: string;
}

export interface QueueData {
  queue: QueueView;
  tickets: TicketView[];
}

export class NoQueueError extends Error {}

/** Finds the clinic's first service point with an open/paused queue and loads it. */
export async function apiLoadDashboard(
  token: string,
  clinicId: string | null,
): Promise<QueueData> {
  if (!clinicId) throw new NoQueueError("Your account isn't assigned to a clinic.");
  const sps = await apiRequest<{ items: ApiServicePoint[] }>(
    `/v1/clinics/${encodeURIComponent(clinicId)}/service-points`,
    { token },
  );
  const sp = sps.items.find((s) => s.queue_id);
  if (!sp?.queue_id)
    throw new NoQueueError("No queue is open today. Open one for a service point first.");
  const q = await apiRequest<ApiQueue>(`/v1/queues/${encodeURIComponent(sp.queue_id)}`, {
    token,
  });
  return mapQueue(q, sp.name);
}

function mapQueue(q: ApiQueue, servicePointName: string): QueueData {
  const perPatientMin = Math.max(
    1,
    Math.round((q.base_service_seconds ?? 360) / 60 / (q.concurrent_servers ?? 1)),
  );
  const tickets = q.entries.map<TicketView>((e) => {
    const category = e.priority_category ?? "none";
    const confirmed = Boolean(e.staff_confirmed);
    return {
      id: e.ticket_id,
      display_number: e.display_number,
      sequence_no: Number(e.display_number.replace(/\D/g, "")) || 0,
      display_name: e.display_name || e.display_number,
      phone_last4: null,
      status: e.status,
      priority_category: confirmed ? category : "none",
      requested_category: category,
      staff_confirmed: confirmed,
      late_flag: e.late_flag,
      protected: Boolean(e.protected),
      position: e.status === "waiting" ? e.position : null,
      joined_at: e.joined_at ?? q.updated_at,
      called_at: null,
      counter_label: null,
      estimated_wait_min: e.status === "waiting" ? e.position * perPatientMin : null,
    };
  });
  return {
    queue: {
      id: q.id,
      service_point_name: servicePointName,
      display_prefix: q.display_prefix,
      status: q.status,
      pause_reason: q.pause_reason ?? null,
      now_serving: q.now_serving ?? null,
      updated_at: q.updated_at,
    },
    tickets,
  };
}

export function toErrorMessage(e: unknown): string {
  if (e instanceof ApiError || e instanceof NoQueueError) return e.message;
  if (e instanceof TypeError) return "Could not reach the SwiftQ server. Check your connection.";
  return e instanceof Error ? e.message : "";
}

export function apiCallNext(queueId: string, token: string, counterLabel?: string) {
  return apiRequest(`/v1/queues/${encodeURIComponent(queueId)}/call-next`, {
    method: "POST",
    token,
    body: counterLabel ? { counter_label: counterLabel } : undefined,
  });
}

export function apiPauseQueue(queueId: string, token: string, reason?: string) {
  return apiRequest(`/v1/queues/${encodeURIComponent(queueId)}/pause`, {
    method: "POST",
    token,
    body: reason ? { reason: reason.slice(0, 140) } : undefined,
  });
}

export function apiResumeQueue(queueId: string, token: string) {
  return apiRequest(`/v1/queues/${encodeURIComponent(queueId)}/resume`, {
    method: "POST",
    token,
  });
}

export function apiWalkIn(
  queueId: string,
  token: string,
  body: {
    display_name?: string;
    phone?: string;
    language: string;
    notification_channel?: "push" | "sms" | "whatsapp" | "email" | "none";
    priority_category?: PriorityCategory;
  },
) {
  return apiRequest(`/v1/queues/${encodeURIComponent(queueId)}/walk-ins`, {
    method: "POST",
    token,
    body,
    idempotent: true,
  });
}

/** serve → complete, no-show → no-show. The API has no recall / return-late endpoints. */
export async function apiTicketAction(
  ticketId: string,
  action: "serve" | "no-show" | "recall" | "return-late",
  token: string,
) {
  const id = encodeURIComponent(ticketId);
  if (action === "serve")
    return apiRequest(`/v1/tickets/${id}/complete`, { method: "POST", token });
  if (action === "no-show")
    return apiRequest(`/v1/tickets/${id}/no-show`, { method: "POST", token });
  if (action === "recall") return; // re-notification is not exposed by the API yet
  throw new Error("Returning a no-show to the queue isn't supported by the API yet.");
}

/** The API has no dismiss reason field, so dismissing is a cancel. */
export function apiDismissTicket(
  ticketId: string,
  token: string,
  _details: { reason: string; note?: string },
) {
  return apiRequest(`/v1/tickets/${encodeURIComponent(ticketId)}/cancel`, {
    method: "POST",
    token,
    idempotent: true,
  });
}

export function apiConfirmPriority(
  ticketId: string,
  token: string,
  body: { confirmed: boolean; category: PriorityCategory },
) {
  return apiRequest(`/v1/tickets/${encodeURIComponent(ticketId)}/confirm-priority`, {
    method: "POST",
    token,
    body,
  });
}

export function apiTransferTicket(ticketId: string, token: string, targetQueueId: string) {
  return apiRequest(`/v1/tickets/${encodeURIComponent(ticketId)}/transfer`, {
    method: "POST",
    token,
    body: { target_queue_id: targetQueueId },
  });
}
