"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "./Button";
import { TextField } from "./TextField";
import type { TicketView, PriorityCategory } from "@/lib/queue/types";

/**
 * Shared dialog behavior: traps focus inside the dialog, returns focus to the
 * trigger on close, and closes on Escape. Renders an inline (non-modal) panel
 * so focus stays near the triggering row button.
 */
function useDialogFocus() {
  const containerRef = useRef<HTMLDivElement>(null);
  const firstFocusableRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    // Move focus to the first focusable element once mounted.
    const focusable = el.querySelector<HTMLElement>(
      'button:not([disabled]), [href], input, select, textarea',
    );
    if (focusable && firstFocusableRef.current === null) {
      firstFocusableRef.current = focusable;
      focusable.focus({ preventScroll: true });
    }

    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        // Bubble up: parent dialog wrapper calls onClose.
        const close = el.closest<HTMLElement>("[data-dialog-close]");
        if (close) close.click();
      }
    };
    el.addEventListener("keydown", onKey);
    return () => el.removeEventListener("keydown", onKey);
  }, []);

  return { containerRef };
}

export function Overlay({ children, onClose, label }: {
  children: React.ReactNode;
  onClose: () => void;
  label: string;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={label}
      data-dialog-close
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
    >
      <div className="w-full max-w-[440px] rounded-[var(--swiftq-radius-lg)] border border-swiftq-border bg-swiftq-surface p-5 shadow-swiftq">
        {children}
      </div>
    </div>
  );
}

export function WalkInDialog({ onClose, onSubmit, busy }: {
  onClose: () => void;
  onSubmit: (v: { name: string; phone: string; priority: PriorityCategory }) => void;
  busy: boolean;
}) {
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [priority, setPriority] = useState<PriorityCategory>("none");
  const [error, setError] = useState("");

  function submit() {
    if (!name.trim()) {
      setError("Enter the patient's first name or nickname.");
      return;
    }
    if (phone && !/^\+27[0-9]{9}$/.test(phone.trim())) {
      setError("Phone must look like +27821234567 (or leave it blank).");
      return;
    }
    onSubmit({ name: name.trim(), phone: phone.trim(), priority });
  }

  return (
    <Overlay onClose={onClose} label="Add walk-in patient">
      <h2 className="m-0 mb-1 text-[1.05rem] font-bold">Add walk-in</h2>
      <p className="mt-0 mb-4 text-[0.85rem] text-swiftq-muted">
        Staff-assisted join. The ticket is confirmed immediately.
      </p>
      <TextField
        id="walkin-name"
        label="First name / nickname"
        placeholder="e.g. Lerato"
        value={name}
        maxLength={40}
        onChange={(e) => setName(e.target.value)}
        error={error && !name.trim() ? error : undefined}
      />
      <TextField
        id="walkin-phone"
        label="Phone (optional, for SMS alerts)"
        placeholder="+27821234567"
        value={phone}
        inputMode="tel"
        onChange={(e) => setPhone(e.target.value)}
      />
      <div className="mb-4 flex flex-col gap-1.5">
        <label htmlFor="walkin-priority" className="text-[0.84rem] font-semibold">
          Priority category
        </label>
        <select
          id="walkin-priority"
          value={priority}
          onChange={(e) => setPriority(e.target.value as PriorityCategory)}
          className="min-h-[var(--swiftq-target)] rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 text-[0.95rem]"
        >
          <option value="none">Standard (no priority)</option>
          <option value="elderly">Elderly</option>
          <option value="pregnant">Pregnant</option>
          <option value="disability">Disability</option>
          <option value="urgent">Urgent</option>
        </select>
      </div>
      {error && name.trim() && (
        <p role="alert" className="mb-3 text-[0.84rem] font-semibold text-swiftq-danger">
          {error}
        </p>
      )}
      <div className="flex gap-2">
        <Button variant="default" className="flex-1" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant="primary" className="flex-1" onClick={submit} disabled={busy}>
          {busy ? "Adding…" : "Add to queue"}
        </Button>
      </div>
    </Overlay>
  );
}

export function PriorityDialog({ ticket, onClose, onSubmit, busy }: {
  ticket: TicketView;
  onClose: () => void;
  onSubmit: (confirmed: boolean, category: PriorityCategory) => void;
  busy: boolean;
}) {
  const [category, setCategory] = useState<PriorityCategory>(
    ticket.requested_category !== "none" ? ticket.requested_category : "elderly",
  );
  return (
    <Overlay onClose={onClose} label={`Confirm priority for ${ticket.display_number}`}>
      <h2 className="m-0 mb-1 text-[1.05rem] font-bold">
        Priority request · {ticket.display_number}
      </h2>
      <p className="mt-0 mb-4 text-[0.85rem] text-swiftq-muted">
        {ticket.display_name} requested{" "}
        <strong>{ticket.requested_category}</strong>. Confirming moves them up
        (bounded); rejecting keeps standard FIFO.
      </p>
      <div className="mb-4 flex flex-col gap-1.5">
        <label htmlFor="pri-cat" className="text-[0.84rem] font-semibold">
          Category (correct if needed)
        </label>
        <select
          id="pri-cat"
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
        <Button variant="primary" onClick={() => onSubmit(true, category)} disabled={busy}>
          {busy ? "Saving…" : "Confirm priority"}
        </Button>
        <div className="flex gap-2">
          <Button variant="danger" className="flex-1" onClick={() => onSubmit(false, "none")} disabled={busy}>
            Reject
          </Button>
          <Button variant="default" className="flex-1" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
        </div>
      </div>
    </Overlay>
  );
}

