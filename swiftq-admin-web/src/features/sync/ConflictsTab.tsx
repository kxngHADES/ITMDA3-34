"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/Button";
import { SyncStatusChip } from "@/components/ui/Chip";
import { EmptyState, ErrorPanel, LoadMore, LoadingBlock } from "@/components/ui/States";
import { useToast } from "@/components/ui/Toast";
import { listSyncLog } from "@/lib/api/endpoints";
import type { SyncLogEntry } from "@/lib/api/types";
import { formatDateTime, shortId } from "@/lib/format";
import { usePagedList } from "@/lib/hooks";
import { ConflictReviewModal } from "./ConflictReviewModal";

export function ConflictsTab() {
  const toast = useToast();
  const [reviewing, setReviewing] = useState<SyncLogEntry | null>(null);
  const load = useCallback((cursor?: string) => listSyncLog({ status: "conflict_pending", limit: 25, cursor }), []);
  const list = usePagedList(load);

  if (list.loading) return <LoadingBlock label="Loading conflicts" />;
  if (list.error) return <ErrorPanel message={list.error} onRetry={() => void list.reload()} />;

  return (
    <>
      {list.items.length === 0 ? (
        <div className="card">
          <EmptyState title="No conflicts to review">When the same record changes on a device and on the server and can&apos;t be settled automatically, it shows up here.</EmptyState>
        </div>
      ) : (
        <div className="table-wrap">
          <table>
            <caption className="visually-hidden">Sync conflicts waiting for review</caption>
            <thead>
              <tr>
                <th scope="col">When</th>
                <th scope="col">Device</th>
                <th scope="col">What changed</th>
                <th scope="col">Status</th>
                <th scope="col"><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((entry) => (
                <tr key={entry.id}>
                  <td className="mono">{formatDateTime(entry.created_at)}</td>
                  <td title={entry.device_uid}>{shortId(entry.device_uid)}</td>
                  <td>
                    <div className="primary">{entry.op.replace("_", " ")}</div>
                    <div className="secondary" title={entry.entity_id ?? undefined}>{entry.entity_type} {shortId(entry.entity_id)}</div>
                  </td>
                  <td><SyncStatusChip status={entry.status} /></td>
                  <td><Button variant="primary" onClick={() => setReviewing(entry)}>Review</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onClick={() => void list.loadMore()} />
        </div>
      )}
      <ConflictReviewModal
        entry={reviewing}
        onClose={() => setReviewing(null)}
        onResolved={(message) => {
          toast.success(message);
          setReviewing(null);
          void list.reload();
        }}
        onStale={() => void list.reload()}
      />
    </>
  );
}
