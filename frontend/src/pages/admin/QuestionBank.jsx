import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import { useApi } from '../../hooks';
import { Alert, Loading, Modal, useAction } from '../../components/ui';
import { fmt, pct } from '../../format';

const REVIEWED_BY = { ALL: 'All reviewers', TL_MANAGER_ONLY: 'TL / Manager only' };

function QuestionModal({ question, categories, levels, onClose, onSaved }) {
  const [q, setQ] = useState({
    text: '',
    guidance: '',
    weight: 1,
    reviewedBy: 'ALL',
    active: true,
    sortOrder: 0,
    excludedLevelIds: [],
    ...question,
  });
  const action = useAction();
  const save = async (e) => {
    e.preventDefault();
    const body = { ...q, weight: Number(q.weight), categoryId: Number(q.categoryId) };
    const r = await action.run(() => (q.id ? api.put(`/admin/questions/${q.id}`, body) : api.post('/admin/questions', body)));
    if (r) onSaved(r);
  };
  const toggleLevel = (id) =>
    setQ((x) => ({
      ...x,
      excludedLevelIds: x.excludedLevelIds.includes(id) ? x.excludedLevelIds.filter((l) => l !== id) : [...x.excludedLevelIds, id],
    }));

  return (
    <Modal title={q.id ? 'Edit question' : 'Add question'} onClose={onClose} wide>
      <form className="stack" onSubmit={save}>
        <Alert>{action.error}</Alert>
        <label className="field">
          Question (one idea, about "the employee")
          <textarea value={q.text} onChange={(e) => setQ({ ...q, text: e.target.value })} required maxLength={1000} rows={3} autoFocus />
        </label>
        <div className="form-grid">
          <label className="field">
            Category
            <select value={q.categoryId} onChange={(e) => setQ({ ...q, categoryId: e.target.value })}>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.block === 'COMMON' ? 'Common · ' : 'Role · '}
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            Weight inside its category
            <input type="number" min="0" step="0.01" value={q.weight} onChange={(e) => setQ({ ...q, weight: e.target.value })} required />
          </label>
          <label className="field">
            Reviewed by
            <select value={q.reviewedBy} onChange={(e) => setQ({ ...q, reviewedBy: e.target.value })}>
              {Object.entries(REVIEWED_BY).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="field">
          Applies to levels
          <div className="checks">
            {levels.map((l) => (
              <label key={l.id}>
                <input type="checkbox" checked={!q.excludedLevelIds.includes(l.id)} onChange={() => toggleLevel(l.id)} /> {l.name}
              </label>
            ))}
          </div>
          <span className="muted small" style={{ fontWeight: 400 }}>
            A level also skips the question when its category has 0 points for that level.
          </span>
        </div>
        <label className="field">
          Scoring guide shown to reviewers (optional)
          <textarea value={q.guidance || ''} onChange={(e) => setQ({ ...q, guidance: e.target.value })} rows={4} />
        </label>
        {q.id && (
          <div className="checks">
            <label>
              <input type="checkbox" checked={q.active} onChange={(e) => setQ({ ...q, active: e.target.checked })} /> Active (unticked = archived)
            </label>
          </div>
        )}
        <div className="modal-foot">
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn" disabled={action.busy}>
            Save question
          </button>
        </div>
      </form>
    </Modal>
  );
}

