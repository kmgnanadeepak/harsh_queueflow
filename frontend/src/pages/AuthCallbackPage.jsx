import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { writeToken } from '../api/client';
import { PageLoader } from '../components/Feedback';
import { useAuth } from '../context/AuthContext';
import { defaultHome } from './LoginPage';

/**
 * Landing page of the Google OAuth2 flow.
 *
 * The backend never sends a Google token to the browser: it exchanges the code,
 * links or creates the account server side, issues a QueueFlow JWT and redirects
 * here with ?token=. This page stores that JWT once and strips it from the URL so
 * it cannot end up in browser history or a referrer header.
 */
export function AuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated, role, isLoading } = useAuth();
  const [failure, setFailure] = useState(null);
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const token = params.get('token');
    const oauthError = params.get('error');

    if (oauthError) {
      setFailure(
        params.get('error_description') ??
          'Google sign-in was cancelled or refused. Please try again.',
      );
      return;
    }

    if (!token) {
      setFailure('Google sign-in did not return a QueueFlow token.');
      return;
    }

    writeToken(token);
    window.history.replaceState({}, '', '/auth/callback');
    // The context re-validates the token against /api/auth/me on the next render.
    window.location.assign('/');
  }, [params]);

  if (failure) {
    return (
      <div className="grid min-h-screen place-items-center px-4">
        <div className="card max-w-md p-6 text-center">
          <h1 className="text-lg font-semibold text-slate-900">Sign-in failed</h1>
          <p className="mt-2 text-sm text-slate-600">{failure}</p>
          <button type="button" className="btn-primary mt-5" onClick={() => navigate('/login')}>
            Back to sign in
          </button>
        </div>
      </div>
    );
  }

  if (isLoading || !isAuthenticated) {
    return <PageLoader label="Completing Google sign-in" />;
  }

  return <PageLoader label={`Redirecting to ${defaultHome(role)}`} />;
}