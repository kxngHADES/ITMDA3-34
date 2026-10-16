"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";

interface Props {
  open: boolean;
  title: string;
  onClose: () => void;
  wide?: boolean;
  children: ReactNode;
}

// Built on the browser's own <dialog>. showModal() gives us, for free: a focus trap,
// Escape to close, a dimmed backdrop, and everything behind it becoming inert.
export function Modal({ open, title, onClose, wide, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className={`modal ${wide ? "wide" : ""}`.trim()}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault(); // we close it ourselves so a busy form can refuse
        onClose();
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose(); // click on the backdrop
      }}
    >
      {/* Children only exist while open, so every form starts fresh each time. */}
      {open && (
        <div className="modal-body">
          <h2 id={titleId}>{title}</h2>
          {children}
        </div>
      )}
    </dialog>
  );
}
