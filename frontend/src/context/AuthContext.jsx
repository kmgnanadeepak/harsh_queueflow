import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { authApi } from '../api/endpoints';
import { readToken, writeToken } from '../api/client';

const AuthContext = createContext(null);

/**
 * Holds the authenticated session.
 *
 * The JWT lives in localStorage so a reload keeps the user signed in, and the
 * user record is re-fetched from /api/auth/me on boot so a token that was
 * revoked or belongs to a deactivated account cannot keep a stale session alive.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState(readToken() ? 'loading' : 'anonymous');

  useEffect(() => {
    if (!readToken()) {
      setStatus('anonymous');
      return;
    }

    let cancelled = false;
    authApi
      .me()
      .then(({ data }) => {
        if (cancelled) return;
        setUser(data);
        setStatus('authenticated');
      })
      .catch(() => {
        if (cancelled) return;
        writeToken(null);
        setUser(null);
        setStatus('anonymous');
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const adopt = useCallback(({ data }) => {
    writeToken(data.token);
    setUser(data.user);
    setStatus('authenticated');
    return data.user;
  }, []);

  const login = useCallback(
    async (credentials) => adopt(await authApi.login(credentials)),
    [adopt],
  );

  const register = useCallback(async (payload) => adopt(await authApi.register(payload)), [adopt]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // A stateless JWT is discarded client side regardless of the response.
    }
    writeToken(null);
    setUser(null);
    setStatus('anonymous');
  }, []);

  const value = useMemo(
    () => ({
      user,
      status,
      isAuthenticated: status === 'authenticated',
      isLoading: status === 'loading',
      role: user?.role ?? null,
      isAdmin: user?.role === 'ADMIN',
      isStaff: user?.role === 'STAFF',
      isCustomer: user?.role === 'USER',
      login,
      register,
      logout,
    }),
    [user, status, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}