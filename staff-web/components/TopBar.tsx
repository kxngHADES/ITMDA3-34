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
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src="/swiftq-logo.svg"
          alt=""
          width={36}
          height={36}
          className="rounded-[10px]"
        />
        <span>
          Swift <span className="text-[#62b8ff]">Q</span>{" "}
          <span className="font-medium text-swiftq-on-accent-muted">Staff</span>
        </span>

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
