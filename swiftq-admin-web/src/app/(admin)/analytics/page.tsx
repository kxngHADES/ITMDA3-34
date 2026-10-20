"use client";

import { RequireRole } from "@/components/layout/RequireRole";
import { AnalyticsScreen } from "@/features/analytics/AnalyticsScreen";

export default function AnalyticsPage() {
  return (
    <RequireRole section="analytics">
      <AnalyticsScreen />
    </RequireRole>
  );
}
