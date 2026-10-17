"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/Button";
import { TextField } from "@/components/TextField";
import type { MockTicket } from "@/lib/queue/mockQueue";
import type { PriorityCategory } from "@/lib/queue/mockQueue";

interface TicketRowProps {
  ticket: MockTicket;
  statusFilter: "active" | "waiting" | "called" | "done";
  busyIds: Set<string>;
  onServe: () => void;
  onNoShow: () => void;
  onRecall: () => void;
  onReturnLate: () => void;
  onDismiss: (reason: string, note: string) => void;
  onPriority: (confirmed: boolean, category: PriorityCategory) => void;
  onTransfer: (targetQueueId: string) => void;
}

export function TicketRow({
  ticket,
  statusFilter,
  busyIds,
  onServe,
  onNoShow,
  onRecall,
  onReturnLate,
  onDismiss,
  onPriority,
  onTransfer,
}: TicketRowProps) {
  const [dialog, setDialog] = useState<"none" | "priority" | "dismiss" | "transfer">("none");
  const { id } = ticket;
  const rowBusy = busyIds.has(id);

  const isActive = ticket.status === "waiting" || ticket.status === "called";
  const isDone =
    ticket.status === "served" || ticket.status === "no_show" || ticket.status === "cancelled";

  const showRow =
    (statusFilter === "active" && isActive) ||
    (statusFilter === "waiting" && ticket.status === "waiting") ||
    (statusFilter === "called" && ticket.status === "called") ||
    (statusFilter === "done" && isDone);

  if (!showRow) return null;

  return (
    <li
      key={id}
      role="listitem"
      className={`rounded-[var(--swiftq-radius)] border px-3 py-2.5 ${
        ticket.status === "called"
          ? "border-swiftq-accent/40 bg-swiftq-accent/5"
          : "border-swiftq-border bg-swiftq-surface"
      }`}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span
          aria-label={`Ticket ${ticket.display_number}`}
          className="min-w-[52px] text-[1rem] font-bold tabular-nums"
        >
          {ticket.display_number}
        </span>
        <span className="flex-1 basis-32 text-[0.9rem] font-medium">
          {ticket.display_name}
          <span className="ml-2 text-[0.75rem] font-normal text-swiftq-muted">
            {ticket.status === "waiting" && ticket.position ? `· #${ticket.position} in line` : ""}
            {ticket.status === "waiting" && ticket.estimated_wait_min != null
              ? ` · ~${ticket.estimated_wait_min} min`
              : ""}
            {ticket.status === "called" && ticket.counter_label ? ` · ${ticket.counter_label}` : ""}
            {ticket.late_flag ? " · late return" : ""}
          </span>
        </span>
        <span
          aria-live="polite"
          className={`rounded-full bg-swiftq-bg px-2 py-0.5 text-[0.72rem] font-semibold text-swiftq-muted capitalize ${
            ticket.status === "called"
              ? "bg-swiftq-accent/5 text-swiftq-accent-dk"
              : ""
          }`}
        >
          {ticket.status.replace("_", "-")}
        </span>
        {ticket.staff_confirmed && ticket.priority_category !== "none" ? (
          <span
            title={`Confirmed priority: ${ticket.priority_category}`}
            className="inline-flex items-center gap-1 rounded-full border border-swiftq-accent/30 bg-swiftq-accent/10 px-2 py-0.5 text-[0.72rem] font-semibold text-swiftq-accent-dk"
            aria-label="Confirmed priority"
          >
            <span aria-hidden="true">⚑</span> Priority
          </span>
        ) : ticket.requested_category !== "none" && !ticket.staff_confirmed ? (
          <span
            title={`Priority requested: ${ticket.requested_category} — awaiting confirmation`}
            className="inline-flex items-center gap-1 rounded-full border border-amber-600/30 bg-amber-50 px-2 py-0.5 text-[0.72rem] font-semibold text-swiftq-warn"
            aria-label="Priority request awaiting review"
          >
            <span aria-hidden="true">⚑</span> Review
          </span>
        ) : null}
      </div>

      {isActive && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {ticket.status === "waiting" &&
            ticket.requested_category !== "none" &&
            !ticket.staff_confirmed && (
              <Button
                size="sm"
                variant="primary"
                disabled={rowBusy || dialog !== "none"}
                onClick={() => setDialog("priority")}
                aria-label={`Confirm priority for ${ticket.display_number}`}
              >
                {dialog === "priority" ? "Saving…" : "Confirm priority"}
              </Button>
            )}
          {ticket.status === "called" && (
            <>
              <Button
                size="sm"
                variant="primary"
                disabled={rowBusy || dialog !== "none"}
                onClick={() => onServe()}
                aria-label={`Serve ticket ${ticket.display_number}`}
              >
                {busyIds.has(`serve:${id}`) ? "Serving…" : "Serve"}
              </Button>
              <Button
                size="sm"
                variant="default"
                disabled={rowBusy || dialog !== "none"}
                onClick={() => onNoShow()}
                aria-label={`Mark ${ticket.display_number} as no-show`}
              >
                {busyIds.has(`no-show:${id}`) ? "Saving…" : "No-show"}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                disabled={rowBusy || dialog !== "none"}
                onClick={() => onRecall()}
                aria-label={`Recall ticket ${ticket.display_number}`}
              >
                Recall
              </Button>
            </>
          )}
          {ticket.status === "no_show" && (
            <Button
              size="sm"
              variant="default"
              disabled={rowBusy || dialog !== "none"}
              onClick={() => onReturnLate()}
              aria-label={`Return ${ticket.display_number} to waiting`}
            >
              Return late → waiting
            </Button>
          )}
          <Button
            size="sm"
            variant="ghost"
            disabled={rowBusy || dialog !== "none"}
            onClick={() => setDialog("transfer")}
            aria-label={`Transfer ${ticket.display_number} to another queue`}
          >
            Transfer
          </Button>
          <Button
            size="sm"
            variant="danger"
            disabled={rowBusy || dialog !== "none"}
            onClick={() => setDialog("dismiss")}
            aria-label={`Dismiss ticket ${ticket.display_number}`}
          >
            Dismiss
          </Button>
        </div>
      )}

      {dialog === "priority" && (
        <PriorityDialogInline
          ticket={ticket}
          onClose={() => setDialog("none")}
          onSubmit={(confirmed, category) => {
            setDialog("none");
            onPriority(confirmed, category);
          }}
          busy={busyIds.has(`priority:${id}`)}
        />
      )}
      {dialog === "dismiss" && (
        <DismissDialogInline
          ticket={ticket}
          onClose={() => setDialog("none")}
          onSubmit={(reason, note) => {
            setDialog("none");
            onDismiss(reason, note);
          }}
          busy={busyIds.has(`dismiss:${id}`)}
        />
      )}
      {dialog === "transfer" && (
        <TransferDialogInline
          ticket={ticket}
          onClose={() => setDialog("none")}
          onSubmit={(targetQueueId) => {
            setDialog("none");
            onTransfer(targetQueueId);
          }}
          busy={busyIds.has(`transfer:${id}`)}
        />
      )}
    </li>
  );
}

