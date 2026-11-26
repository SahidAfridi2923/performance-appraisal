import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import Layout from './components/Layout';
import { Loading } from './components/ui';
import Login from './pages/Login';
import Home from './pages/Home';
import ReviewForm from './pages/ReviewForm';
import AppraisalPage from './pages/AppraisalPage';
import Summary from './pages/Summary';
import Account from './pages/Account';
import QuestionBank from './pages/admin/QuestionBank';
import ScoringRules from './pages/admin/ScoringRules';
import Cycles from './pages/admin/Cycles';
import CycleDetail from './pages/admin/CycleDetail';
import Users from './pages/admin/Users';
import TeamsLevels from './pages/admin/TeamsLevels';

function AdminOnly({ children }) {
  const { hasRole } = useAuth();
  return hasRole('ADMIN') ? children : <Navigate to="/" replace />;
}

export default function App() {
  const { user, loading } = useAuth();
  if (loading) return <Loading what="Signing in" />;
  if (!user) return <Login />;
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="appraisals/:id" element={<AppraisalPage />} />
        <Route path="appraisals/:id/review/:role" element={<ReviewForm />} />
        <Route path="summary" element={<Summary />} />
        <Route path="account" element={<Account />} />
        <Route path="admin/questions" element={<AdminOnly><QuestionBank /></AdminOnly>} />
        <Route path="admin/scoring" element={<AdminOnly><ScoringRules /></AdminOnly>} />
        <Route path="admin/cycles" element={<AdminOnly><Cycles /></AdminOnly>} />
        <Route path="admin/cycles/:id" element={<AdminOnly><CycleDetail /></AdminOnly>} />
        <Route path="admin/users" element={<AdminOnly><Users /></AdminOnly>} />
        <Route path="admin/teams" element={<AdminOnly><TeamsLevels /></AdminOnly>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
