
import { Navigate, Route, Routes } from 'react-router-dom';

import { AppLayout } from './components/AppLayout';
import { GuestRoute, ProtectedRoute } from './components/ProtectedRoute';
import { useAuth } from './context/AuthContext';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { defaultHome, LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { StaffDashboardPage } from './pages/StaffDashboardPage';
import { UserDashboardPage } from './pages/UserDashboardPage';

/**
 * Route map.
 *
 * Each role lands on its own dashboard and the guards keep a user out of the
 * areas that do not belong to them. The backend enforces the same rules - the
 * guards exist so nobody sees a screen full of failed requests.
 */
export default function App() {
  const { role } = useAuth();

  return (
    <Routes>
      <Route
        path="/login"
        element={
          <GuestRoute>
            <LoginPage />
          </GuestRoute>
        }
      />
      <Route
        path="/register"
        element={
          <GuestRoute>
            <RegisterPage />
          </GuestRoute>
        }
      />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />

      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route
          index
          element={<Navigate to={role ? defaultHome(role) : '/dashboard'} replace />}
        />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute roles={['USER']}>
              <UserDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/staff"
          element={
            <ProtectedRoute roles={['STAFF', 'ADMIN']}>
              <StaffDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminDashboardPage />
            </ProtectedRoute>
          }
        />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}