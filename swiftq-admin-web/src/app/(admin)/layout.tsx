"use client";

// Every screen inside the (admin) folder sits behind this guard (plan task D1).
// The folder name in brackets is a Next.js "route group": it groups screens without adding to the web address.

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Shell } from "@/components/layout/Shell";
import { FullPageError, FullPageLoading } from "@/components/layout/FullPage";

export default function AdminLayout({ children }: { children: ReactNode }) {
  const { state, retry, signOut } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "signed-out") router.replace("/login/");
    if (state.status === "denied") router.replace("/access-denied/");
  }, [state.status, router]);

  if (state.status === "ready") return <Shell me={state.me}>{children}</Shell>;
  if (state.status === "error") return <FullPageError message={state.message} onRetry={retry} onSignOut={() => void signOut()} />;
  return <FullPageLoading />;
}
