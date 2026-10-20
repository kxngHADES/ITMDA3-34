"use client";

import { ApiError } from "@/lib/api/client";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { CloseIcon } from "@/components/Icons";
import { TopBar } from "@/components/TopBar";
import { SyncBadge, useSyncHealth } from "@/components/SyncHealth";
import {
  DismissDialog,
  PauseDialog,
  PriorityDialog,
  TransferDialog,
  WalkInDialog,
} from "@/components/QueueDialogs";
import { QueueCard } from "@/components/queue/QueueCard";
import type { StaffSession } from "@/lib/api/auth";
import { clearSession, getSession } from "@/lib/auth/session";
import {
  NoQueueError,
  apiCallNext,
  apiConfirmPriority,
  apiDismissTicket,
  apiPauseQueue,
  apiResumeQueue,
  apiTicketAction,
  apiTransferTicket,
  apiLoadDashboard,
  apiWalkIn,
  toErrorMessage,
} from "@/lib/api/queue";
import type { PriorityCategory, QueueView, TicketView } from "@/lib/queue/types";

type DialogState =
  | { kind: "none" }
  | { kind: "walkin" }
  | { kind: "pause" }
  | { kind: "priority"; ticket: TicketView }
  | { kind: "dismiss"; ticket: TicketView }
  | { kind: "transfer"; ticket: TicketView };

function headOfQueue(tickets: TicketView[]): TicketView | undefined {
  return tickets
    .filter((t) => t.status === "waiting")
    .sort((a, b) => (a.position ?? 99) - (b.position ?? 99))[0];
}

