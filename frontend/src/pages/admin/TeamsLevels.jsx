import { useState } from 'react';
import { api } from '../../api';
import { useApi } from '../../hooks';
import { Alert, Loading, Modal, useAction } from '../../components/ui';

function TeamModal({ team, onClose, onSaved }) {
  const [t, setT] = useState({ code: '', name: '', sortOrder: 0, active: true, ...team });
  const action = useAction();
  const save = async (e) => {
    e.preventDefault();
    const r = await action.run(() => (t.id ? api.put(`/admin/teams/${t.id}`, t) : api.post('/admin/teams', t)));
    if (r) onSaved();
  };
  return (
    <Modal title={t.id ? 'Edit team' : 'Add team'} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <Alert>{action.error}</Alert>
        <div className="form-grid">
          <label className="field">
            Name
            <input value={t.name} onChange={(e) => setT({ ...t, name: e.target.value })} required autoFocus />
          </label>
          <label className="field">
            Short code
            <input value={t.code} onChange={(e) => setT({ ...t, code: e.target.value })} required maxLength={40} />
          </label>
          <label className="field">
            Order
            <input type="number" value={t.sortOrder} onChange={(e) => setT({ ...t, sortOrder: Number(e.target.value) })} />
          </label>
        </div>
        {t.id ? (
          <div className="checks">
            <label>
              <input type="checkbox" checked={t.active} onChange={(e) => setT({ ...t, active: e.target.checked })} /> Active (inactive teams are left out of new cycles)
            </label>
          </div>
        ) : (
          <div className="muted small">A "Role-specific" category is created for the new team; add its questions on the Questions page.</div>
        )}
        <div className="modal-foot">
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn" disabled={action.busy}>
            Save
          </button>
        </div>
      </form>
    </Modal>
  );
}

function LevelModal({ level, onClose, onSaved }) {
  const [l, setL] = useState({ name: '', sortOrder: 0, skipTlReview: false, active: true, ...level });
  const action = useAction();
  const save = async (e) => {
    e.preventDefault();
    const r = await action.run(() => (l.id ? api.put(`/admin/levels/${l.id}`, l) : api.post('/admin/levels', l)));
    if (r) onSaved();
  };
  return (
    <Modal title={l.id ? 'Edit level' : 'Add level'} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <Alert>{action.error}</Alert>
        <div className="form-grid">
          <label className="field">
            Name
            <input value={l.name} onChange={(e) => setL({ ...l, name: e.target.value })} required autoFocus />
          </label>
          <label className="field">
            Order
            <input type="number" value={l.sortOrder} onChange={(e) => setL({ ...l, sortOrder: Number(e.target.value) })} />
          </label>
        </div>
        <div className="checks">
          <label>
            <input type="checkbox" checked={l.skipTlReview} onChange={(e) => setL({ ...l, skipTlReview: e.target.checked })} /> No TL review for this level (the Manager answers instead, like Team Leads)
          </label>
          {l.id && (
            <label>
              <input type="checkbox" checked={l.active} onChange={(e) => setL({ ...l, active: e.target.checked })} /> Active
            </label>
          )}
        </div>
        {!l.id && <div className="muted small">Category points for the new level are copied from the first level; adjust them on the Questions page.</div>}
        <div className="modal-foot">
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn" disabled={action.busy}>
            Save
          </button>
        </div>
      </form>
    </Modal>
  );
}

export default function TeamsLevels() {
  const teams = useApi('/admin/teams');
  const levels = useApi('/admin/levels');
  const [modal, setModal] = useState(null);
  const action = useAction();

  const remove = async (kind, item) => {
    if (!window.confirm(`Remove ${item.name}? If it is still in use it will be deactivated instead.`)) return;
    const r = await action.run(() => api.del(`/admin/${kind}/${item.id}`));
    if (r) {
      action.setNotice(r.deleted ? `${item.name} deleted.` : `${item.name} is in use, so it was deactivated.`);
      kind === 'teams' ? teams.reload() : levels.reload();
    }
  };
  const done = () => {
    setModal(null);
    teams.reload();
    levels.reload();
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Teams &amp; levels</h1>
        </div>
      </div>
      <Alert onClose={() => action.setError(null)}>{action.error}</Alert>
      <Alert kind="ok" onClose={() => action.setNotice(null)}>
        {action.notice}
      </Alert>
      <div className="card">
        <div className="card-head">
          <h3>Teams</h3>
          <button className="btn small" onClick={() => setModal({ type: 'team', data: {} })}>
            + Team
          </button>
        </div>
        {!teams.data ? (
          <Loading />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Name</th>
                  <th>Code</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {teams.data.map((t) => (
                  <tr key={t.id}>
                    <td>{t.sortOrder}</td>
                    <td>{t.name}</td>
                    <td>{t.code}</td>
                    <td>{t.active ? <span className="pill ok">Active</span> : <span className="pill">Inactive</span>}</td>
                    <td className="right nowrap">
                      <button className="link" onClick={() => setModal({ type: 'team', data: t })}>
                        Edit
                      </button>{' '}
                      ·{' '}
                      <button className="link danger" onClick={() => remove('teams', t)}>
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <div className="card">
        <div className="card-head">
          <h3>Levels</h3>
          <button className="btn small" onClick={() => setModal({ type: 'level', data: {} })}>
            + Level
          </button>
        </div>
        {!levels.data ? (
          <Loading />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Name</th>
                  <th>TL review</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {levels.data.map((l) => (
                  <tr key={l.id}>
                    <td>{l.sortOrder}</td>
                    <td>{l.name}</td>
                    <td>{l.skipTlReview ? 'No (Manager answers)' : 'Yes'}</td>
                    <td>{l.active ? <span className="pill ok">Active</span> : <span className="pill">Inactive</span>}</td>
                    <td className="right nowrap">
                      <button className="link" onClick={() => setModal({ type: 'level', data: l })}>
                        Edit
                      </button>{' '}
                      ·{' '}
                      <button className="link danger" onClick={() => remove('levels', l)}>
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {modal?.type === 'team' && <TeamModal team={modal.data} onClose={() => setModal(null)} onSaved={done} />}
      {modal?.type === 'level' && <LevelModal level={modal.data} onClose={() => setModal(null)} onSaved={done} />}
    </div>
  );
}
