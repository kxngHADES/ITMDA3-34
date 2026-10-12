"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { TextField } from "@/components/TextField";
import { TopBar } from "@/components/TopBar";
import { SyncBadge, useSyncHealth } from "@/components/SyncHealth";
import {
  DismissDialog,
  PauseDialog,
  PriorityDialog,
  TransferDialog,
  WalkInDialog,
} from "@/components/QueueDialogs";
import type { StaffSession } from "@/lib/api/auth";
import { clearSession, getSession } from "@/lib/auth/session";
import {
  apiCallNext,
  apiConfirmPriority,
  apiDismissTicket,
  apiPauseQueue,
  apiResumeQueue,
  apiTicketAction,
  apiTransferTicket,
  apiWalkIn,
  isMockToken,
  toErrorMessage,
} from "@/lib/api/queue";
import {
  loadSnapshot,
  persistSnapshot,
  recomputePositions,
  resetSnapshot,
  type MockQueue,
  type MockTicket,
  type PriorityCategory,
} from "@/lib/queue/mockQueue";

type DialogState =
  | { kind: "none" }
  | { kind: "walkin" }
  | { kind: "priority"; ticket: MockTicket }
  | { kind: "dismiss"; ticket: MockTicket }
  | { kind: "transfer"; ticket: MockTicket }
  | { kind: "pause" };

function headOfQueue(tickets: MockTicket[]): MockTicket | undefined {
  return tickets
    .filter((t) => t.status === "waiting")
    .sort((a, b) => (a.position ?? 99) - (b.position ?? 99))[0];
}

