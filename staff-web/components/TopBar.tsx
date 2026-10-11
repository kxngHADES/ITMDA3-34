"use client";

import { Button } from "./Button";

interface TopBarProps {
  userName: string;
  role: string;
  onLogout: () => void;
}

export function TopBar({ userName, role, onLogout }: TopBarProps) {
  return (
    <header className="flex min-h-16 flex-wrap items-center gap-4 bg-swiftq-accent-dk px-5 text-swiftq-on-accent">
      <div className="flex items-center gap-2.5 text-[1.15rem] font-bold tracking-[0.2px]">
        <span
          aria-hidden="true"
          className="inline-block h-6 w-6 rounded border-2 border-swiftq-on-accent"
        />
        SwiftQ Staff
      </div>

      <div className="flex-1" />

      <div className="flex items-center gap-2.5 text-[0.9rem] text-swiftq-on-accent-muted">
        <span>{userName}</span>
        <span className="rounded-full border border-swiftq-on-accent/30 bg-swiftq-on-accent/15 px-2.5 py-[3px] text-[0.78rem] font-semibold text-swiftq-on-accent capitalize">
          {role}
        </span>
        <Button size="sm" onClick={onLogout}>
          Log out
        </Button>
      </div>
    </header>
  );
}
