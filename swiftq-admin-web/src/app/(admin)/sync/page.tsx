"use client";

import { RequireRole } from "@/components/layout/RequireRole";
import { SyncScreen } from "@/features/sync/SyncScreen";

export default function SyncPage() {
  return (
    <RequireRole section="sync">
      <SyncScreen />
    </RequireRole>
  );
}
