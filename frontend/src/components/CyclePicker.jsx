import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useApi } from '../hooks';

/** Dropdown of open / closed cycles; keeps the selection in ?cycle=. Returns [cycleId, element, cycles]. */
export function useCyclePicker() {
  const { data: cycles, error } = useApi('/cycles');
  const [params, setParams] = useSearchParams();
  const selected = params.get('cycle');

  useEffect(() => {
    if (cycles && cycles.length && !selected) {
      const open = cycles.find((c) => c.status === 'OPEN') || cycles[0];
      setParams({ cycle: String(open.id) }, { replace: true });
    }
  }, [cycles, selected, setParams]);

  const element = cycles && cycles.length > 0 && (
    <label className="field">
      Appraisal cycle
      <select value={selected || ''} onChange={(e) => setParams({ cycle: e.target.value })}>
        {cycles.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name} {c.status === 'CLOSED' ? '(closed)' : ''}
          </option>
        ))}
      </select>
    </label>
  );
  return { cycleId: selected, picker: element, cycles, error };
}
