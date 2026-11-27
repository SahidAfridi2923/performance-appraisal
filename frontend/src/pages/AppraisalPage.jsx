import { Fragment, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useApi } from '../hooks';
import { Alert, Loading, Rating, ScoreStatus, useAction } from '../components/ui';
import { date, fmt, ROLE_LABEL, ROLE_ORDER } from '../format';
import Avatar from '../components/Avatar';
import Icon from '../components/Icon';

const scoreColor = (v) => (v === null || v === undefined ? 'var(--surface-3)' : v >= 9 ? 'var(--s5)' : v >= 7 ? 'var(--s4)' : v >= 5 ? 'var(--s3)' : v >= 3 ? 'var(--s2)' : 'var(--s1)');

function ScoreRing({ value }) {
  const r = 62;
  const c = 2 * Math.PI * r;
  const frac = value ? Math.max(0, Math.min(1, value / 10)) : 0;
  return (
    <div className="ring" role="img" aria-label={value ? `Final score ${fmt(value)} out of 10` : 'No final score yet'}>
      <svg viewBox="0 0 148 148">
        <circle cx="74" cy="74" r={r} fill="none" stroke="var(--surface-3)" strokeWidth="12" />
        <circle cx="74" cy="74" r={r} fill="none" stroke={scoreColor(value)} strokeWidth="12" strokeLinecap="round" strokeDasharray={`${c * frac} ${c}`} />
      </svg>
      <div className="center">
        <div>
          <div className="big">{value ? fmt(value) : '–'}</div>
          <div className="of">of 10</div>
        </div>
      </div>
    </div>
  );
}

function BlockRow({ label, value }) {
  return (
    <div className="block-row">
      <span className="muted" style={{ fontWeight: 600 }}>
        {label}
      </span>
      <div className="bar">
        <div style={{ width: `${value ? value * 10 : 0}%`, background: scoreColor(value) }} />
      </div>
      <span className="v">{fmt(value)}</span>
    </div>
  );
}

