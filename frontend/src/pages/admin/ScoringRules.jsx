import { useEffect, useState } from 'react';
import { api } from '../../api';
import { useApi } from '../../hooks';
import { Alert, Loading, useAction } from '../../components/ui';

const toPct = (v) => Math.round(v * 10000) / 100;

function WeightsCard() {
  const { data, error } = useApi('/admin/settings');
  const [w, setW] = useState(null);
  const action = useAction();
  useEffect(() => {
    if (data) setW(Object.fromEntries(Object.entries(data).map(([k, v]) => [k, toPct(v)])));
  }, [data]);
  if (error) return <Alert>{error}</Alert>;
  if (!w) return <Loading />;
  const num = (k) => Number(w[k]) || 0;
  const blockTotal = num('commonWeight') + num('roleWeight');
  const reviewerTotal = num('selfWeight') + num('peerWeight') + num('tlWeight') + num('managerWeight');
  const field = (k, label) => (
    <label className="field" key={k}>
      {label}
      <span className="row">
        <input className="num" type="number" min="0" max="100" step="0.5" value={w[k]} onChange={(e) => setW({ ...w, [k]: e.target.value })} /> %
      </span>
    </label>
  );
  const save = () =>
    action.run(
      () => api.put('/admin/settings', Object.fromEntries(Object.entries(w).map(([k, v]) => [k, (Number(v) || 0) / 100]))),
      'Weights saved. They apply to cycles opened from now on (or after a sync).'
    );
  return (
    <div className="card">
      <div className="card-head">
        <h3>Weights</h3>
        <button className="btn small" disabled={action.busy} onClick={save}>
          Save weights
        </button>
      </div>
      <div className="card-body stack">
        <Alert>{action.error}</Alert>
        <Alert kind="ok">{action.notice}</Alert>
        <div>
          <strong>Final score blocks</strong>{' '}
          <span className={Math.abs(blockTotal - 100) < 0.01 ? 'pill ok' : 'pill warn'}>total {blockTotal}%</span>
        </div>
        <div className="form-grid">
          {field('commonWeight', 'Common competencies')}
          {field('roleWeight', 'Role-specific')}
        </div>
        <div>
          <strong>Who reviews: weight of each review on a question</strong>{' '}
          <span className={Math.abs(reviewerTotal - 100) < 0.01 ? 'pill ok' : 'pill warn'}>total {reviewerTotal}%</span>
          <div className="muted small">If a review is blank or N/A the others are re-weighted. Several people in the same role are averaged first.</div>
        </div>
        <div className="form-grid">
          {field('selfWeight', 'Self')}
          {field('peerWeight', 'Peer')}
          {field('tlWeight', 'TL (team lead)')}
          {field('managerWeight', 'Manager')}
        </div>
      </div>
    </div>
  );
}

