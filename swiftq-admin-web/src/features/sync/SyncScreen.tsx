"use client";

import { useRef, useState, type KeyboardEvent } from "react";
import { ConflictsTab } from "./ConflictsTab";
import { SyncLogTab } from "./SyncLogTab";

const TABS = [
  { id: "conflicts", label: "Conflicts to review" },
  { id: "log", label: "Sync attempts" },
] as const;
type TabId = (typeof TABS)[number]["id"];

export function SyncScreen() {
  const [tab, setTab] = useState<TabId>("conflicts");
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});

  // Arrow keys move between tabs, as people expect from a tab list.
  function onKeyDown(e: KeyboardEvent, index: number) {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    e.preventDefault();
    const next = (index + (e.key === "ArrowRight" ? 1 : TABS.length - 1)) % TABS.length;
    setTab(TABS[next].id);
    refs.current[TABS[next].id]?.focus();
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Sync log</h1>
          <p>Phones and tablets save changes offline and send them later. Review the changes that clashed and check every sync attempt.</p>
        </div>
      </div>
      <div className="tabs" role="tablist" aria-label="Sync log">
        {TABS.map((t, i) => (
          <button
            key={t.id}
            ref={(el) => { refs.current[t.id] = el; }}
            role="tab"
            id={`tab-${t.id}`}
            aria-selected={tab === t.id}
            aria-controls={`panel-${t.id}`}
            tabIndex={tab === t.id ? 0 : -1}
            className="tab"
            onClick={() => setTab(t.id)}
            onKeyDown={(e) => onKeyDown(e, i)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === "conflicts" && <ConflictsTab />}
        {tab === "log" && <SyncLogTab />}
      </div>
    </>
  );
}
