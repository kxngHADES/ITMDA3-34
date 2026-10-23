import type { ReactNode } from "react";
import type { SyncLogStatus } from "@/lib/api/types";

export type Tone = "info" | "success" | "warning" | "progress" | "error" | "neutral";

/** The words carry the meaning. Colour only backs them up. */
export function Chip({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <span className={`chip chip-${tone}`}>{children}</span>;
}

// Proposed mapping of sync log statuses to the shared status colours. Agree it with the group.
export const SYNC_STATUS: Record<SyncLogStatus, { label: string; tone: Tone }> = {
  applied: { label: "Applied", tone: "success" },
  resolved: { label: "Resolved", tone: "success" },
  duplicate: { label: "Duplicate", tone: "neutral" },
  conflict_resolved: { label: "Auto-resolved", tone: "progress" },
  conflict_pending: { label: "Needs review", tone: "warning" },
  rejected: { label: "Rejected", tone: "error" },
};

export function SyncStatusChip({ status }: { status: SyncLogStatus }) {
  const s = SYNC_STATUS[status] ?? { label: status, tone: "neutral" as Tone };
  return <Chip tone={s.tone}>{s.label}</Chip>;
}
