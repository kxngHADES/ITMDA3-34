import type { Metadata } from "next";
import { LoginForm } from "@/components/LoginForm";

export const metadata: Metadata = {
  title: "Sign in | SwiftQ Staff",
  description: "Sign in to the SwiftQ staff portal to manage the live queue.",
};

export default function LoginPage() {
  return (
    <div className="login-backdrop flex min-h-full items-center justify-center p-6">
      <span aria-hidden="true" className="login-orb login-orb-a" />
      <span aria-hidden="true" className="login-orb login-orb-b" />
      <main className="w-full max-w-[420px] overflow-hidden rounded-[var(--swiftq-radius-lg)] bg-swiftq-surface shadow-[0_20px_60px_rgba(0,20,80,0.35)]">
        <header className="border-b border-swiftq-border px-[26px] pt-[26px] pb-[18px] text-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/swiftq-logo.svg"
            alt="SwiftQ logo"
            width={72}
            height={72}
            className="mx-auto mb-3 rounded-[18px] shadow-[0_8px_24px_rgba(10,125,255,0.35)]"
          />
          <p className="m-0 text-[1.7rem] leading-none font-extrabold tracking-tight">
            Swift <span style={{ color: "var(--swiftq-brand-mid)" }}>Q</span>
          </p>
          <p className="mt-1.5 mb-3 text-[0.68rem] font-bold tracking-[0.22em] text-[#0062cc]">
            SMARTER PATIENT FLOW
          </p>
          <h1 className="m-0 mb-1 text-[1.1rem] font-bold">Staff Portal</h1>
          <p className="m-0 text-[0.88rem] text-swiftq-muted">
            Sign in to manage the live queue
          </p>
        </header>
        <div className="px-[26px] py-[22px]">
          <LoginForm />
        </div>
      </main>
    </div>
  );
}
