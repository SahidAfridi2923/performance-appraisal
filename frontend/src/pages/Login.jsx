import { useState } from 'react';
import { useAuth } from '../auth';
import { Alert } from '../components/ui';

export default function Login() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-wrap">
      <form className="card login-card stack" onSubmit={submit}>
        <div>
          <h1>Performance Appraisal</h1>
          <div className="muted">Sign in to continue</div>
        </div>
        <Alert>{error}</Alert>
        <label className="field">
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus autoComplete="username" required />
        </label>
        <label className="field">
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <button className="btn" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
