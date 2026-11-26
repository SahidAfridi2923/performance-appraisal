import { useCallback, useEffect, useState } from 'react';
import { api } from './api';

/** Loads a GET endpoint; returns { data, error, loading, reload, setData }. */
export function useApi(path, deps = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(!!path);

  const reload = useCallback(() => {
    if (!path) return Promise.resolve();
    setLoading(true);
    return api
      .get(path)
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, ...deps]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { data, error, loading, reload, setData };
}
