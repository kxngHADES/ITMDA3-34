"use client";

import { RequireRole } from "@/components/layout/RequireRole";
import { AuditScreen } from "@/features/audit/AuditScreen";

export default function AuditLogPage() {
  return (
    <RequireRole section="audit">
      <AuditScreen />
    </RequireRole>
  );
}
