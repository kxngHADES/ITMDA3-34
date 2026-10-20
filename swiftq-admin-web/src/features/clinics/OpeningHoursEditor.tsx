"use client";

import { WEEKDAYS, type HoursRow } from "@/lib/hours";
import type { Errors } from "@/lib/validation";

interface Props {
  rows: HoursRow[];
  errors: Errors;
  showErrors: boolean;
  onChange: (rows: HoursRow[]) => void;
}

export function OpeningHoursEditor({ rows, errors, showErrors, onChange }: Props) {
  function update(day: string, patch: Partial<HoursRow>) {
    onChange(rows.map((r) => (r.day === day ? { ...r, ...patch } : r)));
  }
  return (
    <fieldset>
      <legend>Opening hours</legend>
      <div className="stack">
        {rows.map((row) => {
          const label = WEEKDAYS.find((d) => d.key === row.day)?.label ?? row.day;
          const error = showErrors ? errors[`hours.${row.day}`] : undefined;
          return (
            <div key={row.day}>
              <div className="hours-row">
                <label className="check">
                  <input type="checkbox" checked={row.open} onChange={(e) => update(row.day, { open: e.target.checked })} />
                  <span>{label}</span>
                </label>
                <input
                  className="input"
                  type="time"
                  aria-label={`${label} opens`}
                  value={row.opens}
                  disabled={!row.open}
                  aria-invalid={error ? true : undefined}
                  onChange={(e) => update(row.day, { opens: e.target.value })}
                />
                <input
                  className="input"
                  type="time"
                  aria-label={`${label} closes`}
                  value={row.closes}
                  disabled={!row.open}
                  aria-invalid={error ? true : undefined}
                  onChange={(e) => update(row.day, { closes: e.target.value })}
                />
              </div>
              {error && <p className="field-error" role="alert">{error}</p>}
            </div>
          );
        })}
      </div>
      {showErrors && errors.hours && <p className="field-error" role="alert">{errors.hours}</p>}
    </fieldset>
  );
}
