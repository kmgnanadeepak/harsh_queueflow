import { useMemo, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';

import { useAuth } from '../context/AuthContext';
import { useRealtime } from '../hooks/useRealtime';
import { humanise } from './Badges';
import { NotificationBell } from './NotificationBell';

const NAVIGATION = [
  { to: '/dashboard', label: 'My queue', roles: ['USER'] },
  { to: '/staff', label: 'Counter', roles: ['STAFF', 'ADMIN'] },
  { to: '/admin', label: 'Administration', roles: ['ADMIN'] },
];

/**
 * Authenticated shell: header, role aware navigation and the single STOMP
 * connection shared by every page underneath it.
 */
export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [refreshSignal, setRefreshSignal] = useState(0);

  const subscriptions = useMemo(
    () => (user?.id ? [`/topic/notifications/${user.id}`] : []),
    [user],
  );

  useRealtime({
    enabled: Boolean(user?.id),
    subscriptions,
    onEvent: () => setRefreshSignal((value) => value + 1),
  });

  const links = NAVIGATION.filter((item) => item.roles.includes(user?.role));

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-6">
            <NavLink to="/" className="flex items-center gap-2">
              <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 text-sm font-bold text-white">
                Q
              </span>
              <span className="text-lg font-semibold text-slate-900">QueueFlow</span>
            </NavLink>

            <nav className="hidden items-center gap-1 sm:flex">
              {links.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    `rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                      isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex items-center gap-2">
            <NotificationBell refreshSignal={refreshSignal} />

            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium text-slate-800">{user?.name}</p>
              <p className="text-xs text-slate-500">{humanise(user?.role)}</p>
            </div>

            <button type="button" onClick={handleLogout} className="btn-secondary">
              Sign out
            </button>
          </div>
        </div>

        <nav className="flex items-center gap-1 border-t border-slate-100 px-4 py-2 sm:hidden">
          {links.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `rounded-lg px-3 py-1.5 text-sm font-medium ${
                  isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}