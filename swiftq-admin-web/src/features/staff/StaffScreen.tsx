"use client";

import { useCallback, useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { SelectField, CheckboxField, type Option } from "@/components/ui/Field";
import { EmptyState, ErrorPanel, LoadMore, LoadingBlock } from "@/components/ui/States";
import { useToast } from "@/components/ui/Toast";
import { deactivateStaff, fetchAll, listClinics, listStaff, updateStaff } from "@/lib/api/endpoints";
import type { StaffAccount, StaffRole } from "@/lib/api/types";
import { ROLE_LABEL, assignableRoles } from "@/lib/auth/permissions";
import { useMe } from "@/lib/auth/AuthProvider";
import { errorMessage } from "@/lib/errors";
import { formatDateTime } from "@/lib/format";
import { useLoad, usePagedList } from "@/lib/hooks";
import { StaffFormModal } from "./StaffFormModal";

const ROLE_FILTER: Option[] = (["receptionist", "nurse", "manager", "it_admin"] as StaffRole[]).map((r) => ({ value: r, label: ROLE_LABEL[r] }));

export function StaffScreen() {
  const toast = useToast();
  const me = useMe();
  const canManage = (person: StaffAccount) => assignableRoles(me.role).includes(person.role);
  const [clinicId, setClinicId] = useState("");
  const [role, setRole] = useState<StaffRole | "">("");
  const [includeInactive, setIncludeInactive] = useState(false);
  const [editing, setEditing] = useState<StaffAccount | "new" | null>(null);
  const [deactivating, setDeactivating] = useState<StaffAccount | null>(null);
  const [busy, setBusy] = useState(false);

  // Clinics fill the filter and the form, and give us names to show instead of ids.
  const loadClinics = useCallback(() => fetchAll(listClinics), []);
  const clinics = useLoad(loadClinics);
  const clinicNames = useMemo(() => new Map((clinics.data ?? []).map((c) => [c.id, c.name])), [clinics.data]);

  const loadPage = useCallback(
    (cursor?: string) =>
      listStaff({
        limit: 25,
        cursor,
        clinic_id: clinicId || undefined,
        role: role || undefined,
        include_inactive: includeInactive || undefined,
      }),
    [clinicId, role, includeInactive],
  );
  const list = usePagedList(loadPage);

  async function confirmDeactivate() {
    if (!deactivating) return;
    setBusy(true);
    try {
      await deactivateStaff(deactivating.id);
      toast.success(`${deactivating.display_name} was deactivated. They can no longer sign in.`);
      setDeactivating(null);
      void list.reload();
    } catch (e) {
      toast.error(errorMessage(e)); // for example CANNOT_DEACTIVATE_SELF
      setDeactivating(null);
    } finally {
      setBusy(false);
    }
  }

  async function reactivate(person: StaffAccount) {
    try {
      await updateStaff(person.id, { is_active: true });
      toast.success(`${person.display_name} was reactivated.`);
      void list.reload();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  const clinicOptions: Option[] = (clinics.data ?? []).map((c) => ({ value: c.id, label: c.name }));

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Staff accounts</h1>
          <p>Add staff, change their role or clinic, and deactivate accounts when people leave. Deactivated accounts are kept, so their history is not lost.</p>
        </div>
        <Button variant="primary" size="lg" onClick={() => setEditing("new")} disabled={clinics.loading || !!clinics.error}>
          Add staff member
        </Button>
      </div>

      <div className="filters">
        <SelectField label="Clinic" value={clinicId} options={clinicOptions} placeholder="All clinics" onChange={(e) => setClinicId(e.target.value)} />
        <SelectField label="Role" value={role} options={ROLE_FILTER} placeholder="All roles" onChange={(e) => setRole(e.target.value as StaffRole | "")} />
        <CheckboxField label="Show deactivated accounts" checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />
      </div>

      {clinics.error && <div className="banner banner-error" role="alert" style={{ marginBottom: "var(--sq-space-4)" }}>{clinics.error}</div>}

      {list.loading ? (
        <LoadingBlock label="Loading staff accounts" />
      ) : list.error ? (
        <ErrorPanel message={list.error} onRetry={() => void list.reload()} />
      ) : list.items.length === 0 ? (
        <div className="card">
          <EmptyState title="No staff accounts match" action={<Button variant="primary" onClick={() => setEditing("new")}>Add staff member</Button>}>
            Change the filters, or add the first person for this clinic.
          </EmptyState>
        </div>
      ) : (
        <div className="table-wrap">
          <table>
            <caption className="visually-hidden">Staff accounts</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Role</th>
                <th scope="col">Clinic</th>
                <th scope="col">Status</th>
                <th scope="col">Last sign in</th>
                <th scope="col"><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((person) => (
                <tr key={person.id}>
                  <td>
                    <div className="primary">{person.display_name}</div>
                    <div className="secondary">{person.email}</div>
                  </td>
                  <td>{ROLE_LABEL[person.role]}</td>
                  <td>{clinicNames.get(person.clinic_id) ?? "Unknown clinic"}</td>
                  <td>{person.is_active ? <Chip tone="success">Active</Chip> : <Chip tone="neutral">Deactivated</Chip>}</td>
                  <td className="mono">{formatDateTime(person.last_sign_in_at)}</td>
                  <td>
                    {canManage(person) && (
                      <div className="actions">
                        <Button onClick={() => setEditing(person)} aria-label={`Edit ${person.display_name}`}>Edit</Button>
                        {person.is_active ? (
                          <Button variant="danger" onClick={() => setDeactivating(person)} aria-label={`Deactivate ${person.display_name}`}>Deactivate</Button>
                        ) : (
                          <Button onClick={() => void reactivate(person)} aria-label={`Reactivate ${person.display_name}`}>Reactivate</Button>
                        )}
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onClick={() => void list.loadMore()} />
        </div>
      )}

      <StaffFormModal
        open={editing !== null}
        staff={editing === "new" || editing === null ? null : editing}
        clinics={clinics.data ?? []}
        onClose={() => setEditing(null)}
        onSaved={(message) => {
          toast.success(message);
          setEditing(null);
          void list.reload();
        }}
      />

      <ConfirmDialog
        open={deactivating !== null}
        title={`Deactivate ${deactivating?.display_name ?? "this account"}?`}
        confirmLabel="Deactivate account"
        danger
        busy={busy}
        onConfirm={() => void confirmDeactivate()}
        onCancel={() => setDeactivating(null)}
      >
        <p>They will no longer be able to sign in. Their record and past actions are kept, and you can reactivate the account later.</p>
      </ConfirmDialog>
    </>
  );
}
