import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import { useApi } from '../hooks';
import { useCyclePicker } from '../components/CyclePicker';
import { Alert, Loading, Rating } from '../components/ui';
import { fmt, ROLE_LABEL } from '../format';

function RoleButton({ appraisalId, task }) {
  const label = ROLE_LABEL[task.role];
  if (task.status === 'SUBMITTED')
    return (
      <Link className="btn small ghost" to={`/appraisals/${appraisalId}/review/${task.role}`}>
        {label} ✓ submitted
      </Link>
    );
  return (
    <Link className={`btn small ${task.status === 'DRAFT' ? 'secondary' : ''}`} to={`/appraisals/${appraisalId}/review/${task.role}`}>
      {task.status === 'DRAFT' ? `Continue ${label}` : `Review as ${label}`}
    </Link>
  );
}

export default function Home() {
  const { user } = useAuth();
  const { cycleId, picker, cycles, error: cyclesError } = useCyclePicker();
  const { data, error, loading } = useApi(cycleId ? `/cycles/${cycleId}/tasks` : null, [cycleId]);
  const { data: mine } = useApi('/my/appraisals');

  const myTask = data && data.tasks.find((t) => t.appraisalId === data.myAppraisalId);
  const others = data ? data.tasks.filter((t) => t.appraisalId !== data.myAppraisalId) : [];
  const pending = others.reduce((n, t) => n + t.roles.filter((r) => r.status !== 'SUBMITTED' && r.role !== 'PEER').length, 0);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Hello, {user.fullName.split(' ')[0]}</h1>
          <div className="muted">Reviews you can give and your own appraisal.</div>
        </div>
        {picker}
      </div>
      <Alert>{cyclesError || error}</Alert>
      {cycles && cycles.length === 0 && <div className="card card-body muted">No appraisal cycle is open yet.</div>}
      {loading && <Loading />}

      {data && myTask && (
        <div className="card">
          <div className="card-head">
            <h3>My appraisal · {data.cycle.name}</h3>
            <span className="muted small">
              {myTask.teamName} · {myTask.levelName || 'level not set'}
            </span>
          </div>
          <div className="card-body row between">
            <div className="muted">Rate yourself (optional but recommended) and add your achievements and goals for the period.</div>
            <div className="row">
              {myTask.roles.map((r) => (
                <RoleButton key={r.role} appraisalId={myTask.appraisalId} task={r} />
              ))}
              <Link className="btn small secondary" to={`/appraisals/${myTask.appraisalId}`}>
                Achievements &amp; goals
              </Link>
            </div>
          </div>
        </div>
      )}

      {data && (
        <div className="card">
          <div className="card-head">
            <h3>People you can review</h3>
            {pending > 0 && <span className="pill warn">{pending} TL / Manager review(s) to do</span>}
          </div>
          {others.length === 0 ? (
            <div className="card-body muted">Nobody to review in this cycle.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Team</th>
                    <th>Level</th>
                    <th>Your reviews</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {others.map((t) => (
                    <tr key={t.appraisalId}>
                      <td>{t.employeeName}</td>
                      <td>{t.teamName}</td>
                      <td>{t.levelName || <span className="pill warn">Not set</span>}</td>
                      <td>
                        <div className="row">
                          {t.roles.map((r) => (
                            <RoleButton key={r.role} appraisalId={t.appraisalId} task={r} />
                          ))}
                        </div>
                      </td>
                      <td className="right">
                        {(t.canLead || t.canViewResults) && <Link to={`/appraisals/${t.appraisalId}`}>Open appraisal</Link>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {mine && mine.some((m) => m.cycle.status === 'CLOSED') && (
        <div className="card">
          <div className="card-head">
            <h3>My results</h3>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Cycle</th>
                  <th>Level</th>
                  <th className="num">Final score</th>
                  <th>Rating</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {mine
                  .filter((m) => m.cycle.status === 'CLOSED')
                  .map((m) => (
                    <tr key={m.appraisalId}>
                      <td>{m.cycle.name}</td>
                      <td>{m.levelName || '–'}</td>
                      <td className="num">{fmt(m.finalScore)}</td>
                      <td>
                        <Rating rating={m.rating} />
                      </td>
                      <td className="right">
                        <Link to={`/appraisals/${m.appraisalId}`}>Details</Link>
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
