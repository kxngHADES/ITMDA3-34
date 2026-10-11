import type { ReactNode } from "react";

interface CardProps {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function Card({ title, action, children, className = "" }: CardProps) {
  return (
    <section
      className={`overflow-hidden rounded-[var(--swiftq-radius)] border border-swiftq-border bg-swiftq-surface shadow-swiftq ${className}`}
    >
      <header className="flex items-center gap-2.5 border-b border-swiftq-border bg-swiftq-surface-subtle px-4 py-3.5">
        <h2 className="m-0 flex-1 text-[1rem] font-semibold">{title}</h2>
        {action}
      </header>
      <div className="p-4">{children}</div>
    </section>
  );
}