function ResultSection({ result }) {
  const [open, setOpen] = useState({});
  return (
    <>
      <div className="card score-hero">
        <ScoreRing value={result.finalScore} />
        <div className="blocks">
          <div className="row" style={{ gap: 10 }}>
            <Rating rating={result.rating} />
            <ScoreStatus status={result.status} />
            <span className="muted small">
              TL / Manager answered {result.leadAnswered} of {result.needed}
            </span>
          </div>
          <BlockRow label="Common competencies" value={result.commonScore} />
          <BlockRow label="Role-specific" value={result.roleScore} />
        </div>
      </div>
      {result.selfGapCount > 0 && (
        <Alert kind="warn">
          On {result.selfGapCount} question(s) the Self number differs from the TL / Manager number by 3 or more. Discuss these in the one-to-one rather than changing scores.
        </Alert>
      )}
      <div className="card" style={{ marginTop: 16 }}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Category / question</th>
                <th className="num">Wt / pts</th>
                {ROLE_ORDER.map((r) => (
                  <th key={r} className="num">
                    {ROLE_LABEL[r]}
                  </th>
                ))}
                <th className="num">Score</th>
              </tr>
            </thead>
            <tbody>
              {result.categories.map((c) => (
                <Fragment key={c.id}>
                  <tr style={{ background: 'var(--surface-2)', cursor: 'pointer' }} onClick={() => setOpen((o) => ({ ...o, [c.id]: !o[c.id] }))}>
                    <td>
                      <strong>
                        <span aria-hidden="true" style={{ display: 'inline-block', width: 14, transition: 'transform .18s', transform: open[c.id] ? 'rotate(90deg)' : 'none' }}>
                          ›
                        </span>{' '}
                        {c.name}
                      </strong>{' '}
                      <span className="muted small">{c.block === 'COMMON' ? 'common' : 'role-specific'}</span>
                    </td>
                    <td className="num">{c.points > 0 ? fmt(c.points) : <span className="muted">n/a</span>}</td>
                    <td colSpan={4} />
                    <td className="num">
                      <strong style={{ color: c.score ? scoreColor(c.score) : undefined }}>{fmt(c.score)}</strong>
                    </td>
                  </tr>
                  {open[c.id] &&
                    c.questions.map((q) => (
                      <tr key={q.id} className={q.applicable ? '' : 'archived'}>
                        <td>
                          <span className="muted">{q.number}.</span> {q.text}
                          {!q.applicable && <span className="muted small"> (does not apply to this level)</span>}
                          {q.leadComments.map((cm, i) => (
                            <div key={i} className="muted small row" style={{ gap: 6, marginTop: 4 }}>
                              <Icon name="message" size={13} /> {cm}
                            </div>
                          ))}
                        </td>
                        <td className="num">{fmt(q.weight)}</td>
                        {ROLE_ORDER.map((r) => (
                          <td key={r} className="num">
                            {q.roleAverages[r] !== undefined ? fmt(q.roleAverages[r]) : <span className="muted">–</span>}
                            {q.roleCounts[r] > 1 && <span className="muted small"> ×{q.roleCounts[r]}</span>}
                          </td>
                        ))}
                        <td className="num">{fmt(q.score)}</td>
                      </tr>
                    ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

function Achievements({ appraisal, reload, situations, impacts }) {
  const { canLead, editable, isSelf } = appraisal;
  const canEdit = editable && (canLead || isSelf);
  const blank = { text: '', situation: '', businessImpact: '', verified: false };
  const [draft, setDraft] = useState(blank);
  const [editing, setEditing] = useState(null);
  const action = useAction();

  const save = async (item) => {
    const body = { ...item, situation: item.situation || null, businessImpact: item.businessImpact || null };
    const r = await action.run(() => (item.id ? api.put(`/achievements/${item.id}`, body) : api.post(`/appraisals/${appraisal.id}/achievements`, body)));
    if (r) {
      setDraft(blank);
      setEditing(null);
      reload();
    }
  };
  const remove = async (item) => {
    if (!window.confirm('Remove this achievement?')) return;
    if ((await action.run(() => api.del(`/achievements/${item.id}`))) !== undefined) reload();
  };
  const verify = async (item, verified) => {
    if (await action.run(() => api.put(`/achievements/${item.id}`, { ...item, verified }))) reload();
  };

  const row = (item, setItem) => (
    <>
      <td>
        <textarea value={item.text} onChange={(e) => setItem({ ...item, text: e.target.value })} placeholder="Achievement / contribution" style={{ width: '100%' }} />
      </td>
      <td>
        <select value={item.situation || ''} onChange={(e) => setItem({ ...item, situation: e.target.value })}>
          <option value="">–</option>
          {situations.map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
      </td>
      <td>
        <select value={item.businessImpact || ''} onChange={(e) => setItem({ ...item, businessImpact: e.target.value })}>
          <option value="">–</option>
          {impacts.map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
      </td>
    </>
  );

  return (
    <div className="card">
      <div className="card-head">
        <h3>Employee-specific achievements / tasks completed</h3>
        <span className="muted small">Counts only when the lead verifies it.</span>
      </div>
      <Alert onClose={() => action.setError(null)}>{action.error}</Alert>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Achievement / contribution</th>
              <th>Situation</th>
              <th>Business impact</th>
              <th>Verified by lead</th>
              {canEdit && <th />}
            </tr>
          </thead>
          <tbody>
            {appraisal.achievements.map((a) =>
              editing && editing.id === a.id ? (
                <tr key={a.id}>
                  {row(editing, setEditing)}
                  <td />
                  <td className="nowrap">
                    <button className="btn small" disabled={action.busy || !editing.text.trim()} onClick={() => save(editing)}>
                      Save
                    </button>{' '}
                    <button className="btn small ghost" onClick={() => setEditing(null)}>
                      Cancel
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={a.id}>
                  <td>{a.text}</td>
                  <td>{a.situation || '–'}</td>
                  <td>{a.businessImpact || '–'}</td>
                  <td>
                    {canLead && editable ? (
                      <label className="row small">
                        <input type="checkbox" checked={a.verified} onChange={(e) => verify(a, e.target.checked)} /> Yes
                      </label>
                    ) : a.verified ? (
                      <span className="pill ok">Yes</span>
                    ) : (
                      <span className="pill">No</span>
                    )}
                  </td>
                  {canEdit && (
                    <td className="nowrap right">
                      <button className="link" onClick={() => setEditing({ ...a })}>
                        Edit
                      </button>{' '}
                      ·{' '}
                      <button className="link danger" onClick={() => remove(a)}>
                        Remove
                      </button>
                    </td>
                  )}
                </tr>
              )
            )}
            {canEdit && (
              <tr>
                {row(draft, setDraft)}
                <td className="muted small">{canLead ? '' : 'lead verifies'}</td>
                <td>
                  <button className="btn small" disabled={action.busy || !draft.text.trim()} onClick={() => save(draft)}>
                    Add
                  </button>
                </td>
              </tr>
            )}
            {!canEdit && appraisal.achievements.length === 0 && (
              <tr>
                <td colSpan={5} className="muted">
                  None recorded.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Goals({ appraisal, reload }) {
  const canEdit = appraisal.editable && (appraisal.canLead || appraisal.isSelf);
  const [draft, setDraft] = useState({ text: '', successCriteria: '' });
  const [editing, setEditing] = useState(null);
  const action = useAction();
  const save = async (g) => {
    const r = await action.run(() => (g.id ? api.put(`/goals/${g.id}`, g) : api.post(`/appraisals/${appraisal.id}/goals`, g)));
    if (r) {
      setDraft({ text: '', successCriteria: '' });
      setEditing(null);
      reload();
    }
  };
  const remove = async (g) => {
    if (!window.confirm('Remove this goal?')) return;
    if ((await action.run(() => api.del(`/goals/${g.id}`))) !== undefined) reload();
  };
  const inputs = (g, set) => (
    <>
      <td>
        <input style={{ width: '100%' }} value={g.text} onChange={(e) => set({ ...g, text: e.target.value })} placeholder="Goal / objective" />
      </td>
      <td>
        <input style={{ width: '100%' }} value={g.successCriteria || ''} onChange={(e) => set({ ...g, successCriteria: e.target.value })} placeholder="Success criteria" />
      </td>
    </>
  );
  return (
    <div className="card">
      <div className="card-head">
        <h3>Goals for the next appraisal period</h3>
      </div>
      <Alert onClose={() => action.setError(null)}>{action.error}</Alert>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Goal / objective</th>
              <th>Success criteria</th>
              {canEdit && <th />}
            </tr>
          </thead>
          <tbody>
            {appraisal.goals.map((g) =>
              editing && editing.id === g.id ? (
                <tr key={g.id}>
                  {inputs(editing, setEditing)}
                  <td className="nowrap">
                    <button className="btn small" disabled={!editing.text.trim()} onClick={() => save(editing)}>
                      Save
                    </button>{' '}
                    <button className="btn small ghost" onClick={() => setEditing(null)}>
                      Cancel
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={g.id}>
                  <td>{g.text}</td>
                  <td>{g.successCriteria || '–'}</td>
                  {canEdit && (
                    <td className="nowrap right">
                      <button className="link" onClick={() => setEditing({ ...g })}>
                        Edit
                      </button>{' '}
                      ·{' '}
                      <button className="link danger" onClick={() => remove(g)}>
                        Remove
                      </button>
                    </td>
                  )}
                </tr>
              )
            )}
            {canEdit && (
              <tr>
                {inputs(draft, setDraft)}
                <td>
                  <button className="btn small" disabled={action.busy || !draft.text.trim()} onClick={() => save(draft)}>
                    Add
                  </button>
                </td>
              </tr>
            )}
            {!canEdit && appraisal.goals.length === 0 && (
              <tr>
                <td colSpan={3} className="muted">
                  None recorded.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function AppraisalPage() {
  const { id } = useParams();
  const { data: a, error, reload } = useApi(`/appraisals/${id}`, [id]);
  const { data: config } = useApi('/config');
  const [comments, setComments] = useState('');
  const action = useAction();

  useEffect(() => {
    if (a) setComments(a.leadComments || '');
  }, [a]);

  if (error) return <Alert>{error}</Alert>;
  if (!a) return <Loading />;

  const setLevel = async (lvl) => {
    if ((await action.run(() => api.put(`/appraisals/${id}/level`, { cycleLevelId: lvl ? Number(lvl) : null }), 'Level updated.')) !== undefined) reload();
  };
  const saveComments = async () => {
    await action.run(() => api.put(`/appraisals/${id}/lead-comments`, { text: comments }), 'Comments saved.');
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <Link className="crumb" to="/">
            <Icon name="back" size={15} /> My work
          </Link>
          <div className="person">
            <Avatar name={a.employee.name} large />
            <div>
              <h1>{a.employee.name}</h1>
              <div className="sub" style={{ marginTop: 2 }}>
                {a.teamName} · {a.cycle.name} {a.cycle.status === 'CLOSED' && <span className="pill blue">Closed</span>}
              </div>
            </div>
          </div>
        </div>
        <label className="field">
          Level
          {a.canLead && a.editable ? (
            <select value={a.level?.id || ''} onChange={(e) => setLevel(e.target.value)} disabled={action.busy}>
              <option value="">– select –</option>
              {a.levels.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          ) : (
            <span style={{ color: 'var(--text)', fontWeight: 500, fontSize: 15 }}>{a.level?.name || 'Not set'}</span>
          )}
        </label>
      </div>
      <Alert onClose={() => action.setError(null)}>{action.error}</Alert>
      <Alert kind="ok">{action.notice}</Alert>

      {a.canViewResults && a.result ? (
        <ResultSection result={a.result} />
      ) : (
        <Alert kind="info">Scores are shown to you once the cycle is closed.</Alert>
      )}

      {a.canLead && (
        <>
          <h2>Reviews received</h2>
          <div className="card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Reviewer</th>
                    <th>As</th>
                    <th>Status</th>
                    <th>Submitted</th>
                  </tr>
                </thead>
                <tbody>
                  {a.reviews.length === 0 && (
                    <tr>
                      <td colSpan={4} className="muted">
                        No reviews yet.
                      </td>
                    </tr>
                  )}
                  {a.reviews.map((r) => (
                    <tr key={r.id}>
                      <td>{r.reviewerName}</td>
                      <td>{ROLE_LABEL[r.role]}</td>
                      <td>{r.status === 'SUBMITTED' ? <span className="pill ok">Submitted</span> : <span className="pill">Draft (not counted)</span>}</td>
                      <td>{date(r.submittedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {(a.canLead || a.canViewResults) && (
        <>
          <h2>Lead comments</h2>
          <div className="card card-body stack">
            <div className="muted small">Give a reason for any 9–10 or 1–2, plus key strengths and improvement areas.</div>
            {a.canLead && a.editable ? (
              <>
                <textarea rows={4} value={comments} onChange={(e) => setComments(e.target.value)} />
                <div>
                  <button className="btn small" disabled={action.busy} onClick={saveComments}>
                    Save comments
                  </button>
                </div>
              </>
            ) : (
              <div style={{ whiteSpace: 'pre-wrap' }}>{a.leadComments || <span className="muted">No comments.</span>}</div>
            )}
          </div>
        </>
      )}

      <h2>Achievements &amp; goals</h2>
      <Achievements appraisal={a} reload={reload} situations={config?.pickLists?.SITUATION || []} impacts={config?.pickLists?.BUSINESS_IMPACT || []} />
      <Goals appraisal={a} reload={reload} />
    </div>
  );
}
