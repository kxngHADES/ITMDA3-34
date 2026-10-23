"use client";

import { useRef, useState, type FormEvent } from "react";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { createServicePoint } from "@/lib/api/endpoints";
import type { ServicePoint } from "@/lib/api/types";
import { focusFirstInvalid } from "@/lib/dom";
import { errorMessage } from "@/lib/errors";
import { parseWholeNumber, validateServicePointForm, type ServicePointFormValues } from "@/lib/validation";
import { useFormState } from "@/lib/hooks";

interface Props {
  open: boolean;
  clinicId: string;
  servicePoint: ServicePoint | null; // null means "add a service point"
  onClose: () => void;
  onSaved: (message: string) => void;
}

export function ServicePointFormModal({ open, clinicId, servicePoint, onClose, onSaved }: Props) {
  return (
    <Modal open={open} title={servicePoint ? "Edit service point" : "Add a service point"} onClose={onClose}>
      <ServicePointForm key={servicePoint?.id ?? "new"} clinicId={clinicId} servicePoint={servicePoint} onClose={onClose} onSaved={onSaved} />
    </Modal>
  );
}

function ServicePointForm({ clinicId, servicePoint, onClose, onSaved }: Omit<Props, "open">) {
  const formRef = useRef<HTMLFormElement>(null);
  const form = useFormState<ServicePointFormValues>({
    name: servicePoint?.name ?? "",
    icon: servicePoint?.icon ?? "",
    servers: String(servicePoint?.concurrent_servers ?? 1),
  });
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const { values, set, touch, show } = form;
  const errors = validateServicePointForm(values);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    form.setSubmitted(true);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      focusFirstInvalid(formRef.current);
      return;
    }
    const name = values.name.trim();
    const icon = values.icon.trim();
    const servers = parseWholeNumber(values.servers) as number;
    setBusy(true);
    try {
      await createServicePoint(clinicId, { name, icon, concurrent_servers: servers });
      onSaved(`${name} was added.`);
    } catch (err) {
      setFormError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <form ref={formRef} onSubmit={onSubmit} noValidate>
      <div className="stack">
        {formError && <div className="banner banner-error" role="alert">{formError}</div>}
        <TextField label="Name" value={values.name} onChange={(e) => set("name", e.target.value)} onBlur={() => touch("name")} error={show("name", errors)} help="What patients see, like General consultation." autoComplete="off" />
        <TextField label="Icon name" value={values.icon} onChange={(e) => set("icon", e.target.value)} onBlur={() => touch("icon")} error={show("icon", errors)} help="The name of an icon from the shared icon set, like stethoscope." autoComplete="off" />
        <TextField label="Staff serving at the same time" inputMode="numeric" value={values.servers} onChange={(e) => set("servers", e.target.value)} onBlur={() => touch("servers")} error={show("servers", errors)} help="From 1 to 20. Wait times are worked out from this number." autoComplete="off" />
      </div>
      <div className="form-actions">
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button type="submit" variant="primary" loading={busy}>{servicePoint ? "Save changes" : "Add service point"}</Button>
      </div>
    </form>
  );
}
