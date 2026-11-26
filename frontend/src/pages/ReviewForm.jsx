import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { useApi } from '../hooks';
import { Alert, Loading, useAction } from '../components/ui';
import { ROLE_LABEL } from '../format';

const SCALE = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const extreme = (v) => v !== null && v !== undefined && (v <= 2 || v >= 9);

export default function ReviewForm() {
  const { id, role } = useParams();
  const navigate = useNavigate();
  const { data: form, error, setData } = useApi(`/appraisals/${id}/reviews/${role}`, [id, role]);
  const { data: config } = useApi('/config');
  const [answers, setAnswers] = useState({});
  const [openGuide, setOpenGuide] = useState({});
  const [showMissing, setShowMissing] = useState(false);
  const [dirty, setDirty] = useState(false);
  const action = useAction();

  useEffect(() => {
    if (!form) return;
    const a = {};
    form.categories.forEach((c) =>
      c.questions.forEach((q) => {
        a[q.id] = { value: q.value, na: q.na, comment: q.comment || '' };
      })
    );
    setAnswers(a);
    setDirty(false);
  }, [form]);

  useEffect(() => {
    const warn = (e) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const questions = useMemo(() => (form ? form.categories.flatMap((c) => c.questions) : []), [form]);
  const answered = questions.filter((q) => answers[q.id] && (answers[q.id].na || answers[q.id].value)).length;
  const scaleLabel = useMemo(() => {
    const m = {};
    (config?.scaleLabels || []).forEach((s) => (m[s.value] = s.label));
    return m;
  }, [config]);

  if (error) return <Alert>{error}</Alert>;
  if (!form) return <Loading />;

  const editable = form.editable;
  const set = (qid, patch) => {
    setAnswers((a) => ({ ...a, [qid]: { ...a[qid], ...patch } }));
    setDirty(true);
  };
  const payload = () => ({
    answers: questions.map((q) => ({
      questionId: q.id,
      value: answers[q.id]?.na ? null : answers[q.id]?.value ?? null,
      na: !!answers[q.id]?.na,
      comment: answers[q.id]?.comment || null,
    })),
  });

  const save = async () => {
    const r = await action.run(() => api.put(`/appraisals/${id}/reviews/${role}`, payload()), 'Draft saved.');
    if (r) {
      setData(r);
      setDirty(false);
    }
  };
  const submit = async () => {
    setShowMissing(true);
    if (!window.confirm('Submit this review? You cannot change it afterwards (an admin can reopen it).')) return;
    const r = await action.run(() => api.post(`/appraisals/${id}/reviews/${role}/submit`, payload()));
    if (r) {
      setDirty(false);
      navigate('/', { replace: false });
    }
  };

  const isMissing = (q) => {
    const a = answers[q.id];
    if (!a || (!a.na && !a.value)) return true;
    return form.commentsRequiredForExtremes && !a.na && extreme(a.value) && !a.comment?.trim();
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <div className="muted small">
            <Link to="/">← My work</Link>
          </div>
          <h1>
            {ROLE_LABEL[role]} review · {form.employeeName}
          </h1>
          <div className="muted">
            {form.teamName} · {form.levelName || 'level not set'} ·{' '}
            {form.status === 'SUBMITTED' ? <span className="pill ok">Submitted</span> : form.status === 'DRAFT' ? <span className="pill">Draft</span> : <span className="pill">Not started</span>}
          </div>
        </div>
      </div>

      {!form.levelName && <Alert kind="warn">The employee's level is not set yet. The TL or Manager sets it on the appraisal page.</Alert>}
      {form.cycleStatus !== 'OPEN' && <Alert kind="info">This cycle is closed; the review is read-only.</Alert>}
      {form.status === 'SUBMITTED' && <Alert kind="info">You submitted this review. Ask an admin if it needs to be reopened.</Alert>}
      <Alert kind="info">
        {role === 'SELF' ? 'Answer about yourself. ' : "Every question is about 'the employee'. "}
        Pick a number from 1 (never / poor) to 10 (always / excellent) for the whole period, or N/A if you could not observe it.
        {form.commentsRequiredForExtremes && ' Any 9–10 or 1–2 needs a one-line comment with a real example.'}
      </Alert>

      <div className="card">
        {form.categories.map((c) => (
          <div key={c.id} className="review-cat">
            <div className="card-head">
              <h3>{c.name}</h3>
              <span className="pill blue">{c.block === 'COMMON' ? 'Common competencies' : 'Role-specific'}</span>
            </div>
            {c.questions.map((q) => {
              const a = answers[q.id] || {};
              const needsComment = form.commentsRequiredForExtremes && !a.na && extreme(a.value);
              return (
                <div key={q.id} className={`q ${showMissing && editable && isMissing(q) ? 'missing' : ''}`}>
                  <div className="q-text">
                    <span className="q-num">{q.number}.</span>
                    <span className="grow">
                      {q.text}
                      {q.reviewedBy === 'TL_MANAGER_ONLY' && <span className="pill small" style={{ marginLeft: 8 }}>TL / Manager only</span>}
                    </span>
                    {q.guidance && (
                      <button className="link small" onClick={() => setOpenGuide((g) => ({ ...g, [q.id]: !g[q.id] }))}>
                        {openGuide[q.id] ? 'Hide guide' : 'How to score'}
                      </button>
                    )}
                  </div>
                  {openGuide[q.id] && <div className="guidance">{q.guidance}</div>}
                  <div className="scale" role="radiogroup" aria-label={`Question ${q.number}`}>
                    {SCALE.map((v) => (
                      <button
                        key={v}
                        type="button"
                        className={!a.na && a.value === v ? 'sel' : ''}
                        disabled={!editable}
                        title={scaleLabel[v]}
                        aria-pressed={!a.na && a.value === v}
                        onClick={() => set(q.id, { value: v, na: false })}
                      >
                        {v}
                      </button>
                    ))}
                    <button type="button" className={`na ${a.na ? 'sel' : ''}`} disabled={!editable} aria-pressed={!!a.na} onClick={() => set(q.id, { na: !a.na, value: null })}>
                      N/A
                    </button>
                  </div>
                  {(needsComment || a.comment || (editable && a.showComment)) && (
                    <div className="q-extra">
                      <textarea
                        placeholder={needsComment ? 'Required: a real example for this score' : 'Comment (optional)'}
                        value={a.comment || ''}
                        disabled={!editable}
                        onChange={(e) => set(q.id, { comment: e.target.value })}
                      />
                    </div>
                  )}
                  {editable && !needsComment && !a.comment && !a.showComment && (
                    <div className="q-extra">
                      <button className="link small" onClick={() => set(q.id, { showComment: true })}>
                        + comment
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ))}
        {questions.length === 0 && <div className="card-body muted">No questions to answer for this level.</div>}
        <div className="sticky-bar">
          <div>
            <strong>
              {answered} / {questions.length}
            </strong>{' '}
            <span className="muted">answered</span>
            {dirty && <span className="muted small"> · unsaved changes</span>}
          </div>
          <div className="grow">
            <Alert onClose={() => action.setError(null)}>{action.error}</Alert>
            <Alert kind="ok">{action.notice}</Alert>
          </div>
          {editable && (
            <div className="row">
              <button className="btn secondary" disabled={action.busy} onClick={save}>
                Save draft
              </button>
              <button className="btn" disabled={action.busy} onClick={submit}>
                Submit review
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
