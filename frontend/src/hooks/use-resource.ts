"use client";

import { useCallback, useEffect, useState } from "react";

export function useResource<T>(loader: () => Promise<T>, dependencies: readonly unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);

  const reload = useCallback(() => {
    setLoading(true);
    setError(null);
    setRevision((value) => value + 1);
  }, []);

  useEffect(() => {
    let active = true;
    loader().then(
      (value) => {
        if (!active) return;
        setData(value);
        setLoading(false);
      },
      (reason) => {
        if (!active) return;
        setError(reason);
        setLoading(false);
      },
    );
    return () => {
      active = false;
    };
    // The caller supplies the exact values that invalidate its loader.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencies, revision]);

  return { data, error, loading, reload };
}
