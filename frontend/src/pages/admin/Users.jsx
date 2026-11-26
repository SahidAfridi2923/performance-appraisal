import { useMemo, useState } from 'react';
import { api } from '../../api';
import { useApi } from '../../hooks';
import { Alert, Loading, Modal, useAction } from '../../components/ui';

const ROLES = ['EMPLOYEE', 'TL', 'MANAGER', 'ADMIN'];
const ROLE_HELP = {
  EMPLOYEE: 'Self review, peer reviews in own team',
  TL: 'TL reviews + lead actions for own / managed teams',
  MANAGER: 'Manager reviews + lead actions for own / managed teams',
  ADMIN: 'Manage questions, scoring, cycles, users',
};

function UserModal({ user, teams, levels, onClose, onSaved }) {
  const [u, setU] = useState({
    username: '',
    fullName: '',
    email: '',
    active: true,
    appraised: true,
    roles: ['EMPLOYEE'],
    teamId: '',
    levelId: '',
    managedTeamIds: [],
    password: '',
    ...user,
  });
  const action = useAction();
  const toggle = (key, v) => setU((x) => ({ ...x, [key]: x[key].includes(v) ? x[key].filter((y) => y !== v) : [...x[key], v] }));
  const save = async (e) => {
    e.preventDefault();
    const body = {
      ...u,
      teamId: u.teamId ? Number(u.teamId) : null,
      levelId: u.levelId ? Number(u.levelId) : null,
      email: u.email || null,
      password: u.password || null,
    };
    const r = await action.run(() => (u.id ? api.put(`/admin/users/${u.id}`, body) : api.post('/admin/users', body)));
    if (r) onSaved();
  };
  const isLead = u.roles.includes('TL') || u.roles.includes('MANAGER');
  return (
    <Modal title={u.id ? `Edit ${u.fullName}` : 'Add user'} onClose={onClose} wide>
      <form className="stack" onSubmit={save}>
        <Alert>{action.error}</Alert>
        <div className="form-grid">
          <label className="field">
            Full name
            <input value={u.fullName} onChange={(e) => setU({ ...u, fullName: e.target.value })} required autoFocus />
          </label>
          <label className="field">
            Username
            <input value={u.username} onChange={(e) => setU({ ...u, username: e.target.value })} required autoComplete="off" />
          </label>
          <label className="field">
            Email
            <input type="email" value={u.email || ''} onChange={(e) => setU({ ...u, email: e.target.value })} />
          </label>
          <label className="field">
            {u.id ? 'New password (leave blank to keep)' : 'Password (min 8 characters)'}
            <input type="password" value={u.password} minLength={8} onChange={(e) => setU({ ...u, password: e.target.value })} required={!u.id} autoComplete="new-password" />
          </label>
          <label className="field">
            Team (appraised in)
            <select value={u.teamId || ''} onChange={(e) => setU({ ...u, teamId: e.target.value })}>
              <option value="">– none –</option>
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            Level
            <select value={u.levelId || ''} onChange={(e) => setU({ ...u, levelId: e.target.value })}>
              <option value="">– none –</option>
              {levels.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="field">
          Roles
          <div className="checks">
            {ROLES.map((r) => (
              <label key={r} title={ROLE_HELP[r]}>
                <input type="checkbox" checked={u.roles.includes(r)} onChange={() => toggle('roles', r)} /> {r}
              </label>
            ))}
          </div>
        </div>
        {isLead && (
          <div className="field">
            Also leads these teams (besides the home team)
            <div className="checks">
              {teams.map((t) => (
                <label key={t.id}>
                  <input type="checkbox" checked={u.managedTeamIds.includes(t.id)} onChange={() => toggle('managedTeamIds', t.id)} /> {t.name}
                </label>
              ))}
            </div>
          </div>
        )}
        <div className="checks">
          <label>
            <input type="checkbox" checked={u.appraised} onChange={(e) => setU({ ...u, appraised: e.target.checked })} /> Is appraised (gets an appraisal when a cycle opens)
          </label>
          <label>
            <input type="checkbox" checked={u.active} onChange={(e) => setU({ ...u, active: e.target.checked })} /> Active (can sign in)
          </label>
        </div>
        <div className="modal-foot">
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn" disabled={action.busy || u.roles.length === 0}>
            Save user
          </button>
        </div>
      </form>
    </Modal>
  );
}

export default function Users() {
  const { data: users, error, reload } = useApi('/admin/users');
  const { data: teams } = useApi('/admin/teams');
  const { data: levels } = useApi('/admin/levels');
  const [modal, setModal] = useState(null);
  const [filter, setFilter] = useState('');
  const shown = useMemo(
    () => (users || []).filter((u) => !filter || `${u.fullName} ${u.username} ${u.teamName || ''}`.toLowerCase().includes(filter.toLowerCase())),
    [users, filter]
  );
  const teamName = (id) => teams?.find((t) => t.id === id)?.name;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Users</h1>
          <div className="muted">People, their team, level and roles. Reviewing is team-wide: peers, TLs and Managers of a team can review its members.</div>
        </div>
        <div className="row">
          <input placeholder="Search…" value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Search users" />
          <button className="btn" onClick={() => setModal({})}>
            + User
          </button>
        </div>
      </div>
      <Alert>{error}</Alert>
      {!users || !teams || !levels ? (
        <Loading />
      ) : (
        <div className="card">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Username</th>
                  <th>Team</th>
                  <th>Level</th>
                  <th>Roles</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {shown.map((u) => (
                  <tr key={u.id}>
                    <td>{u.fullName}</td>
                    <td>{u.username}</td>
                    <td>
                      {u.teamName || '–'}
                      {u.managedTeamIds.length > 0 && <div className="muted small">also leads: {u.managedTeamIds.map(teamName).join(', ')}</div>}
                    </td>
                    <td>{u.levelName || '–'}</td>
                    <td>
                      <div className="badge-row">
                        {u.roles.map((r) => (
                          <span key={r} className={`pill ${r === 'ADMIN' ? 'blue' : ''}`}>
                            {r}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td>
                      {u.active ? <span className="pill ok">Active</span> : <span className="pill">Inactive</span>}
                      {!u.appraised && <div className="muted small">not appraised</div>}
                    </td>
                    <td className="right">
                      <button className="link" onClick={() => setModal({ ...u, password: '' })}>
                        Edit
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {modal && (
        <UserModal
          user={modal}
          teams={teams}
          levels={levels.filter((l) => l.active)}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null);
            reload();
          }}
        />
      )}
    </div>
  );
}
