import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";

interface ShellProps {
  label: string;
  help?: string;
  error?: string;
  optional?: boolean;
  className?: string;
}

function describedBy(id: string, help?: string, error?: string): string | undefined {
  const ids = [help ? `${id}-help` : "", error ? `${id}-error` : ""].filter(Boolean).join(" ");
  return ids || undefined;
}

function Shell({ id, label, help, error, optional, className = "", children }: ShellProps & { id: string; children: ReactNode }) {
  return (
    <div className={`field ${className}`.trim()}>
      <label htmlFor={id}>
        {label}
        {optional && <span className="optional"> (optional)</span>}
      </label>
      {children}
      {help && <p className="field-help" id={`${id}-help`}>{help}</p>}
      {error && <p className="field-error" id={`${id}-error`} role="alert">{error}</p>}
    </div>
  );
}

export function TextField({ label, help, error, optional, className, ...input }: ShellProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <Shell id={id} label={label} help={help} error={error} optional={optional} className={className}>
      <input {...input} id={id} className="input" aria-invalid={error ? true : undefined} aria-describedby={describedBy(id, help, error)} />
    </Shell>
  );
}

export function TextAreaField({ label, help, error, optional, className, ...input }: ShellProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <Shell id={id} label={label} help={help} error={error} optional={optional} className={className}>
      <textarea {...input} id={id} className="textarea" aria-invalid={error ? true : undefined} aria-describedby={describedBy(id, help, error)} />
    </Shell>
  );
}

export interface Option {
  value: string;
  label: string;
}

export function SelectField({
  label, help, error, optional, className, options, placeholder, ...select
}: ShellProps & SelectHTMLAttributes<HTMLSelectElement> & { options: Option[]; placeholder?: string }) {
  const id = useId();
  return (
    <Shell id={id} label={label} help={help} error={error} optional={optional} className={className}>
      <select {...select} id={id} className="select" aria-invalid={error ? true : undefined} aria-describedby={describedBy(id, help, error)}>
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </Shell>
  );
}

export function CheckboxField({ label, ...input }: { label: string } & Omit<InputHTMLAttributes<HTMLInputElement>, "type">) {
  return (
    <label className="check">
      <input {...input} type="checkbox" />
      <span>{label}</span>
    </label>
  );
}