function BandsCard({ config, reload }) {
  const [bands, setBands] = useState(config.ratingBands);
  const action = useAction();
  const save = async () => {
    const r = await action.run(() => api.put('/admin/rating-bands', bands.map((b) => ({ label: b.label, minScore: Number(b.minScore) }))), 'Rating bands saved.');
    if (r) reload();
  };
  return (
    <div className="card">
      <div className="card-head">
        <h3>Overall rating bands</h3>
        <div className="row">
          <button className="btn small secondary" onClick={() => setBands([...bands, { label: '', minScore: 0 }])}>
            + Band
          </button>
          <button className="btn small" disabled={action.busy} onClick={save}>
            Save bands
          </button>
        </div>
      </div>
      <div className="card-body">
        <Alert>{action.error}</Alert>
        <Alert kind="ok">{action.notice}</Alert>
        <div className="muted small" style={{ marginBottom: 8 }}>
          A final score gets the band with the highest "from score" not above it (6.9 stays in the band that starts at 5; 7.0 moves to the band that starts at 7).
        </div>
        <table>
          <thead>
            <tr>
              <th>Rating</th>
              <th className="num">From score</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {bands.map((b, i) => (
              <tr key={i}>
                <td>
                  <input value={b.label} onChange={(e) => setBands(bands.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))} style={{ width: '100%' }} />
                </td>
                <td className="num">
                  <input className="num" type="number" step="0.1" value={b.minScore} onChange={(e) => setBands(bands.map((x, j) => (j === i ? { ...x, minScore: e.target.value } : x)))} />
                </td>
                <td className="right">
                  <button className="link danger" onClick={() => setBands(bands.filter((_, j) => j !== i))}>
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ListCard({ title, help, items: initial, onSave }) {
  const [items, setItems] = useState(initial);
  const action = useAction();
  return (
    <div className="card">
      <div className="card-head">
        <h3>{title}</h3>
        <div className="row">
          <button className="btn small secondary" onClick={() => setItems([...items, ''])}>
            + Item
          </button>
          <button className="btn small" disabled={action.busy} onClick={() => action.run(() => onSave(items.filter((s) => s.trim())), 'Saved.')}>
            Save
          </button>
        </div>
      </div>
      <div className="card-body stack">
        <Alert>{action.error}</Alert>
        <Alert kind="ok">{action.notice}</Alert>
        {help && <div className="muted small">{help}</div>}
        {items.map((s, i) => (
          <div key={i} className="row">
            <input className="grow" value={s} onChange={(e) => setItems(items.map((x, j) => (j === i ? e.target.value : x)))} />
            <button className="icon-btn" disabled={i === 0} onClick={() => setItems(items.map((x, j) => (j === i - 1 ? items[i] : j === i ? items[i - 1] : x)))} aria-label="Move up">
              ↑
            </button>
            <button className="link danger" onClick={() => setItems(items.filter((_, j) => j !== i))}>
              Remove
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function ScaleCard({ labels }) {
  const [rows, setRows] = useState(() => {
    const m = Object.fromEntries(labels.map((l) => [l.value, l.label]));
    return [10, 9, 8, 7, 6, 5, 4, 3, 2, 1].map((v) => ({ value: v, label: m[v] || '' }));
  });
  const action = useAction();
  return (
    <div className="card">
      <div className="card-head">
        <h3>What the numbers 1–10 mean</h3>
        <button className="btn small" disabled={action.busy} onClick={() => action.run(() => api.put('/admin/scale-labels', rows.filter((r) => r.label.trim())), 'Saved.')}>
          Save
        </button>
      </div>
      <div className="card-body stack">
        <Alert>{action.error}</Alert>
        <Alert kind="ok">{action.notice}</Alert>
        <div className="muted small">Shown to reviewers as a tooltip on each number.</div>
        {rows.map((r, i) => (
          <div key={r.value} className="row">
            <strong style={{ width: 24 }}>{r.value}</strong>
            <input className="grow" value={r.label} onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))} />
          </div>
        ))}
      </div>
    </div>
  );
}

export default function ScoringRules() {
  const { data: config, error, reload } = useApi('/config');
  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Scoring rules</h1>
          <div className="muted">Weights, rating bands and pick lists. Category points per level are edited on the Questions page.</div>
        </div>
      </div>
      <Alert>{error}</Alert>
      <WeightsCard />
      {config && (
        <>
          <BandsCard key={JSON.stringify(config.ratingBands)} config={config} reload={reload} />
          <ScaleCard labels={config.scaleLabels} />
          <ListCard title="Achievement situation (pick list)" items={config.pickLists.SITUATION} onSave={(items) => api.put('/admin/pick-lists/SITUATION', { items })} />
          <ListCard title="Business impact (pick list)" items={config.pickLists.BUSINESS_IMPACT} onSave={(items) => api.put('/admin/pick-lists/BUSINESS_IMPACT', { items })} />
        </>
      )}
    </div>
  );
}
