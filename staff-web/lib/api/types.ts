export interface StaffUser {
  id: string;
  email: string;
  name: string;
  role: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: StaffUser;
}

export type TicketStatus =
  | "waiting"
  | "almost"
  | "called"
  | "in-progress"
  | "completed"
  | "cancelled"
  | "no-show";

export interface QueueTicket {
  id: string;
  number: string;
  patientName: string;
  status: TicketStatus;
  service: string;
  waitedMins: number;
}