export default function Home() {
  const router = useRouter();
  const [session, setSession] = useState<StaffSession | null>(null);
  const [ready, setReady] = useState(false);
  const [queue, setQueue] = useState<QueueView | null>(null);
  const [tickets, setTickets] = useState<TicketView[]>([]);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"active" | "waiting" | "called" | "done">("active");
  const [dialog, setDialog] = useState<DialogState>({ kind: "none" });
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set());
  const [notice, setNotice] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const [loadError, setLoadError] = useState<string | null>(null);

  const load = useCallback(
    async (sess: StaffSession) => {
      try {
        const data = await apiLoadDashboard(sess.token, sess.clinicId);
        setQueue(data.queue);
        setTickets(data.tickets);
        setLoadError(null);
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
          clearSession();
          router.replace("/login");
          return;
        }
        if (e instanceof NoQueueError) {
          setQueue(null);
          setTickets([]);
          setLoadError(e.message);
          return;
        }
        throw e;
      }
    },
    [router],
  );

  useEffect(() => {
    const stored = getSession();
    if (!stored) {
      router.replace("/login");
      return;
    }
    setSession(stored);
    load(stored)
      .catch((e) => setLoadError(toErrorMessage(e) || "Could not load the queue."))
      .finally(() => setReady(true));
  }, [router, load]);

  const refresh = useCallback(async () => {
    if (session) await load(session);
  }, [session, load]);

  const health = useSyncHealth({ onSync: refresh, isMockMode: false });

  function fail(e: unknown, fallback: string) {
    setNotice({ kind: "err", text: toErrorMessage(e) || fallback });
  }

  function ok(text: string) {
    setNotice({ kind: "ok", text });
  }

  const isBusy = (key: string) => busyIds.has(key);
  const setBusy = (key: string | null) => {
    setBusyIds((prev) => {
      const next = new Set(prev);
      if (key === null) next.clear();
      else next.add(key);
      return next;
    });
  };

  // ── Queue actions ── each call hits the API, then reloads the queue from it.
  async function run(
    key: string,
    failText: string,
    action: (sess: StaffSession, q: QueueView) => Promise<string>,
    closeDialog = false,
  ) {
    if (!queue || !session) return;
    setBusy(key);
    try {
      const message = await action(session, queue);
      await load(session);
      ok(message);
      if (closeDialog) setDialog({ kind: "none" });
    } catch (e) {
      fail(e, failText);
    } finally {
      setBusy(null);
    }
  }

  async function handleCallNext() {
    if (!queue) return;
    if (queue.status === "paused") {
      setNotice({
        kind: "err",
        text: "This queue is paused. Resume it before calling the next patient.",
      });
      return;
    }
    if (!headOfQueue(tickets)) {
      setNotice({ kind: "err", text: "Nobody is waiting — the queue is empty." });
      return;
    }
    await run("call-next", "Could not call the next patient.", async (sess, q) => {
      await apiCallNext(q.id, sess.token, "Room 1");
      return "Next patient called to Room 1.";
    });
  }

  async function handleStatusAction(
    ticket: TicketView,
    action: "serve" | "no-show" | "recall" | "return-late",
  ) {
    await run(`${action}:${ticket.id}`, "Action failed.", async (sess) => {
      await apiTicketAction(ticket.id, action, sess.token);
      const label =
        action === "serve"
          ? "served"
          : action === "no-show"
            ? "marked as no-show"
            : action === "recall"
              ? "recalled"
              : "returned to the queue (late)";
      return `${ticket.display_number} ${label}.`;
    });
  }

  async function handleDismiss(ticket: TicketView, reason: string, note: string) {
    await run(
      `dismiss:${ticket.id}`,
      "Could not dismiss the ticket.",
      async (sess) => {
        await apiDismissTicket(ticket.id, sess.token, {
          reason,
          note: note || undefined,
        });
        return `${ticket.display_number} dismissed (${reason}).`;
      },
      true,
    );
  }

  async function handlePriority(
    ticket: TicketView,
    confirmed: boolean,
    category: PriorityCategory,
  ) {
    await run(
      `priority:${ticket.id}`,
      "Could not save the priority decision.",
      async (sess) => {
        await apiConfirmPriority(ticket.id, sess.token, { confirmed, category });
        return confirmed
          ? `${ticket.display_number} confirmed as ${category} priority.`
          : `${ticket.display_number} priority request rejected — standard FIFO.`;
      },
      true,
    );
  }

  async function handleWalkIn(v: { name: string; phone: string; priority: PriorityCategory }) {
    await run(
      "walkin",
      "Could not add the walk-in.",
      async (sess, q) => {
        await apiWalkIn(q.id, sess.token, {
          display_name: v.name,
          phone: v.phone || undefined,
          language: "en",
          notification_channel: v.phone ? "sms" : "none",
          priority_category: v.priority,
        });
        return `${v.name} added to the queue.`;
      },
      true,
    );
  }

  async function handlePauseResume(reason: string) {
    const pausing = queue?.status !== "paused";
    await run(
      "pause",
      "Could not update the queue.",
      async (sess, q) => {
        if (pausing) await apiPauseQueue(q.id, sess.token, reason || undefined);
        else await apiResumeQueue(q.id, sess.token);
        return pausing ? "Queue paused — joins and call-next are blocked." : "Queue resumed.";
      },
      true,
    );
  }

  async function handleTransfer(ticket: TicketView, targetQueueId: string) {
    await run(
      `transfer:${ticket.id}`,
      "Could not transfer the ticket.",
      async (sess) => {
        await apiTransferTicket(ticket.id, sess.token, targetQueueId);
        return `${ticket.display_number} transferred to ${targetQueueId}. Join time kept.`;
      },
      true,
    );
  }

  function handleLogout() {
    clearSession();
    router.replace("/login");
  }

  // ── Derived view ──
  const nextUp = useMemo(() => headOfQueue(tickets), [tickets]);
  const calledNow = useMemo(() => tickets.find((t) => t.status === "called"), [tickets]);
  const paused = queue?.status === "paused";

  if (ready && session && !queue) {
    return (
      <div className="flex min-h-full flex-col bg-swiftq-bg">
        <TopBar userName={session.name} role={session.role} onLogout={handleLogout} />
        <main className="mx-auto w-full max-w-[640px] flex-1 px-5 pt-10">
          <Card title="Queue unavailable">
            <p role="alert" className="mb-3.5 text-swiftq-muted">
              {loadError ?? "Could not load the queue."}
            </p>
            <Button variant="primary" onClick={() => void refresh().catch((e) => fail(e, "Retry failed."))}>
              Try again
            </Button>
          </Card>
        </main>
      </div>
    );
  }

  if (!ready || !session || !queue) {
    return (
      <div className="flex min-h-full items-center justify-center bg-swiftq-bg">
        <p className="text-swiftq-muted">Loading…</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-full flex-col bg-swiftq-bg">
      <TopBar userName={session.name} role={session.role} onLogout={handleLogout} />

      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 pt-6 pb-16">
        <div className="mb-4 flex flex-wrap items-start gap-4">
          <div className="flex-1 basis-80">
            <h1 className="m-0 mb-1 text-[1.5rem] font-bold">Live Queue Dashboard</h1>
            <p className="m-0 text-[0.92rem] text-swiftq-muted">
              Service point: {queue.service_point_name} ·{" "}
              <span
                className={paused ? "font-semibold text-swiftq-warn" : "font-semibold text-swiftq-ok"}
              >
                {paused
                  ? `Paused${queue.pause_reason ? ` — ${queue.pause_reason}` : ""}`
                  : "Open"}
              </span>
              {queue.now_serving ? ` · Now serving ${queue.now_serving}` : ""}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2.5">
            <Button variant="default" size="sm" onClick={() => void refresh()}>
              Refresh
            </Button>
            <Button variant="default" size="sm" onClick={() => setDialog({ kind: "walkin" })}>
              + Walk-in
            </Button>
            <Button
              variant={paused ? "primary" : "default"}
              size="sm"
              onClick={() => setDialog({ kind: "pause" })}
            >
              {paused ? "Resume queue" : "Pause queue"}
            </Button>
          </div>
        </div>

        <div className="mb-4">
          <SyncBadge health={health} />
        </div>

        {notice && (
          <div
            role={notice.kind === "err" ? "alert" : "status"}
            aria-live="polite"
            className={`mb-4 rounded-[var(--swiftq-radius)] border px-3.5 py-2.5 text-[0.88rem] font-medium ${
              notice.kind === "err"
                ? "border-swiftq-danger-border bg-swiftq-danger-bg text-swiftq-danger-ink"
                : "border-green-700/25 bg-green-50 text-green-900"
            }`}
          >
            {notice.text}
            <button
              type="button"
              onClick={() => setNotice(null)}
              aria-label="Dismiss message"
              className="float-right cursor-pointer font-bold opacity-60 hover:opacity-100 focus-visible:ring-2 focus-visible:ring-swiftq-focus"
            >
              <CloseIcon />
            </button>
          </div>
        )}

        <div className="grid gap-[18px] lg:grid-cols-[minmax(0,2fr)_minmax(300px,1fr)]">
          <QueueCard
            queueId={queue.id}
            servicePointName={queue.service_point_name}
            tickets={tickets}
            search={search}
            statusFilter={statusFilter}
            setSearch={setSearch}
            setStatusFilter={setStatusFilter}
            onServe={(t) => void handleStatusAction(t, "serve")}
            onNoShow={(t) => void handleStatusAction(t, "no-show")}
            onRecall={(t) => void handleStatusAction(t, "recall")}
            onReturnLate={(t) => void handleStatusAction(t, "return-late")}
            onDismiss={handleDismiss}
            onPriority={handlePriority}
            onTransfer={handleTransfer}
            busyIds={busyIds}
          />

          <div className="flex flex-col gap-[18px]">
            <Card title="Call next patient">
              {calledNow && (
                <p className="mb-2 rounded-[var(--swiftq-radius)] border border-swiftq-accent/30 bg-swiftq-accent/5 px-3 py-2 text-[0.85rem]">
                  <strong>Now serving {calledNow.display_number}</strong> — {calledNow.display_name}
                  {calledNow.counter_label ? ` · ${calledNow.counter_label}` : ""}.
                  Serve it or mark no-show before calling the next patient.
                </p>
              )}
              {paused ? (
                <p className="mb-3.5 text-swiftq-warn">
                  Queue is paused — call-next is blocked until you resume.
                </p>
              ) : nextUp ? (
                <p className="mb-3.5 text-swiftq-muted">
                  Next up:{" "}
                  <strong className="text-swiftq-text">{nextUp.display_number}</strong>{" "}
                  {nextUp.display_name}
                  {nextUp.staff_confirmed ? " (priority)" : ""} ·
                  ~{nextUp.estimated_wait_min} min
                </p>
              ) : (
                <p className="mb-3.5 text-swiftq-muted">No patients waiting.</p>
              )}
              <Button
                variant="primary"
                className="w-full"
                disabled={!nextUp || paused || isBusy("call-next")}
                onClick={() => void handleCallNext()}
              >
                {isBusy("call-next") ? "Calling…" : "Call next"}
              </Button>
              {calledNow && (
                <div className="mt-2 flex gap-2">
                  <Button
                    variant="default"
                    size="sm"
                    className="flex-1"
                    disabled={isBusy(`serve:${calledNow.id}`)}
                    onClick={() => void handleStatusAction(calledNow, "serve")}
                  >
                    {isBusy(`serve:${calledNow.id}`) ? "Serving…" : `Serve ${calledNow.display_number}`}
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    className="flex-1"
                    disabled={isBusy(`no-show:${calledNow.id}`)}
                    onClick={() => void handleStatusAction(calledNow, "no-show")}
                  >
                    {isBusy(`no-show:${calledNow.id}`) ? "Saving…" : "No-show"}
                  </Button>
                </div>
              )}
            </Card>

            <Card
              title="Sync health"
              action={
                <button
                  type="button"
                  onClick={() => void refresh()}
                  className="cursor-pointer rounded-full bg-swiftq-bg px-2.5 py-0.5 text-[0.8rem] font-semibold text-swiftq-muted focus-visible:ring-2 focus-visible:ring-swiftq-focus"
                >
                  Check now
                </button>
              }
            >
              <SyncBadge health={health} />
              <p className="mt-2 mb-0 text-[0.8rem] text-swiftq-muted">
                Connected to the live API.{" "}
                Auto-refresh every 15 s.
              </p>
            </Card>

            <Card title="Add walk-in">
              <p className="mb-3.5 text-[0.88rem] text-swiftq-muted">
                Register a patient without the app — e.g. no smartphone or assisted join.
              </p>
              <Button variant="default" className="w-full" onClick={() => setDialog({ kind: "walkin" })}>
                + Manual walk-in
              </Button>
            </Card>
          </div>
        </div>
      </main>

      {dialog.kind === "walkin" && (
        <WalkInDialog
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(v) => void handleWalkIn(v)}
          busy={isBusy("walkin")}
        />
      )}
      {dialog.kind === "priority" && (
        <PriorityDialog
          ticket={dialog.ticket}
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(c, cat) => void handlePriority(dialog.ticket, c, cat)}
          busy={isBusy(`priority:${dialog.ticket.id}`)}
        />
      )}
      {dialog.kind === "dismiss" && (
        <DismissDialog
          ticket={dialog.ticket}
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(r, n) => void handleDismiss(dialog.ticket, r, n)}
          busy={isBusy(`dismiss:${dialog.ticket.id}`)}
        />
      )}
      {dialog.kind === "transfer" && (
        <TransferDialog
          ticket={dialog.ticket}
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(q) => void handleTransfer(dialog.ticket, q)}
          busy={isBusy(`transfer:${dialog.ticket.id}`)}
        />
      )}
      {dialog.kind === "pause" && (
        <PauseDialog
          paused={paused}
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(r) => void handlePauseResume(r)}
          busy={isBusy("pause")}
        />
      )}
    </div>
  );
}
