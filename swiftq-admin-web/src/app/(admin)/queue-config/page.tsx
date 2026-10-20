"use client";

import { RequireRole } from "@/components/layout/RequireRole";
import { QueueConfigScreen } from "@/features/queue-config/QueueConfigScreen";

export default function QueueConfigPage() {
  return (
    <RequireRole section="queue-config">
      <QueueConfigScreen />
    </RequireRole>
  );
}
