"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { TopBar } from "@/components/TopBar";
import type { StaffSession } from "@/lib/api/auth";
import { clearSession, getSession } from "@/lib/auth/session";

const PLACEHOLDER_STATS = [
  { value: "—", label: "Waiting" },
  { value: "—", label: "Served" },
  { value: "—", label: "No-shows" },
  { value: "— min", label: "Avg wait" },
];

export default function Home() {
  const router = useRouter();
  const [session, setSession] = useState<StaffSession | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const stored = getSession();
    if (!stored) {
      router.replace("/login");
      return;
    }
    setSession(stored);
    setReady(true);
  }, [router]);

  function handleLogout() {
    clearSession();
    router.replace("/login");
  }

  if (!ready || !session) {
    return (
      <div className="flex min-h-full items-center justify-center bg-swiftq-bg">
        <p className="text-swiftq-muted">Loading…</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-full flex-col bg-swiftq-bg">
      <TopBar
        userName={session.name}
        role={session.role}
        onLogout={handleLogout}
      />

      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 pt-6 pb-16">
        <div className="mb-5 flex flex-wrap items-start gap-4">
          <div className="flex-1 basis-80">
            <h1 className="m-0 mb-1 text-[1.5rem] font-bold">
              Live Queue Dashboard
            </h1>
            <p className="m-0 text-[0.92rem] text-swiftq-muted">
              Today&apos;s queue at a glance. Queue actions land here next (W2).
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2.5">
            <Button variant="primary">Open today&apos;s queue</Button>
          </div>
        </div>

        <div className="grid gap-[18px] md:grid-cols-[minmax(0,2fr)_minmax(300px,1fr)]">
          <Card
            title="Today's queue"
            action={
              <span className="rounded-full bg-swiftq-bg px-2.5 py-0.5 text-[0.8rem] font-semibold text-swiftq-muted">
                Scaffold
              </span>
            }
          >
            <div className="mb-4 flex flex-wrap gap-5 border-b border-swiftq-border bg-swiftq-surface-subtle px-1 py-3">
              {PLACEHOLDER_STATS.map((s) => (
                <div key={s.label} className="flex flex-col">
                  <span className="text-[1.35rem] font-bold">{s.value}</span>
                  <span className="text-[0.74rem] tracking-[0.05em] text-swiftq-muted uppercase">
                    {s.label}
                  </span>
                </div>
              ))}
            </div>
            <div className="px-5 py-9 text-center text-swiftq-muted">
              <h3 className="mb-1.5 text-[1rem] font-semibold text-swiftq-text">
                Queue list goes here
              </h3>
              <p className="m-0 text-[0.88rem]">
                W2 will render live tickets with neutral priority indicators.
              </p>
            </div>
          </Card>

          <div className="flex flex-col gap-[18px]">
            <Card title="Call next patient">
              <p className="mb-3.5 text-swiftq-muted">No patients waiting.</p>
              <Button variant="primary" className="w-full" disabled>
                Call next
              </Button>
            </Card>

            <Card title="Sync health">
              <p className="m-0 text-[0.88rem] text-swiftq-muted">
                W6 will show the online / offline / syncing indicator here.
              </p>
            </Card>
          </div>
        </div>
      </main>
    </div>
  );
}
