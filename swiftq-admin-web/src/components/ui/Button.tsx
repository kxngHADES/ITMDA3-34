import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "danger" | "danger-solid" | "ghost";
type Size = "md" | "lg" | "xl";

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  /** Shows a spinner and blocks double clicks while a request is running. */
  loading?: boolean;
}

export function Button({ variant = "secondary", size = "md", loading = false, className = "", children, disabled, type = "button", ...rest }: Props) {
  const sizeClass = size === "md" ? "" : `btn-${size}`;
  return (
    <button
      {...rest}
      type={type}
      className={`btn btn-${variant} ${sizeClass} ${className}`.trim()}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}
