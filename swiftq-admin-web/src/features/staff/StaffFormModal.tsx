"use client";

import { useRef, useState, type FormEvent } from "react";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { SelectField, TextField, type Option } from "@/components/ui/Field";
import { createStaff, updateStaff } from "@/lib/api/endpoints";
import type { Clinic, StaffAccount, StaffRole, UpdateStaffRequest } from "@/lib/api/types";
import { ROLE_LABEL, assignableRoles } from "@/lib/auth/permissions";
import { useMe } from "@/lib/auth/AuthProvider";
import { focusFirstInvalid } from "@/lib/dom";
import { errorMessage } from "@/lib/errors";
import { useFormState } from "@/lib/hooks";
import { validateStaffForm, type StaffFormValues } from "@/lib/validation";

interface Props {
  open: boolean;
  /** null means "add a new staff member". */
  staff: StaffAccount | null;
  clinics: Clinic[];
  onClose: () => void;
  onSaved: (message: string) => void;
}

export function StaffFormModal({ open, staff, clinics, onClose, onSaved }: Props) {
  const mode = staff ? "edit" : "create";
  return (
    <Modal open={open} title={mode === "create" ? "Add a staff member" : "Edit staff member"} onClose={onClose}>
      <StaffForm key={staff?.id ?? "new"} staff={staff} clinics={clinics} onClose={onClose} onSaved={onSaved} />
    </Modal>
  );
}

function StaffForm({ staff, clinics, onClose, onSaved }: Omit<Props, "open">) {
  const mode = staff ? "edit" : "create";
  const me = useMe();
  // Managers can only work with receptionists and nurses, inside their own clinic.
  const roleOptions: Option[] = (assignableRoles(me.role) as StaffRole[]).map((r) => ({ value: r, label: ROLE_LABEL[r] }));
  const formRef = useRef<HTMLFormElement>(null);
  const form = useFormState<StaffFormValues>({
    email: staff?.email ?? "",
    displayName: staff?.display_name ?? "",
    role: staff?.role ?? "",
    clinicId: staff?.clinic_id ?? me.clinic_id ?? "",
  });
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const { values, set, touch, show } = form;

  const errors = validateStaffForm(values, mode);
  const roleChanged = mode === "edit" && staff !== null && values.role !== staff.role;
  const clinicOptions: Option[] = clinics.map((c) => ({ value: c.id, label: c.is_active === false ? `${c.name} (deactivated)` : c.name }));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    form.setSubmitted(true);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      focusFirstInvalid(formRef.current);
      return;
    }
    const name = values.displayName.trim();
    setBusy(true);
    try {
      if (mode === "create") {
        await createStaff({
          email: values.email.trim(),
          display_name: name,
          role: values.role as StaffRole,
          clinic_id: values.clinicId,
        });
        onSaved(`${name} was added. A link to set a password was emailed to ${values.email.trim()}.`);
      } else if (staff) {
        // Send only what changed: the API wants at least one field, and a role change signs the person out.
        const changes: UpdateStaffRequest = {};
        if (name !== staff.display_name) changes.display_name = name;
        if (values.role !== staff.role) changes.role = values.role as StaffRole;
        if (values.clinicId !== staff.clinic_id) changes.clinic_id = values.clinicId;
        if (Object.keys(changes).length === 0) {
          onClose();
          return;
        }
        await updateStaff(staff.id, changes);
        onSaved(`${name}'s details were saved.`);
      }
    } catch (err) {
      setFormError(errorMessage(err)); // for example EMAIL_IN_USE arrives as plain language
      setBusy(false);
    }
  }

  return (
    <form ref={formRef} onSubmit={onSubmit} noValidate>
      <div className="stack">
        {formError && <div className="banner banner-error" role="alert">{formError}</div>}
        <TextField
          label="Full name"
          value={values.displayName}
          maxLength={80}
          onChange={(e) => set("displayName", e.target.value)}
          onBlur={() => touch("displayName")}
          error={show("displayName", errors)}
          autoComplete="off"
        />
        <TextField
          label="Email address"
          type="email"
          value={values.email}
          onChange={(e) => set("email", e.target.value)}
          onBlur={() => touch("email")}
          error={show("email", errors)}
          disabled={mode === "edit"}
          help={mode === "edit" ? "An email address can't be changed." : "We'll email a link so they can set a password."}
          autoComplete="off"
        />
        <div className="form-grid">
          <SelectField
            label="Role"
            value={values.role}
            options={roleOptions}
            placeholder="Choose a role"
            onChange={(e) => set("role", e.target.value as StaffRole | "")}
            onBlur={() => touch("role")}
            error={show("role", errors)}
          />
          <SelectField
            label="Clinic"
            value={values.clinicId}
            options={clinicOptions}
            disabled={me.role === "manager"}
            placeholder="Choose a clinic"
            onChange={(e) => set("clinicId", e.target.value)}
            onBlur={() => touch("clinicId")}
            error={show("clinicId", errors)}
          />
        </div>
        {roleChanged && (
          <div className="banner banner-warning" role="status">
            Changing the role signs this person out of every device. They sign in again to get the new role.
          </div>
        )}
      </div>
      <div className="form-actions">
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button type="submit" variant="primary" loading={busy}>
          {mode === "create" ? "Add staff member" : "Save changes"}
        </Button>
      </div>
    </form>
  );
}
