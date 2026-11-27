import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth';
import Icon from './Icon';
import Avatar from './Avatar';
import Logo, { LogoMark } from './Logo';

function Brand() {
  return (
    <NavLink to="/" className="brand" aria-label="HumAppraisal home">
      <Logo height={34} />
    </NavLink>
  );
}

export default function Layout() {
  const { user, logout, hasRole, isLead } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => {
    setOpen(false);
    // every new page starts at the top (React Router keeps the old scroll position otherwise)
    if (!location.hash) window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
  }, [location.pathname]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [open]);
  const admin = hasRole('ADMIN');
  const roleText = user.roles.map((r) => r[0] + r.slice(1).toLowerCase()).join(' · ');

  return (
    <div className={`shell ${open ? 'open' : ''}`} onClick={(e) => open && e.target === e.currentTarget && setOpen(false)}>
      <aside className="sidebar" aria-label="Main navigation">
        <Brand />
        <nav>
          <div className="nav-group">
            <div className="nav-label">Workspace</div>
            <NavLink to="/" end>
              <Icon name="work" /> My work
            </NavLink>
            {isLead() && (
              <NavLink to="/summary">
                <Icon name="chart" /> Summary
              </NavLink>
            )}
          </div>
          {admin && (
            <div className="nav-group">
              <div className="nav-label">Administration</div>
              <NavLink to="/admin/questions">
                <Icon name="list" /> Questions
              </NavLink>
              <NavLink to="/admin/scoring">
                <Icon name="sliders" /> Scoring rules
              </NavLink>
              <NavLink to="/admin/cycles">
                <Icon name="calendar" /> Cycles
              </NavLink>
              <NavLink to="/admin/users">
                <Icon name="users" /> Users
              </NavLink>
              <NavLink to="/admin/teams">
                <Icon name="layers" /> Teams &amp; levels
              </NavLink>
            </div>
          )}
        </nav>
        <div className="side-foot">
          <Avatar name={user.fullName} />
          <div className="who">
            <NavLink to="/account">{user.fullName}</NavLink>
            <span>{roleText}</span>
          </div>
          <button onClick={logout} aria-label="Sign out" title="Sign out">
            <Icon name="logout" size={16} />
          </button>
        </div>
      </aside>
      <div>
        <div className="mobile-bar">
          <button onClick={() => setOpen(true)} aria-label="Open menu">
            <Icon name="menu" />
          </button>
          <LogoMark size={30} />
          <span className="mobile-title">HumAppraisal</span>
        </div>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
