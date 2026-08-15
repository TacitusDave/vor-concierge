/* eslint-disable react-refresh/only-export-components -- context + hook are colocated intentionally */
import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { get, post } from '../api/client.js';

// Mirrors backend security.Capability bit-for-bit.
export const Capability = {
  UPLOAD_DOCUMENTS: 1,
  MANAGE_USERS: 1 << 1,
  MANAGE_DEPARTMENTS: 1 << 2,
  MANAGE_BILLING: 1 << 3,
  VIEW_ALL_ORG_CHATS: 1 << 4,
  MANAGE_ROLES: 1 << 5,
  INVITE_USERS: 1 << 6,
  VIEW_ANALYTICS: 1 << 7,
};

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const me = await get('/auth/me');
      setUser(me);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial session check on mount
    refresh();
  }, [refresh]);

  const login = useCallback(async (email, password, totp) => {
    const result = await post('/auth/login', { email, password, totp: totp || undefined });
    await refresh();
    return result;
  }, [refresh]);

  const logout = useCallback(async () => {
    await post('/auth/logout');
    setUser(null);
  }, []);

  const isPlatformOperator = !!user?.isPlatformOperator;
  const hasCapability = useCallback(
    (bit) => !!user && (isPlatformOperator || (user.roleCapabilityMask & bit) !== 0),
    [user, isPlatformOperator]
  );

  return (
    <AuthContext.Provider value={{ user, loading, isPlatformOperator, hasCapability, login, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
