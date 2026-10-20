"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useMe } from "@/lib/auth/AuthProvider";
import { ROLE_LABEL, canAccess, rolesLabel, type Section } from "@/lib/auth/permissions";
import { EmptyState } from "@/components/ui/States";

/**
 * Hides a screen from roles that may not use it.
 * This is for navigation only. The API checks the role again on every request,
 * so changing this in the browser would not give anyone extra access.
 */
export function RequireRole({ section, children }: { section: Section; children: ReactNode }) {
  const me = useMe();
  if (canAccess(me.role, section)) return <>{children}</>;
  return (
    <EmptyState
      title="You don't have access to this page"
      action={<Link className="btn btn-primary" href="/dashboard/">Go to home</Link>}
    >
      This page is for {rolesLabel(section)}. You are signed in as {ROLE_LABEL[me.role]}.
    </EmptyState>
  );
}
