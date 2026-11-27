import { useEffect, useState } from 'react';
import { ratingClass, STATUS_LABEL } from '../format';
import Icon from './Icon';

export function Alert({ kind = 'error', children, onClose }) {
  if (!children) return null;
  return (
    <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <span>{children}</span>
      {onClose && (
        <button className="link" onClick={onClose} aria-label="Dismiss">
          <Icon name="close" size={16} />
        </button>
      )}
    </div>
  );
}

export function Loading({ what = 'Loading' }) {
  return (
    <div className="muted pad" role="status" aria-live="polite">
      {what}…
    </div>
  );
}

/** Page title block used at the top of every page. */
export function PageHead({ title, sub, crumb, children }) {
  return (
    <div className="page-head">
      <div>
        {crumb}
        <h1>{title}</h1>
        {sub && <div className="sub">{sub}</div>}
      </div>
      {children && <div className="row">{children}</div>}
    </div>
  );
}

export function Rating({ rating }) {
  if (!rating) return <span className="muted">–</span>;
  return <span className={`pill ${ratingClass(rating)}`}>{rating}</span>;
}

const SHORT = { NOT_STARTED: 'Not started', SELECT_LEVEL: 'No level', INCOMPLETE: 'Incomplete', CHECK_ANSWERS: 'Check answers', SCORED: 'Scored' };

export function ScoreStatus({ status, short }) {
  const cls = status === 'SCORED' ? 'pill ok' : status === 'INCOMPLETE' ? 'pill accent' : 'pill';
  return (
    <span className={cls} title={STATUS_LABEL[status]}>
      {(short ? SHORT : STATUS_LABEL)[status] || status}
    </span>
  );
}

export function Modal({ title, onClose, children, wide }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  useEffect(() => {
    // the page behind a dialog must not scroll
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="link" onClick={onClose} aria-label="Close">
            <Icon name="close" />
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