function PriorityFlag({ ticket }: { ticket: MockTicket }) {
  const confirmed = ticket.staff_confirmed && ticket.priority_category !== "none";
  const requested =
    !confirmed && ticket.requested_category !== "none" && ticket.status === "waiting";
  if (!confirmed && !requested) return null;
  return (
    <span
      title={
        confirmed
          ? `Confirmed priority: ${ticket.priority_category}`
          : `Priority requested: ${ticket.requested_category} — awaiting confirmation`
      }
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[0.72rem] font-semibold ${
        confirmed
          ? "border-swiftq-accent/30 bg-swiftq-accent/10 text-swiftq-accent-dk"
          : "border-amber-600/30 bg-amber-50 text-swiftq-warn"
      }`}
    >
      <span aria-hidden="true">⚑</span>
      {confirmed ? "Priority" : "Review"}
    </span>
  );
}

export default function Home() {
  const router = useRouter();
  const [session, setSession] = useState<StaffSession | null>(null);
  const [ready, setReady] = useState(false);
  const [queue, setQueue] = useState<MockQueue | null>(null);
  const [tickets, setTickets] = useState<MockTicket[]>([]);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"active" | "waiting" | "called" | "done">("active");
  const [dialog, setDialog] = useState<DialogState>({ kind: "none" });
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    const stored = getSession();
    if (!stored) {
      router.replace("/login");
      return;
    }
    setSession(stored);
    const snap = loadSnapshot();
    setQueue(snap.queue);
    setTickets(snap.tickets);
    setReady(true);
  }, [router]);

  const save = useCallback((q: MockQueue, t: MockTicket[]) => {
    const ordered = recomputePositions(t);
    setQueue({ ...q, updated_at: new Date().toISOString() });
    setTickets(ordered);
    persistSnapshot({ queue: q, tickets: ordered });
  }, []);

  // Live refresh: re-read persisted snapshot (simulates Firestore/polling update).
  const refresh = useCallback(async () => {
    await new Promise((r) => setTimeout(r, 250));
    const snap = loadSnapshot();
    setQueue(snap.queue);
    setTickets(snap.tickets);
  }, []);

  const mockMode = useMemo(
    () => (session ? isMockToken(session.token) : true),
    [session],
  );

  const health = useSyncHealth({ onSync: refresh, isMockMode: mockMode });

  function fail(e: unknown, fallback: string) {
    setNotice({ kind: "err", text: toErrorMessage(e) || fallback });
  }

  function ok(text: string) {
    setNotice({ kind: "ok", text });
  }

  // ── Queue actions (backend first when a real token exists, else mock) ──

  async function handleCallNext() {
    if (!queue || !session) return;
    if (queue.status === "paused") {
      setNotice({ kind: "err", text: "This queue is paused. Resume it before calling the next patient." });
      return;
    }
    const next = headOfQueue(tickets);
    if (!next) {
      setNotice({ kind: "err", text: "Nobody is waiting — the queue is empty." });
      return;
    }
    setBusy("call-next");
    try {
      if (!mockMode) await apiCallNext(queue.id, session.token, "Room 1");
      const updated = tickets.map((t) =>
        t.id === next.id
          ? { ...t, status: "called" as const, called_at: new Date().toISOString(), counter_label: "Room 1" }
          : t,
      );
      save({ ...queue, now_serving: next.display_number }, updated);
      ok(`${next.display_number} (${next.display_name}) called to Room 1.`);
    } catch (e) {
      fail(e, "Could not call the next patient.");
    } finally {
      setBusy(null);
    }
  }

  async function handleStatusAction(
    ticket: MockTicket,
    action: "serve" | "no-show" | "recall" | "return-late",
  ) {
    if (!queue || !session) return;
    setBusy(`${action}:${ticket.id}`);
    try {
      if (!mockMode) await apiTicketAction(ticket.id, action, session.token);
      const map: Record<string, MockTicket["status"]> = {
        serve: "served",
        "no-show": "no_show",
        "return-late": "waiting",
      };
      const updated = tickets.map((t) => {
        if (t.id !== ticket.id) return t;
        if (action === "recall") return { ...t, called_at: new Date().toISOString() };
        if (action === "return-late") return { ...t, status: "waiting" as const, late_flag: true };
        return {
          ...t,
          status: map[action],
          ...(action === "serve" || action === "no-show"
            ? {}
            : {}),
        };
      });
      save(queue, updated);
      const label =
        action === "serve"
          ? `served`
          : action === "no-show"
            ? "marked as no-show"
            : action === "recall"
              ? "recalled (notification re-sent)"
              : "returned to the queue (late)";
      ok(`${ticket.display_number} ${label}.`);
    } catch (e) {
      fail(e, "Action failed.");
    } finally {
      setBusy(null);
    }
  }

  async function handleDismiss(ticket: MockTicket, reason: string, note: string) {
    if (!queue || !session) return;
    setBusy(`dismiss:${ticket.id}`);
    try {
      if (!mockMode) {
        await apiDismissTicket(ticket.id, session.token, {
          reason: reason as "duplicate" | "wrong_service_point" | "patient_left" | "expired" | "other",
          note: note || undefined,
        });
      }
      save(queue, tickets.map((t) => (t.id === ticket.id ? { ...t, status: "cancelled" as const } : t)));
      ok(`${ticket.display_number} dismissed (${reason}).`);
      setDialog({ kind: "none" });
    } catch (e) {
      fail(e, "Could not dismiss the ticket.");
    } finally {
      setBusy(null);
    }
  }

  async function handlePriority(ticket: MockTicket, confirmed: boolean, category: PriorityCategory) {
    if (!queue || !session) return;
    setBusy(`priority:${ticket.id}`);
    try {
      if (!mockMode) await apiConfirmPriority(ticket.id, session.token, { confirmed, category });
      const updated = tickets.map((t) =>
        t.id === ticket.id
          ? {
              ...t,
              staff_confirmed: confirmed,
              priority_category: confirmed ? category : "none",
              requested_category: confirmed ? t.requested_category : "none",
            }
          : t,
      );
      save(queue, updated);
      ok(
        confirmed
          ? `${ticket.display_number} confirmed as ${category} priority.`
          : `${ticket.display_number} priority request rejected — standard FIFO.`,
      );
      setDialog({ kind: "none" });
    } catch (e) {
      fail(e, "Could not save the priority decision.");
    } finally {
      setBusy(null);
    }
  }

  async function handleWalkIn(v: { name: string; phone: string; priority: PriorityCategory }) {
    if (!queue || !session) return;
    setBusy("walkin");
    try {
      if (!mockMode) {
        await apiWalkIn(queue.id, session.token, {
          display_name: v.name,
          phone: v.phone || undefined,
          language: "en",
          notification_channel: v.phone ? "sms" : "none",
          priority_category: v.priority,
        });
      }
      const maxSeq = tickets.reduce((m, t) => Math.max(m, t.sequence_no), 0);
      const seq = maxSeq + 1;
      const confirmed = v.priority !== "none";
      const nt: MockTicket = {
        id: `tkt_walkin_${Date.now()}`,
        display_number: `${queue.display_prefix}${String(seq).padStart(3, "0")}`,
        sequence_no: seq,
        display_name: v.name,
        phone_last4: v.phone ? v.phone.slice(-4) : null,
        status: "waiting",
        priority_category: confirmed ? v.priority : "none",
        requested_category: v.priority,
        staff_confirmed: confirmed,
        late_flag: false,
        protected: false,
        position: null,
        joined_at: new Date().toISOString(),
        called_at: null,
        counter_label: null,
        estimated_wait_min: null,
      };
      save(queue, [...tickets, nt]);
      ok(`${nt.display_number} added to the queue.`);
      setDialog({ kind: "none" });
    } catch (e) {
      fail(e, "Could not add the walk-in.");
    } finally {
      setBusy(null);
    }
  }

  async function handlePauseResume(reason: string) {
    if (!queue || !session) return;
    const pausing = queue.status !== "paused";
    setBusy("pause");
    try {
      if (!mockMode) {
        if (pausing) await apiPauseQueue(queue.id, session.token, reason || undefined);
        else await apiResumeQueue(queue.id, session.token);
      }
      save(
        {
          ...queue,
          status: pausing ? "paused" : "open",
          pause_reason: pausing ? reason || "Paused by staff" : null,
        },
        tickets,
      );
      ok(pausing ? "Queue paused — joins and call-next are blocked." : "Queue resumed.");
      setDialog({ kind: "none" });
    } catch (e) {
      fail(e, "Could not update the queue.");
    } finally {
      setBusy(null);
    }
  }

  async function handleTransfer(ticket: MockTicket, targetQueueId: string) {
    if (!queue || !session) return;
    setBusy(`transfer:${ticket.id}`);
    try {
      if (!mockMode) await apiTransferTicket(ticket.id, session.token, targetQueueId);
      // Demo: ticket leaves this queue's list (it now lives on the target queue).
      save(queue, tickets.filter((t) => t.id !== ticket.id));
      ok(`${ticket.display_number} transferred to ${targetQueueId}. Join time kept.`);
      setDialog({ kind: "none" });
    } catch (e) {
      fail(e, "Could not transfer the ticket.");
    } finally {
      setBusy(null);
    }
  }

  function handleLogout() {
    clearSession();
    router.replace("/login");
  }

  // ── Derived view ──

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    let list = [...tickets];
    if (statusFilter === "active") list = list.filter((t) => t.status === "waiting" || t.status === "called");
    else if (statusFilter === "done") list = list.filter((t) => t.status === "served" || t.status === "no_show" || t.status === "cancelled");
    else list = list.filter((t) => t.status === statusFilter);
    if (q.length >= 2) {
      list = list.filter(
        (t) =>
          t.display_number.toLowerCase().includes(q) ||
          t.display_name.toLowerCase().includes(q) ||
          (t.phone_last4 ?? "").includes(q.replace(/\D/g, "")),
      );
    }
    const rank = (t: MockTicket) =>
      t.status === "called" ? -1 : (t.position ?? 999);
    return list.sort((a, b) => rank(a) - rank(b));
  }, [tickets, search, statusFilter]);

  const stats = useMemo(() => {
    const waiting = tickets.filter((t) => t.status === "waiting").length;
    const served = tickets.filter((t) => t.status === "served").length;
    const noshow = tickets.filter((t) => t.status === "no_show").length;
    const waits = tickets
      .filter((t) => t.status === "waiting" && t.estimated_wait_min != null)
      .map((t) => t.estimated_wait_min as number);
    const avg = waits.length ? Math.round(waits.reduce((a, b) => a + b, 0) / waits.length) : 0;
    return [
      { value: String(waiting), label: "Waiting" },
      { value: String(served), label: "Served" },
      { value: String(noshow), label: "No-shows" },
      { value: `${avg} min`, label: "Avg wait" },
    ];
  }, [tickets]);

  const nextUp = useMemo(() => headOfQueue(tickets), [tickets]);
  const calledNow = useMemo(() => tickets.find((t) => t.status === "called"), [tickets]);

  if (!ready || !session || !queue) {
    return (
      <div className="flex min-h-full items-center justify-center bg-swiftq-bg">
        <p className="text-swiftq-muted">Loading…</p>
      </div>
    );
  }

  const paused = queue.status === "paused";

  return (
    <div className="flex min-h-full flex-col bg-swiftq-bg">
      <TopBar userName={session.name} role={session.role} onLogout={handleLogout} />

      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 pt-6 pb-16">
        <div className="mb-4 flex flex-wrap items-start gap-4">
          <div className="flex-1 basis-80">
            <h1 className="m-0 mb-1 text-[1.5rem] font-bold">Live Queue Dashboard</h1>
            <p className="m-0 text-[0.92rem] text-swiftq-muted">
              {queue.service_point_name} · Queue {queue.id} ·{" "}
              <span className={paused ? "font-semibold text-swiftq-warn" : "font-semibold text-swiftq-ok"}>
                {paused ? `Paused${queue.pause_reason ? ` — ${queue.pause_reason}` : ""}` : "Open"}
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
              className="float-right cursor-pointer font-bold opacity-60 hover:opacity-100"
            >
              ×
            </button>
          </div>
        )}

        <div className="grid gap-[18px] lg:grid-cols-[minmax(0,2fr)_minmax(300px,1fr)]">
          <Card
            title="Today's queue"
            action={
              <span className="rounded-full bg-swiftq-bg px-2.5 py-0.5 text-[0.8rem] font-semibold text-swiftq-muted">
                {filtered.length} shown
              </span>
            }
          >
            <div className="mb-4 flex flex-wrap gap-5 border-b border-swiftq-border bg-swiftq-surface-subtle px-1 py-3">
              {stats.map((s) => (
                <div key={s.label} className="flex flex-col">
                  <span className="text-[1.35rem] font-bold">{s.value}</span>
                  <span className="text-[0.74rem] tracking-[0.05em] text-swiftq-muted uppercase">
                    {s.label}
                  </span>
                </div>
              ))}
            </div>

            <div className="mb-3 flex flex-wrap gap-2">
              <div className="min-w-[220px] flex-1">
                <label htmlFor="patient-search" className="sr-only">
                  Search patients
                </label>
                <input
                  id="patient-search"
                  type="search"
                  placeholder="Search: ticket (A014), name, or last 4 phone digits…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="min-h-[var(--swiftq-target)] w-full rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 text-[0.9rem]"
                />
              </div>
              <div role="group" aria-label="Status filter" className="flex gap-1.5">
                {(["active", "waiting", "called", "done"] as const).map((f) => (
                  <button
                    key={f}
                    type="button"
                    onClick={() => setStatusFilter(f)}
                    aria-pressed={statusFilter === f}
                    className={`cursor-pointer rounded-full border px-3 py-1.5 text-[0.8rem] font-semibold capitalize ${
                      statusFilter === f
                        ? "border-swiftq-accent bg-swiftq-accent text-swiftq-on-accent"
                        : "border-swiftq-border bg-swiftq-surface text-swiftq-muted hover:bg-swiftq-hover"
                    }`}
                  >
                    {f}
                  </button>
                ))}
              </div>
            </div>

            {filtered.length === 0 ? (
              <div className="px-5 py-9 text-center text-swiftq-muted">
                <h3 className="mb-1.5 text-[1rem] font-semibold text-swiftq-text">
                  No tickets match
                </h3>
                <p className="m-0 text-[0.88rem]">
                  {search.trim().length >= 2
                    ? "Try a different ticket number, name, or phone digits."
                    : "Nobody in this view right now."}
                </p>
              </div>
            ) : (
              <ul className="m-0 flex list-none flex-col gap-2 p-0">
                {filtered.map((t) => {
                  const rowBusy = busy !== null && busy.endsWith(t.id);
                  return (
                    <li
                      key={t.id}
                      className={`rounded-[var(--swiftq-radius)] border px-3 py-2.5 ${
                        t.status === "called"
                          ? "border-swiftq-accent/40 bg-swiftq-accent/5"
                          : "border-swiftq-border bg-swiftq-surface"
                      }`}
                    >
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="min-w-[52px] text-[1rem] font-bold">{t.display_number}</span>
                        <span className="flex-1 basis-32 text-[0.9rem] font-medium">
                          {t.display_name}
                          <span className="ml-2 text-[0.75rem] font-normal text-swiftq-muted">
                            {t.status === "waiting" && t.position ? `· #${t.position} in line` : ""}
                            {t.status === "waiting" && t.estimated_wait_min != null
                              ? ` · ~${t.estimated_wait_min} min`
                              : ""}
                            {t.status === "called" && t.counter_label ? ` · ${t.counter_label}` : ""}
                            {t.late_flag ? " · late return" : ""}
                          </span>
                        </span>
                        <PriorityFlag ticket={t} />
                        <span className="rounded-full bg-swiftq-bg px-2 py-0.5 text-[0.72rem] font-semibold text-swiftq-muted capitalize">
                          {t.status.replace("_", "-")}
                        </span>
                      </div>
                      {(t.status === "waiting" || t.status === "called") && (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {t.status === "waiting" &&
                            t.requested_category !== "none" &&
                            !t.staff_confirmed && (
                              <Button
                                size="sm"
                                variant="primary"
                                disabled={rowBusy}
                                onClick={() => setDialog({ kind: "priority", ticket: t })}
                              >
                                Confirm priority
                              </Button>
                            )}
                          {t.status === "called" && (
                            <>
                              <Button
                                size="sm"
                                variant="primary"
                                disabled={rowBusy}
                                onClick={() => void handleStatusAction(t, "serve")}
                              >
                                {busy === `serve:${t.id}` ? "Serving…" : "Serve"}
                              </Button>
                              <Button
                                size="sm"
                                variant="default"
                                disabled={rowBusy}
                                onClick={() => void handleStatusAction(t, "no-show")}
                              >
                                {busy === `no-show:${t.id}` ? "Saving…" : "No-show"}
                              </Button>
                              <Button
                                size="sm"
                                variant="ghost"
                                disabled={rowBusy}
                                onClick={() => void handleStatusAction(t, "recall")}
                              >
                                Recall
                              </Button>
                            </>
                          )}
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={rowBusy}
                            onClick={() => setDialog({ kind: "transfer", ticket: t })}
                          >
                            Transfer
                          </Button>
                          <Button
                            size="sm"
                            variant="danger"
                            disabled={rowBusy}
                            onClick={() => setDialog({ kind: "dismiss", ticket: t })}
                          >
                            Dismiss
                          </Button>
                        </div>
                      )}
                      {t.status === "no_show" && (
                        <div className="mt-2">
                          <Button
                            size="sm"
                            variant="default"
                            disabled={rowBusy}
                            onClick={() => void handleStatusAction(t, "return-late")}
                          >
                            Return late → waiting
                          </Button>
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}

            <div className="mt-3 flex justify-end">
              <button
                type="button"
                onClick={() => {
                  const snap = resetSnapshot();
                  setQueue(snap.queue);
                  setTickets(snap.tickets);
                  ok("Demo queue reset to seed data.");
                }}
                className="cursor-pointer text-[0.78rem] font-medium text-swiftq-muted underline hover:text-swiftq-text"
              >
                Reset demo data
              </button>
            </div>
          </Card>

          <div className="flex flex-col gap-[18px]">
            <Card title="Call next patient">
              {calledNow && (
                <p className="mb-2 rounded-[var(--swiftq-radius)] border border-swiftq-accent/30 bg-swiftq-accent/5 px-3 py-2 text-[0.85rem]">
                  <strong>Now serving {calledNow.display_number}</strong> — {calledNow.display_name}
                  {calledNow.counter_label ? ` · ${calledNow.counter_label}` : ""}. Serve it or mark
                  no-show before calling the next patient.
                </p>
              )}
              {paused ? (
                <p className="mb-3.5 text-swiftq-warn">
                  Queue is paused — call-next is blocked until you resume.
                </p>
              ) : nextUp ? (
                <p className="mb-3.5 text-swiftq-muted">
                  Next up: <strong className="text-swiftq-text">{nextUp.display_number}</strong>{" "}
                  {nextUp.display_name}
                  {nextUp.staff_confirmed ? " (priority)" : ""} · ~{nextUp.estimated_wait_min} min
                </p>
              ) : (
                <p className="mb-3.5 text-swiftq-muted">No patients waiting.</p>
              )}
              <Button
                variant="primary"
                className="w-full"
                disabled={!nextUp || paused || busy === "call-next"}
                onClick={() => void handleCallNext()}
              >
                {busy === "call-next" ? "Calling…" : "Call next"}
              </Button>
              {calledNow && (
                <div className="mt-2 flex gap-2">
                  <Button
                    variant="default"
                    size="sm"
                    className="flex-1"
                    onClick={() => void handleStatusAction(calledNow, "serve")}
                  >
                    Serve {calledNow.display_number}
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    className="flex-1"
                    onClick={() => void handleStatusAction(calledNow, "no-show")}
                  >
                    No-show
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
                  className="cursor-pointer rounded-full bg-swiftq-bg px-2.5 py-0.5 text-[0.8rem] font-semibold text-swiftq-muted hover:text-swiftq-text"
                >
                  Check now
                </button>
              }
            >
              <SyncBadge health={health} />
              <p className="mt-2 mb-0 text-[0.8rem] text-swiftq-muted">
                {mockMode
                  ? "Running on local demo data — actions apply instantly and sync when the backend returns."
                  : "Connected to the live API. Changes publish to Firestore live_queues."}{" "}
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
          busy={busy === "walkin"}
        />
      )}
      {dialog.kind === "priority" && (
        <PriorityDialog
          ticket={dialog.ticket}
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(c, cat) => void handlePriority(dialog.ticket, c, cat)}
          busy={busy === `priority:${dialog.ticket.id}`}
        />
      )}
      {dialog.kind === "dismiss" && (
        <DismissDialog
          ticket={dialog.ticket}
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(r, n) => void handleDismiss(dialog.ticket, r, n)}
          busy={busy === `dismiss:${dialog.ticket.id}`}
        />
      )}
      {dialog.kind === "transfer" && (
        <TransferDialog
          ticket={dialog.ticket}
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(q) => void handleTransfer(dialog.ticket, q)}
          busy={busy === `transfer:${dialog.ticket.id}`}
        />
      )}
      {dialog.kind === "pause" && (
        <PauseDialog
          paused={paused}
          onClose={() => setDialog({ kind: "none" })}
          onSubmit={(r) => void handlePauseResume(r)}
          busy={busy === "pause"}
        />
      )}
    </div>
  );
}
