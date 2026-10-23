"use client";

import type { ReactNode } from "react";
import { Button } from "./Button";
import { Modal } from "./Modal";

interface Props {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Asks "are you sure?" before anything that is hard to undo. The button names the action. */
export function ConfirmDialog({ open, title, children, confirmLabel, danger, busy, onConfirm, onCancel }: Props) {
  return (
    <Modal open={open} title={title} onClose={() => !busy && onCancel()}>
      <div className="stack">{children}</div>
      <div className="form-actions">
        <Button onClick={onCancel} disabled={busy}>Cancel</Button>
        <Button variant={danger ? "danger-solid" : "primary"} loading={busy} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}