function CategoryModal({ category, levels, onClose, onSaved }) {
  const [c, setC] = useState(() => ({
    name: '',
    active: true,
    sortOrder: 0,
    ...category,
    levelPoints: { ...Object.fromEntries(levels.map((l) => [l.id, category.block === 'ROLE' ? 1 : 0])), ...(category.levelPoints || {}) },
  }));
  const action = useAction();
  const save = async (e) => {
    e.preventDefault();
    const body = { ...c, levelPoints: Object.fromEntries(Object.entries(c.levelPoints).map(([k, v]) => [k, Number(v) || 0])), questions: null };
    const r = await action.run(() => (c.id ? api.put(`/admin/categories/${c.id}`, body) : api.post('/admin/categories', body)));
    if (r) onSaved(r);
  };
  return (
    <Modal title={c.id ? 'Edit category' : `Add ${c.block === 'COMMON' ? 'common' : 'role-specific'} category`} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <Alert>{action.error}</Alert>
        <label className="field">
          Name
          <input value={c.name} onChange={(e) => setC({ ...c, name: e.target.value })} required maxLength={200} autoFocus />
        </label>
        <div className="field">
          Points by level (relative weight inside the {c.block === 'COMMON' ? 'Common' : 'Role-specific'} block; 0 = does not apply)
          <div className="points">
            {levels.map((l) => (
              <label key={l.id}>
                {l.name}
                <input type="number" min="0" step="0.5" value={c.levelPoints[l.id] ?? 0} onChange={(e) => setC({ ...c, levelPoints: { ...c.levelPoints, [l.id]: e.target.value } })} />
              </label>
            ))}
          </div>
        </div>
        {c.id && (
          <div className="checks">
            <label>
              <input type="checkbox" checked={c.active} onChange={(e) => setC({ ...c, active: e.target.checked })} /> Active
            </label>
          </div>
        )}
        <div className="modal-foot">
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn" disabled={action.busy}>
            Save category
          </button>
        </div>
      </form>
    </Modal>
  );
}

