import { useEffect, useState } from 'react';
import { ratingClass, STATUS_LABEL } from '../format';

export function Alert({ kind = 'error', children, onClose }) {
  if (!children) return null;
  return (
    <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <span>{children}</span>
      {onClose && (
        <button className="link" onClick={onClose} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  );
}

export function Loading({ what = 'Loading' }) {
  return <div className="muted pad">{what}…</div>;
}

export function Rating({ rating }) {
  if (!rating) return <span className="muted">–</span>;
  return <span className={`pill ${ratingClass(rating)}`}>{rating}</span>;
}

export function ScoreStatus({ status }) {
  const cls = status === 'SCORED' ? 'pill ok' : status === 'INCOMPLETE' ? 'pill warn' : 'pill';
  return <span className={cls}>{STATUS_LABEL[status] || status}</span>;
}

export function Modal({ title, onClose, children, wide }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="link" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

/** Runs an async action, shows its error, disables while busy. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const run = async (fn, okMessage) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const r = await fn();
      if (okMessage) setNotice(okMessage);
      return r;
    } catch (e) {
      setError(e.message);
      return undefined;
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, notice, setError, setNotice, run };
}
