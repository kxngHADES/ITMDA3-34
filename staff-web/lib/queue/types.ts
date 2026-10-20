export type PriorityCategory = "none" | "elderly" | "pregnant" | "disability" | "urgent";
export type TicketStatus = "waiting" | "called" | "served" | "no_show" | "cancelled";
export type QueueStatus = "open" | "paused" | "closed";

export interface QueueView {
  id: string;
  service_point_name: string;
  display_prefix: string;
  status: QueueStatus;
  pause_reason: string | null;
  now_serving: string | null;
  updated_at: string;
}

export interface TicketView {
  id: string;
  display_number: string;
  sequence_no: number;
  display_name: string;
  phone_last4: string | null;
  status: TicketStatus;
  priority_category: PriorityCategory;
  requested_category: PriorityCategory;
  staff_confirmed: boolean;
  late_flag: boolean;
  protected: boolean;
  position: number | null;
  joined_at: string;
  called_at: string | null;
  counter_label: string | null;
  estimated_wait_min: number | null;
}
