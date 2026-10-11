import type { Metadata } from "next";
import { LoginForm } from "@/components/LoginForm";

export const metadata: Metadata = {
  title: "Sign in | SwiftQ Staff",
  description: "Sign in to the SwiftQ staff portal to manage the live queue.",
};

export default function LoginPage() {
  return (
    <div
      className="flex min-h-full items-center justify-center p-6"
      style={{
        background:
          "linear-gradient(160deg, var(--swiftq-accent-dk) 0%, var(--swiftq-accent) 55%, var(--swiftq-bg) 55%)",
      }}
    >
      <main className="w-full max-w-[420px] overflow-hidden rounded-[var(--swiftq-radius-lg)] bg-swiftq-surface shadow-swiftq">
        <header className="border-b border-swiftq-border px-[26px] pt-[26px] pb-[18px] text-center">
          <h1 className="mt-2.5 mb-1 text-[1.3rem] font-bold">
            SwiftQ Staff Portal
          </h1>
          <p className="m-0 text-[0.88rem] text-swiftq-muted">
            Sign in to manage the live queue
          </p>
        </header>
        <div className="px-[26px] py-[22px]">
          <LoginForm />
          <div className="mt-[18px] border-t border-dashed border-swiftq-border pt-[14px] text-[0.8rem] text-swiftq-muted">
            <strong>Demo accounts (prototype)</strong>
            <ul className="mt-2 list-disc pl-[18px]">
              <li>
                <code className="rounded bg-swiftq-bg px-1.5 py-px text-[0.78rem]">
                  thandi@clinic.za
                </code>{" "}
                — receptionist
              </li>
              <li>
                <code className="rounded bg-swiftq-bg px-1.5 py-px text-[0.78rem]">
                  naledi@clinic.za
                </code>{" "}
                — nurse
              </li>
              <li>
                <code className="rounded bg-swiftq-bg px-1.5 py-px text-[0.78rem]">
                  manager@clinic.za
                </code>{" "}
                — manager
              </li>
            </ul>
            <p className="mt-2">
              Password for all demo accounts:{" "}
              <code className="rounded bg-swiftq-bg px-1.5 py-px text-[0.78rem]">
                swiftq
              </code>
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}
