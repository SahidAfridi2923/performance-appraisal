import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import { useApi } from '../hooks';
import { useCyclePicker } from '../components/CyclePicker';
import { Alert, Loading, PageHead, Rating } from '../components/ui';
import Avatar, { Person } from '../components/Avatar';
import Icon from '../components/Icon';
import { fmt, ROLE_LABEL } from '../format';

function RoleButton({ appraisalId, task }) {
  const label = ROLE_LABEL[task.role];
  const to = `/appraisals/${appraisalId}/review/${task.role}`;
  if (task.status === 'SUBMITTED')
    return (
      <Link className="btn small done" to={to}>
        <Icon name="check" size={15} /> {label}
      </Link>
    );
  return (
    <Link className={`btn small ${task.status === 'DRAFT' ? 'secondary' : ''}`} to={to}>
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
  const all = others.flatMap((t) => t.roles);
  const isOpen = data?.cycle.status === 'OPEN';
  const leadTodo = isOpen ? all.filter((r) => r.status !== 'SUBMITTED' && r.role !== 'PEER').length : 0;
  const done = all.filter((r) => r.status === 'SUBMITTED').length;
  const hour = new Date().getHours();
  const greet = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  const closed = (mine || []).filter((m) => m.cycle.status === 'CLOSED');

  return (
    <div>
      <PageHead title={`${greet}, ${user.fullName.split(' ')[0]}`} sub="Your reviews to give and your own appraisal for the selected cycle.">
        {picker}
      </PageHead>
      <Alert>{cyclesError || error}</Alert>
      {cycles && cycles.length === 0 && (
        <div className="card card-body muted">No appraisal cycle is open yet. Your admin opens one when it is time for reviews.</div>
      )}
      {loading && <Loading />}

      {data && (
        <div className="stats">
          <div className="stat">
            <div className="label">Cycle</div>
            <div className="value" style={{ fontSize: 20, marginTop: 8 }}>
              {data.cycle.name}
            </div>
            <span className={`pill ${data.cycle.status === 'OPEN' ? 'ok' : 'blue'}`}>{data.cycle.status === 'OPEN' ? 'Open for reviews' : 'Closed'}</span>
          </div>
          <div className="stat">
            <div className="label">TL / Manager reviews to do</div>
            <div className="value" style={{ color: leadTodo ? 'var(--accent)' : undefined }}>{leadTodo}</div>
          </div>
          <div className="stat">
            <div className="label">Reviews you submitted</div>
            <div className="value">
              {done}
              <small> / {all.length}</small>
            </div>
          </div>
          <div className="stat">
            <div className="label">People you can review</div>
            <div className="value">{others.length}</div>
          </div>
        </div>
      )}

      {data && myTask && (
        <div className="card hero-card">
          <Avatar name={user.fullName} large />
          <div>
            <h3 style={{ fontSize: 17 }}>Your appraisal</h3>
            <div className="muted">
              {myTask.teamName} · {myTask.levelName || 'level not set yet'} · rate yourself and record your achievements and goals.
            </div>
          </div>
          <div className="task-actions">
            {myTask.roles.map((r) => (
              <RoleButton key={r.role} appraisalId={myTask.appraisalId} task={r} />
            ))}
            <Link className="btn small secondary" to={`/appraisals/${myTask.appraisalId}`}>
              <Icon name="star" size={15} /> Achievements &amp; goals
            </Link>
          </div>
        </div>
      )}

      {data && (
        <div className="card">
          <div className="card-head">
            <h3>People you can review</h3>
            {leadTodo > 0 && <span className="pill accent">{leadTodo} waiting on you</span>}
          </div>
          {others.length === 0 ? (
            <div className="card-body muted">Nobody to review in this cycle.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Level</th>
                    <th>Your reviews</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {others.map((t) => (
                    <tr key={t.appraisalId}>
                      <td>
                        <Person name={t.employeeName} meta={t.teamName} />
                      </td>
                      <td>{t.levelName || <span className="pill warn">Not set</span>}</td>
                      <td>
                        <div className="row" style={{ gap: 8 }}>
                          {t.roles.map((r) => (
                            <RoleButton key={r.role} appraisalId={t.appraisalId} task={r} />
                          ))}
                        </div>
                      </td>
                      <td className="right">
                        {(t.canLead || t.canViewResults) && (
                          <Link className="btn small ghost" to={`/appraisals/${t.appraisalId}`}>
                            Open
                          </Link>
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

      {closed.length > 0 && (
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
                {closed.map((m) => (
                  <tr key={m.appraisalId}>
                    <td>{m.cycle.name}</td>
                    <td>{m.levelName || '–'}</td>
                    <td className="num">
                      <strong>{fmt(m.finalScore)}</strong>
                    </td>
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
