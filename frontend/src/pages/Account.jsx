import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, useAction } from '../components/ui';

export default function Account() {
  const { user } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const { busy, error, notice, run } = useAction();

  const submit = async (e) => {
    e.preventDefault();
    const ok = await run(() => api.post('/auth/change-password', { currentPassword: current, newPassword: next }), 'Password changed.');
    if (ok !== undefined) {
      setCurrent('');
      setNext('');
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>{user.fullName}</h1>
          <div className="muted">
            {user.username} · {user.roles.join(', ')}
            {user.teamName && ` · ${user.teamName}`}
            {user.levelName && ` · ${user.levelName}`}
          </div>
        </div>
      </div>
      <form className="card card-body stack" style={{ maxWidth: 420 }} onSubmit={submit}>
        <h3>Change password</h3>
        <Alert>{error}</Alert>
        <Alert kind="ok">{notice}</Alert>
        <label className="field">
          Current password
          <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required autoComplete="current-password" />
        </label>
        <label className="field">
          New password (at least 8 characters)
          <input type="password" value={next} onChange={(e) => setNext(e.target.value)} minLength={8} required autoComplete="new-password" />
        </label>
        <button className="btn" disabled={busy}>Change password</button>
      </form>
    </div>
  );
}
