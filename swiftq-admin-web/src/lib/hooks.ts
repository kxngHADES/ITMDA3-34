"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { Page } from "@/lib/api/types";
import { errorMessage } from "@/lib/errors";

/** Load one thing. Pass a function wrapped in useCallback: when it changes, the data reloads. */
export function useLoad<T>(load: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const run = useRef(0);

  const reload = useCallback(async () => {
    const id = ++run.current;
    setLoading(true);
    setError(null);
    try {
      const result = await load();
      if (id === run.current) setData(result);
    } catch (e) {
      if (id === run.current) setError(errorMessage(e));
    } finally {
      if (id === run.current) setLoading(false);
    }
  }, [load]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { data, loading, error, reload };
}

/** Load a list one page at a time (API contract 2.7: limit, cursor, has_more). */
export function usePagedList<T>(load: (cursor?: string) => Promise<Page<T>>) {
  const [items, setItems] = useState<T[]>([]);
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [moreError, setMoreError] = useState<string | null>(null);
  const run = useRef(0);

  const reload = useCallback(async () => {
    const id = ++run.current;
    setLoading(true);
    setError(null);
    setMoreError(null);
    try {
      const page = await load();
      if (id !== run.current) return;
      setItems(page.items);
      setHasMore(page.has_more ?? false);
      setCursor(page.next_cursor ?? undefined);
    } catch (e) {
      if (id === run.current) setError(errorMessage(e));
    } finally {
      if (id === run.current) setLoading(false);
    }
  }, [load]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const loadMore = useCallback(async () => {
    if (!hasMore || !cursor) return;
    const id = run.current;
    setLoadingMore(true);
    setMoreError(null);
    try {
      const page = await load(cursor);
      if (id !== run.current) return;
      setItems((prev) => [...prev, ...page.items]);
      setHasMore(page.has_more ?? false);
      setCursor(page.next_cursor ?? undefined);
    } catch (e) {
      if (id === run.current) setMoreError(errorMessage(e));
    } finally {
      setLoadingMore(false);
    }
  }, [load, hasMore, cursor]);

  return { items, hasMore, loading, loadingMore, error, moreError, reload, loadMore };
}

/** Track which fields a person has touched, so errors appear after they leave a field or press Save. */
export function useFormState<T extends object>(initial: T) {
  const [values, setValues] = useState<T>(initial);
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [submitted, setSubmitted] = useState(false);

  const set = useCallback(<K extends keyof T>(key: K, value: T[K]) => {
    setValues((v) => ({ ...v, [key]: value }));
  }, []);
  const touch = useCallback((key: string) => setTouched((t) => ({ ...t, [key]: true })), []);
  const show = useCallback(
    (key: string, errors: Record<string, string>): string | undefined =>
      submitted || touched[key] ? errors[key] : undefined,
    [submitted, touched],
  );

  return { values, set, setValues, touch, show, submitted, setSubmitted };
}
