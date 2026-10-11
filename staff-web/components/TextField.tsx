import type { InputHTMLAttributes } from "react";

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  error?: string;
}

export function TextField({ label, hint, error, id, ...rest }: TextFieldProps) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;

  return (
    <div className="mb-[14px] flex flex-col gap-[5px]">
      <label htmlFor={id} className="text-[0.84rem] font-semibold">
        {label}
      </label>
      <input
        id={id}
        aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
        aria-invalid={Boolean(error)}
        className="min-h-[var(--swiftq-target)] w-full rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface px-3 py-2.5 text-[0.95rem] text-swiftq-text placeholder:text-swiftq-muted/70 focus:border-swiftq-focus"
        {...rest}
      />
      {hint && !error && (
        <span id={hintId} className="text-[0.78rem] text-swiftq-muted">
          {hint}
        </span>
      )}
      {error && (
        <span
          id={errorId}
          role="alert"
          className="text-[0.84rem] font-semibold text-swiftq-danger"
        >
          {error}
        </span>
      )}
    </div>
  );
}
