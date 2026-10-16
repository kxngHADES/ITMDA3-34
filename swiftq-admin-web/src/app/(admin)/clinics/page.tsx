"use client";

import { RequireRole } from "@/components/layout/RequireRole";
import { ClinicsScreen } from "@/features/clinics/ClinicsScreen";

export default function ClinicsPage() {
  return (
    <RequireRole section="clinics">
      <ClinicsScreen />
    </RequireRole>
  );
}