/** Inline dialogs rendered inside the row so focus stays near the triggering button. */
function PriorityDialogInline({
  ticket,
  onClose,
  onSubmit,
  busy,
}: {
  ticket: MockTicket;
  onClose: () => void;
  onSubmit: (confirmed: boolean, category: PriorityCategory) => void;
  busy: boolean;
}) {
  const [category, setCategory] = useState<PriorityCategory>(
    ticket.requested_category !== "none" ? ticket.requested_category : "elderly",
  );
  const containerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const focusable = el.querySelector<HTMLElement>(
      'button:not([disabled]), [href], input, select, textarea',
    );
    focusable?.focus({ preventScroll: true });
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    el.addEventListener("keydown", onKey);
    return () => el.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div
      ref={containerRef}
      role="dialog"
      aria-modal="true"
      aria-label={`Confirm priority for ${ticket.display_number}`}
      className="mt-3 rounded-[var(--swiftq-radius-lg)] border border-swiftq-border bg-swiftq-surface p-4 shadow-swiftq"
    >
      <h3 className="m-0 mb-3 text-[1rem] font-bold">
        Priority request · {ticket.display_number}
      </h3>
      <p className="mb-3 text-[0.85rem] text-swiftq-muted">
        {ticket.display_name} requested{" "}
        <strong>{ticket.requested_category}</strong>. Confirming moves them up
        (bounded); rejecting keeps standard FIFO.
      </p>
      <div className="mb-3 flex flex-col gap-1.5">
        <label htmlFor={`pri-cat-${ticket.id}`} className="text-[0.84rem] font-semibold">
          Category (correct if needed)
        </label>
        <select
          id={`pri-cat-${ticket.id}`}
          value={category}
          onChange={(e) => setCategory(e.target.value as PriorityCategory)}
          className="min-h-[var(--swiftq-target)] rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 text-[0.95rem]"
        >
          <option value="elderly">Elderly</option>
          <option value="pregnant">Pregnant</option>
          <option value="disability">Disability</option>
          <option value="urgent">Urgent</option>
        </select>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row sm:gap-2">
        <Button
          variant="primary"
          className="flex-1"
          onClick={() => onSubmit(true, category)}
          disabled={busy}
        >
          {busy ? "Saving…" : "Confirm priority"}
        </Button>
        <div className="flex gap-2">
          <Button
            variant="danger"
            className="flex-1"
            onClick={() => onSubmit(false, "none")}
            disabled={busy}
          >
            Reject
          </Button>
          <Button variant="default" className="flex-1" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
        </div>
      </div>
    </div>
  );
}

