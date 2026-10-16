"use client";

import { EmptyState } from "@/components/ui/States";

// The audit log is not built on the shared API yet (Admin API Reference, section 8).
export function AuditScreen() {
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Audit log</h1>
          <p>This will show who did what, and when.</p>
        </div>
      </div>
      <div className="card">
        <EmptyState title="The audit log is not available yet">
          The server team has not built it yet. It will appear here once it is ready.
        </EmptyState>
      </div>
    </>
  );
}
