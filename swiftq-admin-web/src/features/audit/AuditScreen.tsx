"use client";

import { useCallback, useState } from "react";
import { Chip, type Tone } from "@/components/ui/Chip";
import { SelectField, TextField, type Option } from "@/components/ui/Field";
import { EmptyState, ErrorPanel, LoadMore, LoadingBlock } from "@/components/ui/States";
import { fetchAll, listAuditLog, listClinics, listStaff } from "@/lib/api/endpoints";
import type { AuditOutcome } from "@/lib/api/types";
import { ROLE_LABEL } from "@/lib/auth/permissions";
import { formatDateTime, humaniseAction, shortId } from "@/lib/format";
import { useLoad, usePagedList } from "@/lib/hooks";

export const AUDIT_OUTCOME: Record<AuditOutcome, { label: string; tone: Tone }> = {
  success: { label: "Done", tone: "success" },
  denied: { label: "Not allowed", tone: "warning" },
  failed: { label: "Failed", tone: "error" },
};

const OUTCOME_OPTIONS: Option[] = (Object.keys(AUDIT_OUTCOME) as AuditOutcome[]).map((o) => ({ value: o, label: AUDIT_OUTCOME[o].label }));

// Date inputs give a local calendar day, the API wants an exact instant, so cover the whole day.
const startOfDay = (ymd: string) => (ymd ? new Date(`${ymd}T00:00:00`).toISOString() : undefined);
const endOfDay = (ymd: string) => (ymd ? new Date(`${ymd}T23:59:59.999`).toISOString() : undefined);

export function AuditScreen() {
  const [clinicId, setClinicId] = useState("");
  const [actorId, setActorId] = useState("");
  const [outcome, setOutcome] = useState<AuditOutcome | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const rangeError = from && to && to < from ? "The end date must be on or after the start date." : undefined;

  const loadClinics = useCallback(() => fetchAll(listClinics), []);
  const clinics = useLoad(loadClinics);
  const loadStaff = useCallback(() => fetchAll((q) => listStaff({ ...q, include_inactive: true })), []);
  const staff = useLoad(loadStaff);
  const staffNames = new Map((staff.data ?? []).map((s) => [s.id, s.display_name || s.email]));

  const load = useCallback(
    async (_cursor?: string) => {
      if (rangeError) return { items: [], next_cursor: null, has_more: false };
      return listAuditLog({
        clinic_id: clinicId || undefined,
        actor_id: actorId || undefined,
        outcome: outcome || undefined,
        from: startOfDay(from),
        to: endOfDay(to),
      });
    },
    [clinicId, actorId, outcome, from, to, rangeError],
  );
  const list = usePagedList(load);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Audit log</h1>
          <p>Who did what, and when. Newest first.</p>
        </div>
      </div>
      <div className="filters">
        {(clinics.data?.length ?? 0) > 1 && (
          <SelectField
            label="Clinic"
            value={clinicId}
            options={(clinics.data ?? []).map((c) => ({ value: c.id, label: c.name }))}
            placeholder="All clinics"
            onChange={(e) => setClinicId(e.target.value)}
          />
        )}
        <SelectField
          label="Staff member"
          value={actorId}
          options={(staff.data ?? []).map((s) => ({ value: s.id, label: s.display_name || s.email }))}
          placeholder="Everyone"
          onChange={(e) => setActorId(e.target.value)}
        />
        <SelectField label="Outcome" value={outcome} options={OUTCOME_OPTIONS} placeholder="All outcomes" onChange={(e) => setOutcome(e.target.value as AuditOutcome | "")} />
        <TextField label="From" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
        <TextField label="To" type="date" value={to} min={from || undefined} error={rangeError} onChange={(e) => setTo(e.target.value)} />
      </div>
      {list.loading ? (
        <LoadingBlock label="Loading audit log" />
      ) : list.error ? (
        <ErrorPanel message={list.error} onRetry={() => void list.reload()} />
      ) : list.items.length === 0 ? (
        <div className="card"><EmptyState title="No actions match">Change the filters to see more.</EmptyState></div>
      ) : (
        <div className="table-wrap">
          <table>
            <caption className="visually-hidden">Staff actions, newest first</caption>
            <thead>
              <tr>
                <th scope="col">When</th>
                <th scope="col">Who</th>
                <th scope="col">Role</th>
                <th scope="col">Action</th>
                <th scope="col">On</th>
                <th scope="col">Outcome</th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((e) => {
                const o = AUDIT_OUTCOME[e.outcome] ?? { label: e.outcome, tone: "neutral" as Tone };
                return (
                  <tr key={e.id}>
                    <td className="mono">{formatDateTime(e.created_at)}</td>
                    <td title={e.actor_id}>{staffNames.get(e.actor_id) ?? shortId(e.actor_id)}</td>
                    <td>{ROLE_LABEL[e.actor_role] ?? e.actor_role}</td>
                    <td>{humaniseAction(e.action)}</td>
                    <td title={e.target_id ?? undefined}>
                      {e.target_type ? [e.target_type.replace(/_/g, " "), e.target_id && shortId(e.target_id)].filter(Boolean).join(" ") : "None"}
                    </td>
                    <td><Chip tone={o.tone}>{o.label}</Chip></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onClick={() => void list.loadMore()} />
        </div>
      )}
    </>
  );
}
