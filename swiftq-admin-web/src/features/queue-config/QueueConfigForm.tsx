"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { updateQueueConfig } from "@/lib/api/endpoints";
import type { QueueConfig } from "@/lib/api/types";
import { focusFirstInvalid } from "@/lib/dom";
import { errorMessage } from "@/lib/errors";
import { useFormState } from "@/lib/hooks";
import {
  DEFAULT_QUEUE_CONFIG,
  configToForm,
  parseWholeNumber,
  validateQueueConfig,
  type QueueConfigFormValues,
} from "@/lib/validation";

interface Props {
  servicePointId: string;
  initial: QueueConfig;
  onSaved: (saved: QueueConfig) => void;
}

export function QueueConfigForm({ servicePointId, initial, onSaved }: Props) {
  const toast = useToast();
  const formRef = useRef<HTMLFormElement>(null);
  const [baseline, setBaseline] = useState<QueueConfigFormValues>(() => configToForm(initial));
  const form = useFormState<QueueConfigFormValues>(baseline);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const { values, set, setValues, touch, show } = form;

  const { errors, value } = validateQueueConfig(values);
  const dirty = (Object.keys(baseline) as (keyof QueueConfigFormValues)[]).some((k) => baseline[k] !== values[k]);

  // Warn before leaving the page with changes that were not saved.
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    form.setSubmitted(true);
    setFormError(null);
    if (!value) {
      focusFirstInvalid(formRef.current);
      return;
    }
    setBusy(true);
    try {
      // PUT replaces the whole configuration, so every field is sent.
      const saved = await updateQueueConfig(servicePointId, value);
      const next = configToForm(saved);
      setBaseline(next);
      setValues(next);
      toast.success("Queue settings saved. They apply to each new day's queue.");
      onSaved(saved);
    } catch (err) {
      setFormError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const field = (key: keyof QueueConfigFormValues, label: string, help?: string) => (
    <TextField
      label={label}
      help={help}
      inputMode="decimal"
      value={values[key]}
      onChange={(e) => set(key, e.target.value)}
      onBlur={() => touch(key)}
      error={show(key, errors)}
      autoComplete="off"
    />
  );

  const baseSeconds = parseWholeNumber(values.baseSeconds);

  return (
    <form ref={formRef} onSubmit={onSubmit} noValidate className="stack-lg">
      {formError && <div className="banner banner-error" role="alert">{formError}</div>}

      <section className="card stack" aria-labelledby="priority-heading">
        <div>
          <h2 id="priority-heading">Priority</h2>
          <p className="muted">A patient with a confirmed priority moves forward by their group&apos;s weight. Higher numbers move them further.</p>
        </div>
        <div className="form-grid">
          {field("elderly", "Elderly weight")}
          {field("pregnant", "Pregnant weight")}
          {field("disability", "Disability weight")}
          {field("urgent", "Urgent weight")}
          {field("maxJump", "Most places someone can move forward", "No priority patient moves further than this.")}
          {field("starvationMinutes", "Protect waiting patients after (minutes)", "Nobody who has waited this long can be overtaken.")}
          {field("latePenalty", "Late return penalty (places)", "How far back a patient goes if they return after a no-show.")}
        </div>
      </section>

      <section className="card stack" aria-labelledby="wait-heading">
        <div>
          <h2 id="wait-heading">Wait-time estimates</h2>
          <p className="muted">Patients see a range, worked out from how long recent consultations really took.</p>
        </div>
        <div className="form-grid">
          {field(
            "baseSeconds",
            "Starting consultation time (seconds)",
            baseSeconds !== null && baseSeconds >= 60 ? `That is ${Math.round((baseSeconds / 60) * 10) / 10} minutes. Used until enough real consultations are recorded.` : "At least 60 seconds.",
          )}
          {field("minSamples", "Consultations needed before using real times")}
          {field("emaAlpha", "How quickly estimates follow recent consultations", "From 0.01 to 1. A higher number reacts faster. The default is 0.2.")}
        </div>
      </section>

      <div className="form-actions" style={{ marginTop: 0 }}>
        <Button
          onClick={() => {
            setValues(configToForm(DEFAULT_QUEUE_CONFIG));
            form.setSubmitted(false);
          }}
          disabled={busy}
        >
          Reset to defaults
        </Button>
        <Button type="submit" variant="primary" size="lg" loading={busy} disabled={!dirty}>
          Save settings
        </Button>
      </div>
    </form>
  );
}
