import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "default" | "danger" | "ghost";
type Size = "md" | "sm";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  children: ReactNode;
}

const variants: Record<Variant, string> = {
  primary:
    "bg-swiftq-accent border-swiftq-accent text-swiftq-on-accent hover:bg-swiftq-accent-dk hover:border-swiftq-accent-dk",
  default:
    "bg-swiftq-surface border-swiftq-border text-swiftq-text hover:bg-swiftq-hover",
  danger:
    "bg-swiftq-surface border-swiftq-danger-border text-swiftq-danger hover:bg-swiftq-danger-bg",
  ghost:
    "bg-transparent border-transparent text-swiftq-accent hover:bg-swiftq-accent/10",
};

export function Button({
  variant = "default",
  size = "md",
  className = "",
  children,
  ...rest
}: ButtonProps) {
  const sizeCls =
    size === "sm"
      ? "min-h-[38px] px-3 text-[0.85rem]"
      : "min-h-[var(--swiftq-target)] px-[18px] text-[0.94rem]";

  return (
    <button
      className={`inline-flex cursor-pointer items-center justify-center gap-2 rounded-[var(--swiftq-radius)] border font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${sizeCls} ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}
