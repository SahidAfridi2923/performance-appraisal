import { useState } from 'react';
import { useAuth } from '../auth';
import { Alert } from '../components/ui';
import Icon from '../components/Icon';

// bar heights + colours for the 1-10 scale illustration
const BARS = [18, 26, 34, 42, 52, 60, 70, 80, 90, 100];
const barColor = (i) => ['#dc2626', '#dc2626', '#ea580c', '#ea580c', '#d97706', '#d97706', '#65a30d', '#65a30d', '#16a34a', '#16a34a'][i];

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
    <div className="login">
      <section className="login-art" aria-hidden="true">
        <div className="brand" style={{ padding: 0 }}>
          <span className="brand-mark">
            <Icon name="target" size={18} style={{ color: '#fff' }} />
          </span>
          <span>
            Appraisal
            <small>Performance reviews</small>
          </span>
        </div>
        <div>
          <h2>Fair reviews, scored the same way for everyone.</h2>
          <p>Self, peer, team lead and manager reviews combine into one score out of 10, with questions your admins keep up to date.</p>
        </div>
        <div className="scale-art">
          {BARS.map((h, i) => (
            <div key={i} style={{ height: `${h}%`, background: barColor(i), opacity: 0.35 + i * 0.065 }}>
              <span>{i + 1}</span>
            </div>
          ))}
        </div>
      </section>
      <section className="login-form">
        <form className="login-card stack" onSubmit={submit}>
          <div>
            <h1>Welcome back</h1>
            <div className="muted">Sign in to your appraisal workspace.</div>
          </div>
          <Alert>{error}</Alert>
          <label className="field">
            Username
            <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus autoComplete="username" autoCapitalize="none" required />
          </label>
          <label className="field">
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
          </label>
          <button className="btn" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
          <div className="muted small">Forgot your password? Ask your admin to reset it.</div>
        </form>
      </section>
    </div>
  );
}