function CategoryCard({ cat, index, siblings, levels, share, showArchived, onEdit, onAddQuestion, onEditQuestion, reload, action }) {
  const questions = cat.questions.filter((q) => showArchived || q.active);
  const activeQs = cat.questions.filter((q) => q.active);
  const totalWeight = activeQs.reduce((s, q) => s + q.weight, 0);

  const move = async (dir) => {
    const ids = siblings.map((s) => s.id);
    const j = index + dir;
    [ids[index], ids[j]] = [ids[j], ids[index]];
    if ((await action.run(() => api.put('/admin/categories/order', { ids }))) !== undefined) reload();
  };
  const moveQ = async (qi, dir) => {
    const ids = activeQs.map((q) => q.id);
    const j = qi + dir;
    [ids[qi], ids[j]] = [ids[j], ids[qi]];
    const archived = cat.questions.filter((q) => !q.active).map((q) => q.id);
    if ((await action.run(() => api.put(`/admin/categories/${cat.id}/question-order`, { ids: [...ids, ...archived] }))) !== undefined) reload();
  };
  const archive = async (q) => {
    if (!window.confirm(`Archive this question?\n\n"${q.text}"\n\nIt will not appear in new cycles. Past results keep it.`)) return;
    if ((await action.run(() => api.del(`/admin/questions/${q.id}`), 'Question archived.')) !== undefined) reload();
  };
  const restore = async (q) => {
    if (await action.run(() => api.put(`/admin/questions/${q.id}`, { ...q, active: true }), 'Question restored.')) reload();
  };
  const purge = async (q) => {
    if (!window.confirm(`Delete permanently?\n\n"${q.text}"\n\nOnly possible when no cycle has used it.`)) return;
    if ((await action.run(() => api.del(`/admin/questions/${q.id}?permanent=true`), 'Question deleted.')) !== undefined) reload();
  };
  const removeCategory = async () => {
    const msg = cat.questions.length ? 'This category has questions, so it will be archived (hidden from new cycles).' : 'Delete this empty category?';
    if (!window.confirm(msg)) return;
    if ((await action.run(() => api.del(`/admin/categories/${cat.id}`))) !== undefined) reload();
  };
  const restoreCategory = async () => {
    if (await action.run(() => api.put(`/admin/categories/${cat.id}`, { ...cat, active: true, questions: null }))) reload();
  };

  return (
    <div className={`card cat-card ${cat.active ? '' : 'archived'}`}>
      <div className="card-head">
        <div>
          <h3>
            {cat.name} {!cat.active && <span className="pill">archived</span>}
          </h3>
          <div className="badge-row small muted">
            {levels.map((l) => (
              <span key={l.id} title={`Share of the final score for ${l.name}`}>
                {l.name}: {fmt(cat.levelPoints[l.id] ?? 0)} pts{share && share[l.id] !== undefined && ` (${pct(share[l.id])} of final)`}
              </span>
            ))}
            <span>· question weights total {fmt(totalWeight)}</span>
          </div>
        </div>
        <div className="row">
          <button className="icon-btn" disabled={index === 0} onClick={() => move(-1)} aria-label="Move category up">
            ↑
          </button>
          <button className="icon-btn" disabled={index === siblings.length - 1} onClick={() => move(1)} aria-label="Move category down">
            ↓
          </button>
          <button className="btn small secondary" onClick={onEdit}>
            Edit
          </button>
          {cat.active ? (
            <button className="btn small danger secondary" onClick={removeCategory}>
              Remove
            </button>
          ) : (
            <button className="btn small secondary" onClick={restoreCategory}>
              Restore
            </button>
          )}
          <button className="btn small" onClick={onAddQuestion}>
            + Question
          </button>
        </div>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th style={{ width: 70 }}>Order</th>
              <th>Question</th>
              <th className="num">Weight</th>
              <th>Reviewed by</th>
              <th>Levels</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {questions.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  No questions yet.
                </td>
              </tr>
            )}
            {questions.map((q) => {
              const ai = activeQs.indexOf(q);
              return (
                <tr key={q.id} className={q.active ? '' : 'archived'}>
                  <td className="nowrap">
                    {q.active && (
                      <>
                        <button className="icon-btn" disabled={ai === 0} onClick={() => moveQ(ai, -1)} aria-label="Move up">
                          ↑
                        </button>{' '}
                        <button className="icon-btn" disabled={ai === activeQs.length - 1} onClick={() => moveQ(ai, 1)} aria-label="Move down">
                          ↓
                        </button>
                      </>
                    )}
                  </td>
                  <td className="qtext">
                    {q.text}
                    {q.guidance && (
                      <span className="pill small" style={{ marginLeft: 6 }} title={q.guidance}>
                        guide
                      </span>
                    )}
                    {!q.active && (
                      <span className="pill small" style={{ marginLeft: 6 }}>
                        archived
                      </span>
                    )}
                  </td>
                  <td className="num">{fmt(q.weight)}</td>
                  <td className="nowrap">{REVIEWED_BY[q.reviewedBy]}</td>
                  <td className="small">
                    {q.excludedLevelIds.length === 0
                      ? 'All'
                      : levels
                          .filter((l) => !q.excludedLevelIds.includes(l.id))
                          .map((l) => l.name)
                          .join(', ') || 'None'}
                  </td>
                  <td className="nowrap right">
                    <button className="link" onClick={() => onEditQuestion(q)}>
                      Edit
                    </button>{' '}
                    ·{' '}
                    {q.active ? (
                      <button className="link danger" onClick={() => archive(q)}>
                        Remove
                      </button>
                    ) : (
                      <>
                        <button className="link" onClick={() => restore(q)}>
                          Restore
                        </button>{' '}
                        ·{' '}
                        <button className="link danger" onClick={() => purge(q)}>
                          Delete
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function QuestionBank() {
  const { data: teams } = useApi('/admin/teams');
  const [teamId, setTeamId] = useState('');
  const { data: bank, error, reload } = useApi(teamId ? `/admin/question-bank?teamId=${teamId}` : null, [teamId]);
  const [showArchived, setShowArchived] = useState(false);
  const [modal, setModal] = useState(null);
  const action = useAction();

  useEffect(() => {
    if (teams && teams.length && !teamId) setTeamId(String(teams[0].id));
  }, [teams, teamId]);

  const levels = useMemo(() => (bank ? bank.levels.filter((l) => l.active) : []), [bank]);
  const allCats = bank ? [...bank.common, ...bank.role] : [];

  // share of the final score per category and level (Scoring_Rules rows 29-38)
  const shares = useMemo(() => {
    if (!bank) return {};
    const out = {};
    const blocks = [
      ['COMMON', bank.common, bank.settings.commonWeight],
      ['ROLE', bank.role, bank.settings.roleWeight],
    ];
    const total = bank.settings.commonWeight + bank.settings.roleWeight;
    blocks.forEach(([, cats, w]) => {
      const active = cats.filter((c) => c.active && c.questions.some((q) => q.active));
      levels.forEach((l) => {
        const sum = active.reduce((s, c) => s + (c.levelPoints[l.id] || 0), 0);
        active.forEach((c) => {
          out[c.id] = out[c.id] || {};
          out[c.id][l.id] = sum > 0 ? ((c.levelPoints[l.id] || 0) / sum) * (w / total) : 0;
        });
      });
    });
    return out;
  }, [bank, levels]);

  const afterSave = () => {
    setModal(null);
    reload();
  };
  const team = teams && teams.find((t) => String(t.id) === teamId);
  const visible = (cats) => cats.filter((c) => showArchived || c.active);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Question bank</h1>
          <div className="muted">
            Add, edit, reorder or remove questions and categories at any time. Changes apply to cycles opened from now on; to push them into an open cycle use{' '}
            <Link to="/admin/cycles">Cycles → Sync questions</Link>.
          </div>
        </div>
        <div className="row">
          <label className="field">
            Team
            <select value={teamId} onChange={(e) => setTeamId(e.target.value)}>
              {(teams || []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} {t.active ? '' : '(inactive)'}
                </option>
              ))}
            </select>
          </label>
          <label className="row small" style={{ marginTop: 16 }}>
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Show archived
          </label>
        </div>
      </div>
      <Alert>{error}</Alert>
      <Alert onClose={() => action.setError(null)}>{action.error}</Alert>
      <Alert kind="ok" onClose={() => action.setNotice(null)}>
        {action.notice}
      </Alert>
      {!bank ? (
        <Loading />
      ) : (
        <>
          <div className="row between">
            <h2>
              Common competencies <span className="muted small">· {pct(bank.settings.commonWeight)} of the final score · shared by every team</span>
            </h2>
            <button className="btn small secondary" onClick={() => setModal({ type: 'category', data: { block: 'COMMON' } })}>
              + Common category
            </button>
          </div>
          {visible(bank.common).map((c, i, arr) => (
            <CategoryCard
              key={c.id}
              cat={c}
              index={i}
              siblings={arr}
              levels={levels}
              share={shares[c.id]}
              showArchived={showArchived}
              action={action}
              reload={reload}
              onEdit={() => setModal({ type: 'category', data: c })}
              onAddQuestion={() => setModal({ type: 'question', data: { categoryId: c.id } })}
              onEditQuestion={(q) => setModal({ type: 'question', data: q })}
            />
          ))}

          <div className="row between">
            <h2>
              Role-specific · {team?.name} <span className="muted small">· {pct(bank.settings.roleWeight)} of the final score</span>
            </h2>
            <button className="btn small secondary" onClick={() => setModal({ type: 'category', data: { block: 'ROLE', teamId: Number(teamId) } })}>
              + Role category
            </button>
          </div>
          {visible(bank.role).length === 0 && <div className="card card-body muted">No role-specific categories for this team yet.</div>}
          {visible(bank.role).map((c, i, arr) => (
            <CategoryCard
              key={c.id}
              cat={c}
              index={i}
              siblings={arr}
              levels={levels}
              share={shares[c.id]}
              showArchived={showArchived}
              action={action}
              reload={reload}
              onEdit={() => setModal({ type: 'category', data: c })}
              onAddQuestion={() => setModal({ type: 'question', data: { categoryId: c.id } })}
              onEditQuestion={(q) => setModal({ type: 'question', data: q })}
            />
          ))}
        </>
      )}
      {modal?.type === 'question' && (
        <QuestionModal question={modal.data} categories={allCats.filter((c) => c.active || c.id === modal.data.categoryId)} levels={levels} onClose={() => setModal(null)} onSaved={afterSave} />
      )}
      {modal?.type === 'category' && <CategoryModal category={modal.data} levels={levels} onClose={() => setModal(null)} onSaved={afterSave} />}
    </div>
  );
}
