"use client";

import { RequireRole } from "@/components/layout/RequireRole";
import { StaffScreen } from "@/features/staff/StaffScreen";

export default function StaffPage() {
  return (
    <RequireRole section="staff">
      <StaffScreen />
    </RequireRole>
  );
}
