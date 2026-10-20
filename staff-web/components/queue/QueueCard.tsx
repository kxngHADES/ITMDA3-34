"use client";

import { useCallback } from "react";
import { Card } from "@/components/Card";
import { CloseIcon, SearchIcon } from "@/components/Icons";
import { TicketRow } from "@/components/queue/TicketRow";
import type { TicketView, PriorityCategory } from "@/lib/queue/types";

interface QueueCardProps {
  queueId: string;
  servicePointName: string;
  tickets: TicketView[];
  search: string;
  statusFilter: string;
  setSearch: (v: string) => void;
  setStatusFilter: (v: "active" | "waiting" | "called" | "done") => void;
  onServe: (t: TicketView) => void;
  onNoShow: (t: TicketView) => void;
  onRecall: (t: TicketView) => void;
  onReturnLate: (t: TicketView) => void;
  onDismiss: (t: TicketView, reason: string, note: string) => void;
  onPriority: (t: TicketView, confirmed: boolean, category: PriorityCategory) => void;
  onTransfer: (t: TicketView, targetQueueId: string) => void;
  busyIds: Set<string>;
}

export function QueueCard({
  queueId,
  servicePointName,
  tickets,
  search,
  statusFilter,
  setSearch,
  setStatusFilter,
  onServe,
  onNoShow,
  onRecall,
  onReturnLate,
  onDismiss,
  onPriority,
  onTransfer,
  busyIds,
}: QueueCardProps) {
  const active = tickets.filter(
    (t) => t.status === "waiting" || t.status === "called",
  );
  const served = tickets.filter((t) => t.status === "served").length;
  const noshow = tickets.filter((t) => t.status === "no_show").length;
  const waits = tickets
    .filter((t) => t.status === "waiting" && t.estimated_wait_min != null)
    .map((t) => t.estimated_wait_min as number);
  const avg = waits.length
    ? Math.round(waits.reduce((a, b) => a + b, 0) / waits.length)
    : 0;

  const filtered = useCallback((): TicketView[] => {
    const q = search.trim().toLowerCase();
    let list = [...tickets];
    if (statusFilter === "active")
      list = list.filter((t) => t.status === "waiting" || t.status === "called");
    else if (statusFilter === "waiting") list = list.filter((t) => t.status === "waiting");
    else if (statusFilter === "called") list = list.filter((t) => t.status === "called");
    else list = list.filter((t) => t.status === "served" || t.status === "no_show" || t.status === "cancelled");
    if (q.length >= 2) {
      const digits = q.replace(/\D/g, "");
      list = list.filter(
        (t) =>
          t.display_number.toLowerCase().includes(q) ||
          t.display_name.toLowerCase().includes(q) ||
          (t.phone_last4 ?? "").includes(digits),
      );
    }
    const rank = (t: TicketView) =>
      t.status === "called" ? -1 : (t.position ?? 999);
    return list.sort((a, b) => rank(a) - rank(b));
  }, [tickets, search, statusFilter]);

  return (
    <Card
      title="Today's queue"
      action={
        <span className="rounded-full bg-swiftq-bg px-2.5 py-0.5 text-[0.8rem] font-semibold text-swiftq-muted">
          {filtered().length} shown
        </span>
      }
    >
      <div className="mb-4 flex flex-wrap gap-5 border-b border-swiftq-border bg-swiftq-surface-subtle px-1 py-3">
        <div className="flex flex-col">
          <span className="text-[1.35rem] font-bold">{active.length}</span>
          <span className="text-[0.74rem] tracking-[0.05em] text-swiftq-muted uppercase">
            Active
          </span>
        </div>
        <div className="flex flex-col">
          <span className="text-[1.35rem] font-bold">{served}</span>
          <span className="text-[0.74rem] tracking-[0.05em] text-swiftq-muted uppercase">
            Served
          </span>
        </div>
        <div className="flex flex-col">
          <span className="text-[1.35rem] font-bold">{noshow}</span>
          <span className="text-[0.74rem] tracking-[0.05em] text-swiftq-muted uppercase">
            No-shows
          </span>
        </div>
        <div className="flex flex-col">
          <span className="text-[1.35rem] font-bold">{avg} min</span>
          <span className="text-[0.74rem] tracking-[0.05em] text-swiftq-muted uppercase">
            Avg wait
          </span>
        </div>
      </div>

      <div className="mb-3 flex flex-wrap gap-2">
        <div className="min-w-[220px] flex-1 relative">
          <label htmlFor="patient-search" className="sr-only">
            Search patients
          </label>
          <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-swiftq-muted/60" />
          <input
            id="patient-search"
            type="search"
            role="searchbox"
            aria-controls="ticket-list"
            aria-label="Search patients by ticket number, name, or last 4 phone digits"
            placeholder="Search: ticket (A014), name, or last 4 phone digits…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="min-h-[var(--swiftq-target)] w-full pl-9 pr-10 rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 text-[0.9rem] focus:border-swiftq-focus focus-visible:ring-2 focus-visible:ring-swiftq-focus focus-visible:ring-offset-2"
          />
          {search.trim().length > 0 && (
            <button
              type="button"
              aria-label="Clear search"
              onClick={() => setSearch("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 p-1 rounded-md text-swiftq-muted hover:text-swiftq-text focus-visible:ring-2 focus-visible:ring-swiftq-focus"
            >
              <CloseIcon />
            </button>
          )}
        </div>
        <div role="group" aria-label="Filter tickets by status" className="flex gap-1.5">
          {(["active", "waiting", "called", "done"] as const).map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setStatusFilter(f)}
              aria-pressed={statusFilter === f}
              className={`cursor-pointer rounded-full border px-3 py-1.5 text-[0.8rem] font-semibold capitalize transition-colors ${
                statusFilter === f
                  ? "border-swiftq-accent bg-swiftq-accent text-swiftq-on-accent"
                  : "border-swiftq-border bg-swiftq-surface text-swiftq-muted hover:bg-swiftq-hover"
              } focus-visible:ring-2 focus-visible:ring-swiftq-focus`}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      <ul
        id="ticket-list"
        role="list"
        aria-label="Queue tickets in effective order"
        className="m-0 list-none flex flex-col gap-2 p-0"
      >
        {filtered().length === 0 ? (
          <li role="status" className="px-5 py-9 text-center text-swiftq-muted">
            <h3 className="mb-1.5 text-[1rem] font-semibold text-swiftq-text">
              No tickets match
            </h3>
            <p className="m-0 text-[0.88rem]">
              {search.trim().length >= 2
                ? "Try a different ticket number, name, or phone digits."
                : "Nobody in this view right now."}
            </p>
          </li>
        ) : (
          filtered().map((t) => (
            <TicketRow
              key={t.id}
              ticket={t}
              statusFilter={statusFilter as "active" | "waiting" | "called" | "done"}
              busyIds={busyIds}
              onServe={() => onServe(t)}
              onNoShow={() => onNoShow(t)}
              onRecall={() => onRecall(t)}
              onReturnLate={() => onReturnLate(t)}
              onDismiss={(reason, note) => onDismiss(t, reason, note)}
              onPriority={(confirmed, category) => onPriority(t, confirmed, category)}
              onTransfer={(targetQueueId) => onTransfer(t, targetQueueId)}
            />
          ))
        )}
      </ul>

    </Card>
  );
}
