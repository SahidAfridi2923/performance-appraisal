import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { useApi } from '../hooks';
import { Alert, Loading, PageHead, useAction } from '../components/ui';
import Icon from '../components/Icon';
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

  // leaving the page any way (sidebar, back button) keeps unsaved answers as a draft; drafts never count
  const pending = useRef(null);
  pending.current = form && form.editable && dirty ? { answers, questions } : null;
  useEffect(
    () => () => {
      const p = pending.current;
      if (!p) return;
      const body = {
        answers: p.questions.map((q) => ({
          questionId: q.id,
          value: p.answers[q.id]?.na ? null : p.answers[q.id]?.value ?? null,
          na: !!p.answers[q.id]?.na,
          comment: p.answers[q.id]?.comment || null,
        })),
      };
      api.put(`/appraisals/${id}/reviews/${role}`, body).catch(() => {});
    },
    [id, role]
  );
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
      pending.current = null;
      navigate('/', { replace: false });
    }
  };

  const isMissing = (q) => {
    const a = answers[q.id];
    if (!a || (!a.na && !a.value)) return true;
    return form.commentsRequiredForExtremes && !a.na && extreme(a.value) && !a.comment?.trim();
  };
  const isAnswered = (q) => answers[q.id] && (answers[q.id].na || answers[q.id].value);
  const pctDone = questions.length ? Math.round((answered / questions.length) * 100) : 0;
  const statusPill =
    form.status === 'SUBMITTED' ? <span className="pill ok">Submitted</span> : form.status === 'DRAFT' ? <span className="pill blue">Draft</span> : <span className="pill">Not started</span>;

  return (
    <div>
      <PageHead
        crumb={
          <Link
            className="crumb"
            to="/"
          >
            <Icon name="back" size={15} /> My work
          </Link>
        }
        title={`${ROLE_LABEL[role]} review · ${form.employeeName}`}
        sub={
          <span className="row" style={{ gap: 8 }}>
            {form.teamName} · {form.levelName || 'level not set'} {statusPill}
          </span>
        }
      />

      {!form.levelName && <Alert kind="warn">The employee's level is not set yet. The TL or Manager sets it on the appraisal page.</Alert>}
      {form.cycleStatus !== 'OPEN' && <Alert kind="info">This cycle is closed; the review is read-only.</Alert>}
      {form.status === 'SUBMITTED' && <Alert kind="info">You submitted this review. Ask an admin if it needs to be reopened.</Alert>}
      {editable && (
        <Alert kind="info">
          {role === 'SELF' ? 'Answer about yourself. ' : "Every question is about 'the employee'. "}
          Pick 1 (never / poor) to 10 (always / excellent) for the whole period, or N/A if you could not observe it.
          {form.commentsRequiredForExtremes && ' A 9–10 or 1–2 needs a one-line comment with a real example.'}
        </Alert>
      )}

      <div className="review-layout">
        <nav className="toc" aria-label="Sections">
          <div style={{ padding: '6px 10px 10px' }}>
            <div className="small muted" style={{ fontWeight: 600 }}>
              {pctDone}% complete
            </div>
            <div className="progress" style={{ marginTop: 6 }}>
              <div style={{ width: `${pctDone}%` }} />
            </div>
          </div>
          {form.categories.map((c) => {
            const n = c.questions.filter(isAnswered).length;
            return (
              <a key={c.id} href={`#cat-${c.id}`} className={n === c.questions.length ? 'complete' : ''}>
                <span>{c.name}</span>
                <span className="count">
                  {n}/{c.questions.length}
                </span>
              </a>
            );
          })}
        </nav>

        <div>
          {form.categories.map((c) => (
            <section key={c.id} id={`cat-${c.id}`} className="card review-cat">
              <div className="card-head">
                <h3>{c.name}</h3>
                <span className="pill blue plain">{c.block === 'COMMON' ? 'Common competencies' : 'Role-specific'}</span>
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
                        {q.reviewedBy === 'TL_MANAGER_ONLY' && (
                          <span className="pill plain small" style={{ marginLeft: 8 }}>
                            TL / Manager only
                          </span>
                        )}
                      </span>
                      {q.guidance && (
                        <button className="link small" onClick={() => setOpenGuide((g) => ({ ...g, [q.id]: !g[q.id] }))} aria-expanded={!!openGuide[q.id]}>
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
                          className={`v${v} ${!a.na && a.value === v ? 'sel' : ''}`}
                          disabled={!editable}
                          title={scaleLabel[v]}
                          role="radio"
                          aria-checked={!a.na && a.value === v}
                          onClick={() => set(q.id, { value: v, na: false })}
                        >
                          {v}
                        </button>
                      ))}
                      <button type="button" className={`na ${a.na ? 'sel' : ''}`} disabled={!editable} role="radio" aria-checked={!!a.na} onClick={() => set(q.id, { na: !a.na, value: null })}>
                        N/A
                      </button>
                    </div>
                    <div className="scale-hint">{a.na ? 'Not observed' : a.value ? scaleLabel[a.value] : ''}</div>
                    {(needsComment || a.comment || (editable && a.showComment)) && (
                      <div className="q-extra">
                        <textarea
                          aria-label={`Comment for question ${q.number}`}
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
                          <Icon name="message" size={14} /> Add a comment
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </section>
          ))}
          {questions.length === 0 && <div className="card card-body muted">No questions to answer for this level.</div>}

          <div className="sticky-bar">
            <div className="row" style={{ gap: 14 }}>
              <strong>
                {answered} / {questions.length}
              </strong>
              <div className="progress">
                <div style={{ width: `${pctDone}%` }} />
              </div>
              {dirty && <span className="muted small">Unsaved changes</span>}
            </div>
            {(action.error || action.notice) && (
              <div className="grow" style={{ flexBasis: '100%' }}>
                <Alert onClose={() => action.setError(null)}>{action.error}</Alert>
                <Alert kind="ok">{action.notice}</Alert>
              </div>
            )}
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
    </div>
  );
}
