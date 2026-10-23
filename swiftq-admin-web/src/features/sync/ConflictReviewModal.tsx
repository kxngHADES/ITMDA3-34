"use client";

import { useState, type FormEvent } from "react";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { TextAreaField } from "@/components/ui/Field";
import { resolveConflict } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/client";
import type { ConflictResolution, SyncLogEntry } from "@/lib/api/types";
import { errorMessage } from "@/lib/errors";
import { formatDateTime, shortId } from "@/lib/format";

const CHOICES: { value: ConflictResolution; title: string; body: string }[] = [
  { value: "keep_server", title: "Keep the server copy", body: "The device's change is ignored and the record stays as the server has it." },
  { value: "apply_client", title: "Use the device's change", body: "The record is overwritten with what the device sent." },
  { value: "discard", title: "Discard this entry", body: "Close the conflict without applying the device's change." },
];

function show(value: unknown): string {
  if (value === null || value === undefined) return "None";
  if (typeof value === "string") return value;
  return JSON.stringify(value);
}

interface Props {
  entry: SyncLogEntry | null;
  onClose: () => void;
  onResolved: (message: string) => void;
  /** Called when someone else already resolved it, so the list can refresh. */
  onStale: () => void;
}

export function ConflictReviewModal({ entry, onClose, onResolved, onStale }: Props) {
  return (
    <Modal open={entry !== null} wide title="Review sync conflict" onClose={onClose}>
      {entry && <ConflictForm key={entry.id} entry={entry} onClose={onClose} onResolved={onResolved} onStale={onStale} />}
    </Modal>
  );
}

function ConflictForm({ entry, onClose, onResolved, onStale }: Omit<Props, "entry"> & { entry: SyncLogEntry }) {
  const [choice, setChoice] = useState<ConflictResolution | "">("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [choiceError, setChoiceError] = useState<string | null>(null);

  const conflict = entry.conflict;
  const keys = conflict ? [...new Set([...Object.keys(conflict.client_value), ...Object.keys(conflict.server_value)])] : [];
  const newerIsDevice = conflict ? new Date(conflict.client_updated_at) > new Date(conflict.server_updated_at) : false;
  const noteTooLong = note.length > 280;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!choice) {
      setChoiceError("Choose what to do with this conflict.");
      return;
    }
    if (noteTooLong) return;
    setBusy(true);
    setFormError(null);
    try {
      await resolveConflict(entry.id, choice, note.trim() || undefined);
      onResolved("Conflict resolved.");
    } catch (err) {
      if (err instanceof ApiError && err.code === "CONFLICT_ALREADY_RESOLVED") onStale();
      setFormError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      <div className="stack">
        {formError && <div className="banner banner-error" role="alert">{formError}</div>}
        <p className="muted">
          Device {shortId(entry.device_uid)} sent a change to {entry.entity_type} {shortId(entry.entity_id)} ({entry.op.replace("_", " ")}) at {formatDateTime(entry.created_at)}, and the server copy had changed too.
        </p>
        {conflict ? (
          <>
            <div className="table-wrap" style={{ boxShadow: "none", border: "1px solid var(--sq-border-default)" }}>
              <table className="compare">
                <caption className="visually-hidden">The device and server values side by side</caption>
                <thead>
                  <tr>
                    <th scope="col">Field</th>
                    <th scope="col">Device ({formatDateTime(conflict.client_updated_at)})</th>
                    <th scope="col">Server ({formatDateTime(conflict.server_updated_at)})</th>
                  </tr>
                </thead>
                <tbody>
                  {keys.map((key) => {
                    const differs = show(conflict.client_value[key]) !== show(conflict.server_value[key]);
                    return (
                      <tr key={key} className={differs ? "differs" : undefined}>
                        <td>{key}{differs && <span className="visually-hidden"> (different)</span>}</td>
                        <td>{show(conflict.client_value[key])}</td>
                        <td>{show(conflict.server_value[key])}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <p className="field-help">Rows with a tinted background are the fields that differ. The newer change is the {newerIsDevice ? "device's" : "server's"}.</p>
          </>
        ) : (
          <div className="banner banner-info">This entry has no saved values to compare.</div>
        )}

        <fieldset>
          <legend>What should happen?</legend>
          <div className="stack">
            {CHOICES.map((c) => (
              <label key={c.value} className="radio-card">
                <input type="radio" name="resolution" value={c.value} checked={choice === c.value} onChange={() => { setChoice(c.value); setChoiceError(null); }} />
                <span>
                  <strong>{c.title}</strong>
                  <span className="field-help" style={{ display: "block" }}>{c.body}</span>
                </span>
              </label>
            ))}
          </div>
          {choiceError && <p className="field-error" role="alert">{choiceError}</p>}
        </fieldset>

        <TextAreaField
          label="Note"
          optional
          value={note}
          onChange={(e) => setNote(e.target.value)}
          help={`${note.length} of 280 characters. This is saved with the decision.`}
          error={noteTooLong ? "Use 280 characters or fewer." : undefined}
        />
      </div>
      <div className="form-actions">
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button type="submit" variant="primary" loading={busy}>Resolve conflict</Button>
      </div>
    </form>
  );
}
