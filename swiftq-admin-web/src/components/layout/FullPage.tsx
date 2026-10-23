"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";

/** Shown while we check who is signed in. The API can take up to a minute to wake up, so say so. */
export function FullPageLoading() {
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setSlow(true), 4000);
    return () => clearTimeout(t);
  }, []);
  return (
    <div className="fullpage">
      <div className="panel" role="status">
        <span className="spinner" aria-hidden="true" />
        <p>Loading</p>
        {slow && <p className="muted">The server is waking up. The first load can take up to a minute.</p>}
      </div>
    </div>
  );
}

export function FullPageError({ message, onRetry, onSignOut }: { message: string; onRetry: () => void; onSignOut: () => void }) {
  return (
    <div className="fullpage">
      <div className="panel" role="alert">
        <h1 className="sq-text-title-3">We couldn&apos;t load your account</h1>
        <p className="muted">{message}</p>
        <div className="row">
          <Button variant="primary" onClick={onRetry}>Try again</Button>
          <Button onClick={onSignOut}>Sign out</Button>
        </div>
      </div>
    </div>
  );
}
