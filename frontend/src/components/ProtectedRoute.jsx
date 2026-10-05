import { Navigate, useLocation } from 'react-router-dom';

import { useAuth } from '../context/AuthContext';
import { PageLoader } from './Feedback';

/**
 * Route guard. `roles` is optional: when present the signed-in user's role must be
 * one of them, otherwise the user is sent to the dashboard they are allowed to see.
 * The server enforces the same rules - this only avoids rendering a page that
 * would immediately fail every request it makes.
 */
export function ProtectedRoute({ roles, children }) {
  const { isAuthenticated, isLoading, role } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <PageLoader label="Restoring your session" />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (roles && !roles.includes(role)) {
    return <Navigate to={homeFor(role)} replace />;
  }

  return children;
}

export function homeFor(role) {
  if (role === 'ADMIN') return '/admin';
  if (role === 'STAFF') return '/staff';
  return '/dashboard';
}

export function GuestRoute({ children }) {
  const { isAuthenticated, isLoading, role } = useAuth();

  if (isLoading) {
    return <PageLoader label="Restoring your session" />;
  }

  if (isAuthenticated) {
    return <Navigate to={homeFor(role)} replace />;
  }

  return children;
}