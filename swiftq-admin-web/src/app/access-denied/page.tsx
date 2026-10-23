"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Button } from "@/components/ui/Button";
import { FullPageLoading } from "@/components/layout/FullPage";
import { ROLE_LABEL } from "@/lib/auth/permissions";

export default function AccessDeniedPage() {
  const { state, signOut } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "signed-out") router.replace("/login/");
    if (state.status === "ready") router.replace("/dashboard/");
  }, [state.status, router]);

  if (state.status !== "denied") return <FullPageLoading />;

  const role = state.me ? ROLE_LABEL[state.me.role] : null;
  return (
    <div className="fullpage">
      <div className="panel" role="alert">
        <h1 className="sq-text-title-1">This account can&apos;t use Admin</h1>
        <p className="muted">
          SwiftQ Admin is for IT admins and clinic managers.
          {role ? ` You are signed in as ${role}.` : ""} Ask your IT admin if you think this is a mistake.
        </p>
        <Button variant="primary" size="lg" onClick={() => void signOut()}>Sign out</Button>
      </div>
    </div>
  );
}
