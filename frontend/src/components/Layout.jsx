import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth';

export default function Layout() {
  const { user, logout, hasRole, isLead } = useAuth();
  const admin = hasRole('ADMIN');
  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">Performance Appraisal</div>
        <nav>
          <NavLink to="/" end>
            My work
          </NavLink>
          {isLead() && <NavLink to="/summary">Summary</NavLink>}
          {admin && (
            <>
              <span className="nav-sep" />
              <NavLink to="/admin/questions">Questions</NavLink>
              <NavLink to="/admin/scoring">Scoring rules</NavLink>
              <NavLink to="/admin/cycles">Cycles</NavLink>
              <NavLink to="/admin/users">Users</NavLink>
              <NavLink to="/admin/teams">Teams &amp; levels</NavLink>
            </>
          )}
        </nav>
        <div className="userbox">
          <NavLink to="/account" className="muted">
            {user.fullName}
          </NavLink>
          <button className="btn small ghost" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
