"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type SyncState = "online" | "syncing" | "offline" | "demo";

export interface SyncHealth {
  state: SyncState;
  lastSyncAt: string | null;
  pendingCount: number;
  lastError: string | null;
  retry: () => void;
}

interface Options {
  /** Called when the user (or auto-poll) requests a refresh. */
  onSync: () => Promise<void>;
  pollMs?: number;
  isMockMode?: boolean;
}

/**
 * Sync health / offline indicator state.
 * - Listens to browser online/offline events.
 * - Wraps every queue refresh so the badge shows "Syncing…".
 * - Polls in the background (default 15 s) while online.
 */
export function useSyncHealth({ onSync, pollMs = 15000, isMockMode = true }: Options): SyncHealth {
  const [browserOnline, setBrowserOnline] = useState<boolean>(
    () => (typeof navigator === "undefined" ? true : navigator.onLine),
  );
  const [syncing, setSyncing] = useState(false);
  const [lastSyncAt, setLastSyncAt] = useState<string | null>(null);
  const [pendingCount, setPendingCount] = useState(0);
  const [lastError, setLastError] = useState<string | null>(null);
  const onSyncRef = useRef(onSync);
  onSyncRef.current = onSync;

  const doSync = useCallback(async () => {
    setSyncing(true);
    setLastError(null);
    try {
      await onSyncRef.current();
      setLastSyncAt(new Date().toISOString());
      setPendingCount(0);
    } catch (e) {
      setLastError(e instanceof Error ? e.message : "Sync failed.");
      setPendingCount((n) => n + 1);
    } finally {
      setSyncing(false);
    }
  }, []);

  useEffect(() => {
    const goOnline = () => {
      setBrowserOnline(true);
      void doSync();
    };
    const goOffline = () => setBrowserOnline(false);
    window.addEventListener("online", goOnline);
    window.addEventListener("offline", goOffline);
    return () => {
      window.removeEventListener("online", goOnline);
      window.removeEventListener("offline", goOffline);
    };
  }, [doSync]);

  useEffect(() => {
    void doSync();
    if (pollMs <= 0) return;
    const t = setInterval(() => {
      if (typeof navigator !== "undefined" && !navigator.onLine) return;
      void doSync();
    }, pollMs);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  let state: SyncState;
  if (!browserOnline) state = "offline";
  else if (syncing) state = "syncing";
  else if (lastError) state = "offline";
  else if (isMockMode) state = "demo";
  else state = "online";

  return { state, lastSyncAt, pendingCount, lastError, retry: () => void doSync() };
}

const DOT: Record<SyncState, string> = {
  online: "bg-green-600",
  syncing: "bg-amber-500",
  offline: "bg-red-600",
  demo: "bg-amber-500",
};

const LABEL: Record<SyncState, string> = {
  online: "Online",
  syncing: "Syncing…",
  offline: "Offline",
  demo: "Demo data — backend unreachable",
};

export function SyncBadge({
  health,
  compact = false,
}: {
  health: SyncHealth;
  compact?: boolean;
}) {
  const time = health.lastSyncAt
    ? new Date(health.lastSyncAt).toLocaleTimeString()
    : "never";
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-wrap items-center gap-2 rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 py-2"
    >
      <span
        aria-hidden="true"
        className={`inline-block h-2.5 w-2.5 rounded-full ${DOT[health.state]} ${
          health.state === "syncing" ? "animate-pulse" : ""
        }`}
      />
      <span className="text-[0.85rem] font-semibold">{LABEL[health.state]}</span>
      {!compact && (
        <span className="text-[0.78rem] text-swiftq-muted">
          Last sync {time}
          {health.pendingCount > 0 ? ` · ${health.pendingCount} pending` : ""}
        </span>
      )}
      {(health.state === "offline" || health.lastError) && (
        <button
          type="button"
          onClick={health.retry}
          className="ml-auto cursor-pointer rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-2.5 py-1 text-[0.8rem] font-semibold text-swiftq-accent hover:bg-swiftq-hover"
        >
          Retry
        </button>
      )}
    </div>
  );
}
