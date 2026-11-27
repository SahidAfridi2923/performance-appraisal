import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api';
import { useApi } from '../../hooks';
import { Alert, Loading, Modal, useAction } from '../../components/ui';
import { date, pct, ROLE_LABEL, ROLE_ORDER } from '../../format';

function SyncModal({ cycleId, onClose, onApplied }) {
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const action = useAction();
  useEffect(() => {
    // apply=false: the server computes the changes and rolls them back
    api.post(`/admin/cycles/${cycleId}/sync`).then(setReport).catch((e) => setError(e.message));
  }, [cycleId]);
  const apply = async () => {
    const r = await action.run(() => api.post(`/admin/cycles/${cycleId}/sync?apply=true`));
    if (r) onApplied(r);
  };
  const r = report;
  const changes = r
    ? r.questionsAdded + r.questionsUpdated + r.questionsRemoved + r.categoriesAdded + r.categoriesUpdated + r.categoriesRemoved + r.levelsAdded + (r.settingsChanged ? 1 : 0)
    : 0;
  return (
    <Modal title="Sync question bank into this open cycle" onClose={onClose}>
      <div className="stack">
        <Alert>{error || action.error}</Alert>
        {!r ? (
          <Loading what="Comparing" />
        ) : changes === 0 ? (
          <Alert kind="ok">This cycle already matches the question bank.</Alert>
        ) : (
          <>
            <table>
              <tbody>
                <tr><td>Questions added</td><td className="num">{r.questionsAdded}</td></tr>
                <tr><td>Questions changed (text, weight, order, reviewer, levels)</td><td className="num">{r.questionsUpdated}</td></tr>
                <tr><td>Questions removed</td><td className="num">{r.questionsRemoved}</td></tr>
                <tr><td>Categories added / changed / removed</td><td className="num">{r.categoriesAdded} / {r.categoriesUpdated} / {r.categoriesRemoved}</td></tr>
                <tr><td>Levels added</td><td className="num">{r.levelsAdded}</td></tr>
                <tr><td>Scoring weights changed</td><td className="num">{r.settingsChanged ? 'Yes' : 'No'}</td></tr>
              </tbody>
            </table>
            {r.notes.map((n, i) => (
              <Alert key={i} kind="warn">
                {n}
              </Alert>
            ))}
          </>
        )}
        <div className="modal-foot">
          <button className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn" disabled={action.busy || !r || changes === 0} onClick={apply}>
            Apply to this cycle
          </button>
        </div>
      </div>
    </Modal>
  );
}

