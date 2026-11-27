import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useApi } from '../hooks';
import { useCyclePicker } from '../components/CyclePicker';
import { Alert, Loading, PageHead, Rating, ScoreStatus } from '../components/ui';
import { fmt, ROLE_LABEL, ROLE_ORDER } from '../format';
import Avatar from '../components/Avatar';

export default function Summary() {
  const { cycleId, picker, error: cErr } = useCyclePicker();
  const { data, error, loading } = useApi(cycleId ? `/cycles/${cycleId}/summary` : null, [cycleId]);
  const [team, setTeam] = useState('');
  const [level, setLevel] = useState('');

  const rows = useMemo(() => (data ? data.rows.filter((r) => (!team || r.teamName === team) && (!level || r.levelName === level)) : []), [data, team, level]);
  const levels = useMemo(() => (data ? [...new Set(data.rows.map((r) => r.levelName).filter(Boolean))] : []), [data]);

  // flag teams whose average is ~2 points away from the others (Rules sheet: "Check before sharing")
  const outliers = useMemo(() => {
    if (!data) return new Set();
    const avgs = data.teams.filter((t) => t.average !== null);
    const out = new Set();
    avgs.forEach((t) => {
      const others = avgs.filter((o) => o !== t);
      if (!others.length) return;
      const mean = others.reduce((s, o) => s + o.average, 0) / others.length;
      if (Math.abs(t.average - mean) >= 2) out.add(t.teamName);
    });
    return out;
  }, [data]);

  return (
    <div>
      <PageHead title="Summary" sub="Scores are out of 10. Compare people within the same level, not a Junior against a Team Lead.">
        {picker}
      </PageHead>
      <Alert>{cErr || error}</Alert>
      {loading && <Loading />}
      {data && (
        <>
          <div className="team-grid">
            {data.teams.map((t) => (
              <button key={t.teamName} type="button" className={`team-card ${team === t.teamName ? 'active' : ''}`} onClick={() => setTeam(team === t.teamName ? '' : t.teamName)} aria-pressed={team === t.teamName}>
                <div className="row between">
                  <span className="t">{t.teamName}</span>
                  {outliers.has(t.teamName) && (
                    <span className="pill accent" title="About 2 points away from the other teams; discuss with the lead before sharing results">
                      Check
                    </span>
                  )}
                </div>
                <div className="avg">
                  {fmt(t.average)}
                  <span className="muted" style={{ fontSize: 14, fontWeight: 500 }}>
                    {' '}
                    avg
                  </span>
                </div>
                <div className="muted small">
                  {t.scored} of {t.resources} scored
                </div>
                <div className="bar">
                  <div style={{ width: `${t.resources ? (t.scored / t.resources) * 100 : 0}%` }} />
                </div>
              </button>
            ))}
          </div>

          <div className="card">
            <div className="card-head">
              <h3>People</h3>
              <div className="row">
                <select value={team} onChange={(e) => setTeam(e.target.value)} aria-label="Filter by team">
                  <option value="">All teams</option>
                  {data.teams.map((t) => (
                    <option key={t.teamName}>{t.teamName}</option>
                  ))}
                </select>
                <select value={level} onChange={(e) => setLevel(e.target.value)} aria-label="Filter by level">
                  <option value="">All levels</option>
                  {levels.map((l) => (
                    <option key={l}>{l}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Resource</th>
                    <th>Level</th>
                    <th className="num">TL/Mgr answers</th>
                    <th>Reviews in</th>
                    <th className="num">Common</th>
                    <th className="num">Role</th>
                    <th className="num">Final</th>
                    <th>Rating</th>
                    <th className="num">Verified ach.</th>
                    <th className="num">Goals</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 && (
                    <tr>
                      <td colSpan={10} className="muted">
                        Nobody to show.
                      </td>
                    </tr>
                  )}
                  {rows.map((r) => (
                    <tr key={r.appraisalId}>
                      <td>
                        <Link to={`/appraisals/${r.appraisalId}`} className="person" style={{ textDecoration: 'none' }}>
                          <Avatar name={r.employeeName} />
                          <span>
                            <span className="name" style={{ display: 'block' }}>
                              {r.employeeName}
                            </span>
                            <span className="meta">{r.teamName}</span>
                          </span>
                        </Link>
                        {r.selfGapCount > 2 && (
                          <div className="muted small" title="Self vs TL/Manager differ by 3+ on several questions">
                            self-rating gap on {r.selfGapCount}
                          </div>
                        )}
                      </td>
                      <td>{r.levelName || <span className="pill warn">Not set</span>}</td>
                      <td className="num">
                        {r.leadAnswered} of {r.needed}
                      </td>
                      <td className="small">
                        {ROLE_ORDER.map((role) => (
                          <span key={role} className="nowrap" style={{ marginRight: 8 }}>
                            {ROLE_LABEL[role]} <strong>{r.submittedReviews[role] || 0}</strong>
                          </span>
                        ))}
                      </td>
                      <td className="num">{fmt(r.commonScore)}</td>
                      <td className="num">{fmt(r.roleScore)}</td>
                      <td className="num">{r.finalScore !== null ? <strong>{fmt(r.finalScore)}</strong> : <ScoreStatus status={r.status} short />}</td>
                      <td>
                        <Rating rating={r.rating} />
                      </td>
                      <td className="num">{r.verifiedAchievements}</td>
                      <td className="num">{r.goals}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