function DismissDialogInline({
  ticket,
  onClose,
  onSubmit,
  busy,
}: {
  ticket: MockTicket;
  onClose: () => void;
  onSubmit: (reason: string, note: string) => void;
  busy: boolean;
}) {
  const [reason, setReason] = useState("patient_left");
  const [note, setNote] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const focusable = el.querySelector<HTMLElement>(
      'button:not([disabled]), [href], input, select, textarea',
    );
    focusable?.focus({ preventScroll: true });
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    el.addEventListener("keydown", onKey);
    return () => el.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div
      ref={containerRef}
      role="dialog"
      aria-modal="true"
      aria-label={`Dismiss ticket ${ticket.display_number}`}
      className="mt-3 rounded-[var(--swiftq-radius-lg)] border border-swiftq-border bg-swiftq-surface p-4 shadow-swiftq"
    >
      <h3 className="m-0 mb-3 text-[1rem] font-bold">Dismiss {ticket.display_number}</h3>
      <p className="mb-3 text-[0.85rem] text-swiftq-muted">
        Removes a waiting or called ticket. This is terminal — the patient is
        notified where possible.
      </p>
      <div className="mb-3 flex flex-col gap-1.5">
        <label htmlFor={`dismiss-reason-${ticket.id}`} className="text-[0.84rem] font-semibold">
          Reason
        </label>
        <select
          id={`dismiss-reason-${ticket.id}`}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          className="min-h-[var(--swiftq-target)] rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 text-[0.95rem]"
        >
          <option value="duplicate">Duplicate</option>
          <option value="wrong_service_point">Wrong service point</option>
          <option value="patient_left">Patient left</option>
          <option value="expired">Expired</option>
          <option value="other">Other</option>
        </select>
      </div>
      <TextField
        id={`dismiss-note-${ticket.id}`}
        label="Note (optional)"
        placeholder="Max 140 characters"
        value={note}
        maxLength={140}
        onChange={(e) => setNote(e.target.value)}
      />
      <div className="mt-4 flex gap-2 sm:flex-row">
        <Button variant="default" className="flex-1" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant="danger"
          className="flex-1"
          onClick={() => onSubmit(reason, note)}
          disabled={busy}
        >
          {busy ? "Dismissing…" : "Dismiss ticket"}
        </Button>
      </div>
    </div>
  );
}

function TransferDialogInline({
  ticket,
  onClose,
  onSubmit,
  busy,
}: {
  ticket: MockTicket;
  onClose: () => void;
  onSubmit: (targetQueueId: string) => void;
  busy: boolean;
}) {
  const [target, setTarget] = useState("q_soshanguve_b_vitals");
  const containerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const focusable = el.querySelector<HTMLElement>(
      'button:not([disabled]), [href], input, select, textarea',
    );
    focusable?.focus({ preventScroll: true });
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    el.addEventListener("keydown", onKey);
    return () => el.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div
      ref={containerRef}
      role="dialog"
      aria-modal="true"
      aria-label={`Transfer ticket ${ticket.display_number}`}
      className="mt-3 rounded-[var(--swiftq-radius-lg)] border border-swiftq-border bg-swiftq-surface p-4 shadow-swiftq"
    >
      <h3 className="m-0 mb-3 text-[1rem] font-bold">
        Transfer {ticket.display_number}
      </h3>
      <p className="mb-3 text-[0.85rem] text-swiftq-muted">
        Move this waiting ticket to another service point at the same clinic.
        Original join time is kept.
      </p>
      <div className="mb-4 flex flex-col gap-1.5">
        <label htmlFor={`transfer-target-${ticket.id}`} className="text-[0.84rem] font-semibold">
          Target queue
        </label>
        <select
          id={`transfer-target-${ticket.id}`}
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          className="min-h-[var(--swiftq-target)] rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 text-[0.95rem]"
        >
          <option value="q_soshanguve_b_general">General consultation (current)</option>
          <option value="q_soshanguve_b_vitals">Vitals / Triage</option>
          <option value="q_soshanguve_b_chronic">Chronic medication</option>
        </select>
      </div>
      <div className="flex gap-2 sm:flex-row">
        <Button variant="default" className="flex-1" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant="primary" className="flex-1" onClick={() => onSubmit(target)} disabled={busy}>
          {busy ? "Transferring…" : "Transfer"}
        </Button>
      </div>
    </div>
  );
}
