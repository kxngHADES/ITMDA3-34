"use client";

import { useRef, useState, type FormEvent } from "react";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { createClinic } from "@/lib/api/endpoints";
import type { Clinic } from "@/lib/api/types";
import { focusFirstInvalid } from "@/lib/dom";
import { errorMessage } from "@/lib/errors";
import { toEntries, toRows, type HoursRow } from "@/lib/hours";
import { useFormState } from "@/lib/hooks";
import { validateClinicForm, type ClinicFormValues } from "@/lib/validation";
import { OpeningHoursEditor } from "./OpeningHoursEditor";

interface Props {
  open: boolean;
  clinic: Clinic | null; // null means "add a clinic"
  onClose: () => void;
  onSaved: (message: string, saved: Clinic) => void;
}

export function ClinicFormModal({ open, clinic, onClose, onSaved }: Props) {
  return (
    <Modal open={open} wide title={clinic ? "Edit clinic" : "Add a clinic"} onClose={onClose}>
      <ClinicForm key={clinic?.id ?? "new"} clinic={clinic} onClose={onClose} onSaved={onSaved} />
    </Modal>
  );
}

function ClinicForm({ clinic, onClose, onSaved }: Omit<Props, "open">) {
  const formRef = useRef<HTMLFormElement>(null);
  const form = useFormState<ClinicFormValues>({
    name: clinic?.name ?? "",
    address: clinic?.address ?? "",
    hours: toRows(clinic?.opening_hours),
  });
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const { values, set, touch, show, submitted } = form;
  const errors = validateClinicForm(values);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    form.setSubmitted(true);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      focusFirstInvalid(formRef.current);
      return;
    }
    setBusy(true);
    try {
      const body = { name: values.name.trim(), address: values.address.trim(), opening_hours: toEntries(values.hours) };
      const saved = await createClinic(body);
      onSaved(clinic ? `${body.name} was saved.` : `${body.name} was added. Add its service points next.`, saved);
    } catch (err) {
      setFormError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <form ref={formRef} onSubmit={onSubmit} noValidate>
      <div className="stack">
        {formError && <div className="banner banner-error" role="alert">{formError}</div>}
        <TextField label="Clinic name" value={values.name} onChange={(e) => set("name", e.target.value)} onBlur={() => touch("name")} error={show("name", errors)} autoComplete="off" />
        <TextField label="Address" value={values.address} onChange={(e) => set("address", e.target.value)} onBlur={() => touch("address")} error={show("address", errors)} autoComplete="off" />
        <OpeningHoursEditor rows={values.hours} errors={errors} showErrors={submitted} onChange={(rows: HoursRow[]) => set("hours", rows)} />
      </div>
      <div className="form-actions">
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button type="submit" variant="primary" loading={busy}>{clinic ? "Save changes" : "Add clinic"}</Button>
      </div>
    </form>
  );
}
