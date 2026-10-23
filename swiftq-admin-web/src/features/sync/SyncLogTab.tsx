"use client";

import { useCallback, useState } from "react";
import { SyncStatusChip, SYNC_STATUS } from "@/components/ui/Chip";
import { SelectField, type Option } from "@/components/ui/Field";
import { EmptyState, ErrorPanel, LoadMore, LoadingBlock } from "@/components/ui/States";
import { fetchAll, listClinics, listSyncLog } from "@/lib/api/endpoints";
import type { SyncLogStatus } from "@/lib/api/types";
import { formatDateTime, shortId } from "@/lib/format";
import { useLoad, usePagedList } from "@/lib/hooks";

const STATUS_OPTIONS: Option[] = (Object.keys(SYNC_STATUS) as SyncLogStatus[]).map((s) => ({ value: s, label: SYNC_STATUS[s].label }));

export function SyncLogTab() {
  const [status, setStatus] = useState<SyncLogStatus | "">("");
  const [clinicId, setClinicId] = useState("");

  const loadClinics = useCallback(() => fetchAll(listClinics), []);
  const clinics = useLoad(loadClinics);
  const clinicNames = new Map((clinics.data ?? []).map((c) => [c.id, c.name]));

  const load = useCallback(
    (cursor?: string) => listSyncLog({ limit: 25, cursor, status: status || undefined, clinic_id: clinicId || undefined }),
    [status, clinicId],
  );
  const list = usePagedList(load);

  return (
    <>
      <div className="filters">
        <SelectField label="Status" value={status} options={STATUS_OPTIONS} placeholder="All statuses" onChange={(e) => setStatus(e.target.value as SyncLogStatus | "")} />
        <SelectField
          label="Clinic"
          value={clinicId}
          options={(clinics.data ?? []).map((c) => ({ value: c.id, label: c.name }))}
          placeholder="All clinics"
          onChange={(e) => setClinicId(e.target.value)}
        />
      </div>
      {list.loading ? (
        <LoadingBlock label="Loading sync log" />
      ) : list.error ? (
        <ErrorPanel message={list.error} onRetry={() => void list.reload()} />
      ) : list.items.length === 0 ? (
        <div className="card"><EmptyState title="No sync attempts match">Change the filters to see more.</EmptyState></div>
      ) : (
        <div className="table-wrap">
          <table>
            <caption className="visually-hidden">Sync attempts, newest first</caption>
            <thead>
              <tr>
                <th scope="col">When</th>
                <th scope="col">Device</th>
                <th scope="col">Clinic</th>
                <th scope="col">What</th>
                <th scope="col">Status</th>
                <th scope="col">Attempt</th>
                <th scope="col">Resolution</th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((e) => (
                <tr key={e.id}>
                  <td className="mono">{formatDateTime(e.created_at)}</td>
                  <td title={e.device_uid}>{shortId(e.device_uid)}</td>
                  <td>{e.clinic_id ? clinicNames.get(e.clinic_id) ?? shortId(e.clinic_id) : "None"}</td>
                  <td>{e.op.replace("_", " ")}</td>
                  <td><SyncStatusChip status={e.status} /></td>
                  <td className="mono">{e.attempt ?? 1}</td>
                  <td>{e.resolution ? e.resolution.replace(/_/g, " ") : "None"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onClick={() => void list.loadMore()} />
        </div>
      )}
    </>
  );
}