export function DismissDialog({ ticket, onClose, onSubmit, busy }: {
  ticket: TicketView;
  onClose: () => void;
  onSubmit: (reason: string, note: string) => void;
  busy: boolean;
}) {
  const [reason, setReason] = useState("patient_left");
  const [note, setNote] = useState("");
  return (
    <Overlay onClose={onClose} label={`Dismiss ticket ${ticket.display_number}`}>
      <h2 className="m-0 mb-1 text-[1.05rem] font-bold">
        Dismiss {ticket.display_number}
      </h2>
      <p className="mt-0 mb-4 text-[0.85rem] text-swiftq-muted">
        Removes a waiting or called ticket. This is terminal — the patient is
        notified where possible.
      </p>
      <div className="mb-3 flex flex-col gap-1.5">
        <label htmlFor="dismiss-reason" className="text-[0.84rem] font-semibold">
          Reason
        </label>
        <select
          id="dismiss-reason"
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
        id="dismiss-note"
        label="Note (optional)"
        placeholder="Max 140 characters"
        value={note}
        maxLength={140}
        onChange={(e) => setNote(e.target.value)}
      />
      <div className="flex gap-2">
        <Button variant="default" className="flex-1" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant="danger" className="flex-1" onClick={() => onSubmit(reason, note)} disabled={busy}>
          {busy ? "Dismissing…" : "Dismiss ticket"}
        </Button>
      </div>
    </Overlay>
  );
}

export function TransferDialog({ ticket, onClose, onSubmit, busy }: {
  ticket: TicketView;
  onClose: () => void;
  onSubmit: (targetQueueId: string) => void;
  busy: boolean;
}) {
  const [target, setTarget] = useState("q_soshanguve_b_vitals");
  return (
    <Overlay onClose={onClose} label={`Transfer ticket ${ticket.display_number}`}>
      <h2 className="m-0 mb-1 text-[1.05rem] font-bold">
        Transfer {ticket.display_number}
      </h2>
      <p className="mt-0 mb-4 text-[0.85rem] text-swiftq-muted">
        Move this waiting ticket to another service point at the same clinic.
        Original join time is kept.
      </p>
      <div className="mb-4 flex flex-col gap-1.5">
        <label htmlFor="transfer-target" className="text-[0.84rem] font-semibold">
          Target queue
        </label>
        <select
          id="transfer-target"
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          className="min-h-[var(--swiftq-target)] rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 text-[0.95rem]"
        >
          <option value="q_soshanguve_b_general">General consultation (current)</option>
          <option value="q_soshanguve_b_vitals">Vitals / Triage</option>
          <option value="q_soshanguve_b_chronic">Chronic medication</option>
        </select>
      </div>
      <div className="flex gap-2">
        <Button variant="default" className="flex-1" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant="primary" className="flex-1" onClick={() => onSubmit(target)} disabled={busy}>
          {busy ? "Transferring…" : "Transfer"}
        </Button>
      </div>
    </Overlay>
  );
}

export function PauseDialog({ paused, onClose, onSubmit, busy }: {
  paused: boolean;
  onClose: () => void;
  onSubmit: (reason: string) => void;
  busy: boolean;
}) {
  const [reason, setReason] = useState("");
  return (
    <Overlay onClose={onClose} label={paused ? "Resume queue" : "Pause queue"}>
      <h2 className="m-0 mb-1 text-[1.05rem] font-bold">
        {paused ? "Resume queue?" : "Pause queue?"}
      </h2>
      <p className="mt-0 mb-4 text-[0.85rem] text-swiftq-muted">
        {paused
          ? "Patients can join again and call-next is unblocked."
          : "While paused, joins are rejected and call-next is blocked."}
      </p>
      {!paused && (
        <TextField
          id="pause-reason"
          label="Reason (shown to patients)"
          placeholder="e.g. Staff tea break, back at 10:30"
          value={reason}
          maxLength={140}
          onChange={(e) => setReason(e.target.value)}
        />
      )}
      <div className="flex gap-2">
        <Button variant="default" className="flex-1" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant={paused ? "primary" : "danger"}
          className="flex-1"
          onClick={() => onSubmit(reason)}
          disabled={busy}
        >
          {busy ? "Saving…" : paused ? "Resume" : "Pause"}
        </Button>
      </div>
    </Overlay>
  );
}
