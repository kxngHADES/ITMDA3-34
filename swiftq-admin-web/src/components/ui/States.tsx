import type { ReactNode } from "react";
import { Button } from "./Button";

export function LoadingBlock({ label = "Loading" }: { label?: string }) {
  return (
    <div className="loading-block" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorPanel({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty" role="alert">
      <h2>That didn&apos;t load</h2>
      <p>{message}</p>
      {onRetry && <Button variant="primary" onClick={onRetry}>Try again</Button>}
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <h2>{title}</h2>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

/** The "Load more" control under a paged table. */
export function LoadMore({ hasMore, loading, error, onClick }: { hasMore: boolean; loading: boolean; error: string | null; onClick: () => void }) {
  if (!hasMore && !error) return null;
  return (
    <div className="load-more">
      {error && <p className="field-error" role="alert">{error}</p>}
      {hasMore && <Button loading={loading} onClick={onClick}>Load more</Button>}
    </div>
  );
}
