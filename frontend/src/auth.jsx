import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, setUnauthorizedHandler, tokenStore } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(!!tokenStore.get());

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (tokenStore.get()) {
      api.get('/auth/me').then(setUser).catch(logout).finally(() => setLoading(false));
    }
  }, [logout]);

  const login = async (username, password) => {
    const res = await api.post('/auth/login', { username, password });
    tokenStore.set(res.token);
    setUser(res.user);
    return res.user;
  };

  const hasRole = (r) => !!user && user.roles.includes(r);
  const isLead = () => hasRole('TL') || hasRole('MANAGER') || hasRole('ADMIN');

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, hasRole, isLead }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
