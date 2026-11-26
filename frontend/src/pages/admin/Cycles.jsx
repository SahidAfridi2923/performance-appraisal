import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import { useApi } from '../../hooks';
import { Alert, Loading, useAction } from '../../components/ui';
import { date } from '../../format';

const STATUS_PILL = { DRAFT: 'pill', OPEN: 'pill ok', CLOSED: 'pill blue' };

export default function Cycles() {
  const { data, error, reload } = useApi('/admin/cycles');
  const [name, setName] = useState('');
  const action = useAction();

  const create = async (e) => {
    e.preventDefault();
    if (await action.run(() => api.post('/admin/cycles', { name }), 'Cycle created as a draft. Open it when the question bank is ready.')) {
      setName('');
      reload();
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Appraisal cycles</h1>
          <div className="muted">
            Opening a cycle freezes a copy of the current questions and weights, so later edits never change its results (unless you sync them in).
          </div>
        </div>
        <form className="row" onSubmit={create}>
          <input placeholder="e.g. Jul–Sep 2026" value={name} onChange={(e) => setName(e.target.value)} required aria-label="New cycle name" />
          <button className="btn" disabled={action.busy}>
            + New cycle
          </button>
        </form>
      </div>
      <Alert>{error || action.error}</Alert>
      <Alert kind="ok">{action.notice}</Alert>
      {!data ? (
        <Loading />
      ) : (
        <div className="card">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Cycle</th>
                  <th>Status</th>
                  <th className="num">People</th>
                  <th>Opened</th>
                  <th>Closed</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted">
                      No cycles yet.
                    </td>
                  </tr>
                )}
                {data.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <Link to={`/admin/cycles/${c.id}`}>{c.name}</Link>
                    </td>
                    <td>
                      <span className={STATUS_PILL[c.status]}>{c.status}</span>
                    </td>
                    <td className="num">{c.appraisalCount}</td>
                    <td>{date(c.openedAt)}</td>
                    <td>{date(c.closedAt)}</td>
                    <td className="right">
                      <Link to={`/admin/cycles/${c.id}`}>Manage</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