export default function CycleDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const cycles = useApi('/admin/cycles');
  const cycle = cycles.data?.find((c) => String(c.id) === id);
  const started = cycle && cycle.status !== 'DRAFT';
  const participants = useApi(started ? `/admin/cycles/${id}/participants` : null, [started]);
  const reviews = useApi(started ? `/admin/cycles/${id}/reviews` : null, [started]);
  const users = useApi('/admin/users');
  const teamsApi = useApi('/admin/teams');
  const levelsApi = useApi('/admin/levels');
  const [addTeam, setAddTeam] = useState('');
  const [addLevel, setAddLevel] = useState('');
  const [tab, setTab] = useState('people');
  const [syncOpen, setSyncOpen] = useState(false);
  const [addUser, setAddUser] = useState('');
  const [name, setName] = useState(null);
  const action = useAction();

  const reloadAll = () => {
    cycles.reload();
    participants.reload();
    reviews.reload();
  };
  const notIn = useMemo(() => {
    const ids = new Set((participants.data || []).map((p) => p.userId));
    // anyone active who is not in the cycle yet, whatever their role
    return (users.data || []).filter((u) => u.active && !ids.has(u.id));
  }, [participants.data, users.data]);

  if (cycles.error) return <Alert>{cycles.error}</Alert>;
  if (!cycle) return <Loading />;

  const transition = async (verb, confirmText) => {
    if (confirmText && !window.confirm(confirmText)) return;
    if (await action.run(() => api.post(`/admin/cycles/${id}/${verb}`), `Cycle ${verb === 'open' ? 'opened' : verb === 'close' ? 'closed' : 'reopened'}.`)) reloadAll();
  };
  const remove = async () => {
    if (!window.confirm(`Delete "${cycle.name}" and ALL its reviews, scores, achievements and goals? This cannot be undone.`)) return;
    if ((await action.run(() => api.del(`/admin/cycles/${id}`))) !== undefined) navigate('/admin/cycles');
  };
  const rename = async () => {
    if (await action.run(() => api.put(`/admin/cycles/${id}`, { name }))) {
      setName(null);
      cycles.reload();
    }
  };
  const pickUser = (uid) => {
    setAddUser(uid);
    const u = (users.data || []).find((x) => String(x.id) === uid);
    setAddTeam(u?.teamId ? String(u.teamId) : '');
    setAddLevel(u?.levelId ? String(u.levelId) : '');
  };
  const add = async () => {
    const u = (users.data || []).find((x) => String(x.id) === addUser);
    const body = { userId: Number(addUser), teamId: addTeam ? Number(addTeam) : null, levelId: addLevel ? Number(addLevel) : null };
    if (await action.run(() => api.post(`/admin/cycles/${id}/participants`, body), `${u?.fullName || 'Person'} added to this cycle.`)) {
      setAddUser('');
      setAddTeam('');
      setAddLevel('');
      reloadAll();
      users.reload();
    }
  };
  const removeParticipant = async (p) => {
    if (!window.confirm(`Remove ${p.fullName} from this cycle? Their reviews, achievements and goals in this cycle are deleted.`)) return;
    if ((await action.run(() => api.del(`/admin/cycles/${id}/participants/${p.appraisalId}`), `${p.fullName} removed from this cycle.`)) !== undefined) reloadAll();
  };
  const reopenReview = async (r) => {
    if ((await action.run(() => api.post(`/admin/reviews/${r.id}/reopen`), `Review by ${r.reviewerName} reopened as a draft.`)) !== undefined) reviews.reload();
  };
  const deleteReview = async (r) => {
    if (!window.confirm(`Delete the ${ROLE_LABEL[r.role]} review by ${r.reviewerName}?`)) return;
    if ((await action.run(() => api.del(`/admin/reviews/${r.id}`))) !== undefined) reloadAll();
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <div className="muted small">
            <Link to="/admin/cycles">← Cycles</Link>
          </div>
          {name === null ? (
            <h1>
              {cycle.name}{' '}
              <button className="link small" onClick={() => setName(cycle.name)}>
                rename
              </button>
            </h1>
          ) : (
            <div className="row">
              <input value={name} onChange={(e) => setName(e.target.value)} aria-label="Cycle name" />
              <button className="btn small" onClick={rename}>
                Save
              </button>
              <button className="btn small ghost" onClick={() => setName(null)}>
                Cancel
              </button>
            </div>
          )}
          <div className="muted">
            Status <strong>{cycle.status}</strong> · opened {date(cycle.openedAt)} · closed {date(cycle.closedAt)}
            {cycle.weights && ` · Common ${pct(cycle.weights.commonWeight)} / Role ${pct(cycle.weights.roleWeight)} · Self ${pct(cycle.weights.selfWeight)}, Peer ${pct(cycle.weights.peerWeight)}, TL ${pct(cycle.weights.tlWeight)}, Manager ${pct(cycle.weights.managerWeight)}`}
          </div>
        </div>
        <div className="row">
          {cycle.status === 'DRAFT' && (
            <button className="btn" disabled={action.busy} onClick={() => transition('open', 'Open this cycle? The current question bank and weights are copied into it, and every active, appraised user with a team gets an appraisal.')}>
              Open cycle
            </button>
          )}
          {cycle.status === 'OPEN' && (
            <>
              <button className="btn secondary" onClick={() => setSyncOpen(true)}>
                Sync questions
              </button>
              <button className="btn" disabled={action.busy} onClick={() => transition('close', 'Close this cycle? Results become final and nothing can be edited. Draft (unsubmitted) reviews do not count.')}>
                Close cycle
              </button>
            </>
          )}
          {cycle.status === 'CLOSED' && (
            <button className="btn secondary" disabled={action.busy} onClick={() => transition('reopen', 'Reopen this closed cycle for edits?')}>
              Reopen
            </button>
          )}
          <button className="btn danger secondary" onClick={remove}>
            Delete
          </button>
        </div>
      </div>
      <Alert onClose={() => action.setError(null)}>{action.error}</Alert>
      <Alert kind="ok" onClose={() => action.setNotice(null)}>
        {action.notice}
      </Alert>

      {!started ? (
        <div className="card card-body muted">
          This cycle is a draft. Finish the question bank, users and levels, then open it. <Link to="/admin/questions">Go to questions</Link>
        </div>
      ) : (
        <>
          <div className="tabs">
            <button className={tab === 'people' ? 'active' : ''} onClick={() => setTab('people')}>
              People ({participants.data?.length ?? '…'})
            </button>
            <button className={tab === 'reviews' ? 'active' : ''} onClick={() => setTab('reviews')}>
              Reviews ({reviews.data?.length ?? '…'})
            </button>
            <Link className="btn small ghost" style={{ marginLeft: 'auto', alignSelf: 'center' }} to={`/summary?cycle=${id}`}>
              Open summary
            </Link>
          </div>

          {tab === 'people' && (
            <div className="card">
              {cycle.status === 'OPEN' && (
                <div className="card-body" style={{ borderBottom: '1px solid var(--border)' }}>
                  <div className="row" style={{ alignItems: 'flex-end' }}>
                    <label className="field grow" style={{ minWidth: 220 }}>
                      Add a person
                      <select value={addUser} onChange={(e) => pickUser(e.target.value)}>
                        <option value="">{notIn.length ? 'Choose someone not in this cycle…' : 'Everyone active is already in this cycle'}</option>
                        {notIn.map((u) => (
                          <option key={u.id} value={u.id}>
                            {u.fullName} · {u.roles.map((r) => r[0] + r.slice(1).toLowerCase()).join(', ')}
                            {u.teamName ? ` · ${u.teamName}` : ''}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="field" style={{ minWidth: 180 }}>
                      Team
                      <select value={addTeam} onChange={(e) => setAddTeam(e.target.value)} disabled={!addUser}>
                        <option value="">– choose –</option>
                        {(teamsApi.data || []).filter((t) => t.active).map((t) => (
                          <option key={t.id} value={t.id}>
                            {t.name}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="field" style={{ minWidth: 150 }}>
                      Level
                      <select value={addLevel} onChange={(e) => setAddLevel(e.target.value)} disabled={!addUser}>
                        <option value="">– choose –</option>
                        {(levelsApi.data || []).filter((l) => l.active).map((l) => (
                          <option key={l.id} value={l.id}>
                            {l.name}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button className="btn" disabled={!addUser || !addTeam || !addLevel || action.busy} onClick={add}>
                      Add to cycle
                    </button>
                  </div>
                  <div className="muted small" style={{ marginTop: 8 }}>
                    Anyone active can be added, including Managers and TLs. The team and level are saved on their profile. People you remove stay out of this cycle until you add them again.
                  </div>
                </div>
              )}
              {!participants.data ? (
                <Loading />
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Employee</th>
                        <th>Team</th>
                        <th>Level</th>
                        {ROLE_ORDER.map((r) => (
                          <th key={r} className="num">
                            {ROLE_LABEL[r]}
                          </th>
                        ))}
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {participants.data.map((p) => (
                        <tr key={p.appraisalId}>
                          <td>
                            <Link to={`/appraisals/${p.appraisalId}`}>{p.fullName}</Link>
                          </td>
                          <td>{p.teamName}</td>
                          <td>{p.levelName || <span className="pill warn">Not set</span>}</td>
                          {ROLE_ORDER.map((r) => (
                            <td key={r} className="num small" title="submitted (+ drafts)">
                              {p.submitted[r] || 0}
                              {p.drafts[r] ? <span className="muted"> +{p.drafts[r]}</span> : ''}
                            </td>
                          ))}
                          <td className="right">
                            {cycle.status === 'OPEN' && (
                              <button className="link danger" onClick={() => removeParticipant(p)}>
                                Remove
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {tab === 'reviews' && (
            <div className="card">
              {!reviews.data ? (
                <Loading />
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Employee</th>
                        <th>Team</th>
                        <th>Reviewer</th>
                        <th>As</th>
                        <th>Status</th>
                        <th>Updated</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {reviews.data.length === 0 && (
                        <tr>
                          <td colSpan={7} className="muted">
                            No reviews yet.
                          </td>
                        </tr>
                      )}
                      {reviews.data.map((r) => (
                        <tr key={r.id}>
                          <td>{r.employeeName}</td>
                          <td>{r.teamName}</td>
                          <td>{r.reviewerName}</td>
                          <td>{ROLE_LABEL[r.role]}</td>
                          <td>{r.status === 'SUBMITTED' ? <span className="pill ok">Submitted</span> : <span className="pill">Draft</span>}</td>
                          <td>{date(r.updatedAt)}</td>
                          <td className="right nowrap">
                            {cycle.status === 'OPEN' && r.status === 'SUBMITTED' && (
                              <>
                                <button className="link" onClick={() => reopenReview(r)}>
                                  Reopen
                                </button>{' '}
                                ·{' '}
                              </>
                            )}
                            {cycle.status === 'OPEN' && (
                              <button className="link danger" onClick={() => deleteReview(r)}>
                                Delete
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </>
      )}
      {syncOpen && (
        <SyncModal
          cycleId={id}
          onClose={() => setSyncOpen(false)}
          onApplied={(r) => {
            setSyncOpen(false);
            action.setNotice(`Synced: ${r.questionsAdded} added, ${r.questionsUpdated} changed, ${r.questionsRemoved} removed.`);
            reloadAll();
          }}
        />
      )}
    </div>
  );
}
