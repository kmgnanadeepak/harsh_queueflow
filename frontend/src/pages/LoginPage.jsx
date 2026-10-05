import { useEffect, useState } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';

import { authApi } from '../api/endpoints';
import { Alert, Spinner } from '../components/Feedback';
import { useAuth } from '../context/AuthContext';

/**
 * Email + password sign-in.
 *
 * The "Continue with Google" button is only rendered when
 * /api/auth/google/status reports the provider as configured, so the UI never
 * offers a flow the backend cannot complete.
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [google, setGoogle] = useState({ enabled: false, loading: true });

  useEffect(() => {
    const expired = window.sessionStorage.getItem('queueflow.sessionExpired');
    if (expired) {
      setError(expired);
      window.sessionStorage.removeItem('queueflow.sessionExpired');
    }
    authApi
      .googleStatus()
      .then(({ data }) => setGoogle({ enabled: Boolean(data.enabled), loading: false }))
      .catch(() => setGoogle({ enabled: false, loading: false }));
  }, []);

  const submit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const user = await login(form);
      const target = location.state?.from ?? defaultHome(user.role);
      navigate(target, { replace: true });
    } catch (requestError) {
      setError(describe(requestError));
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Sign in to QueueFlow"
      subtitle="Track your place in line from any device."
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        {error && <Alert onDismiss={() => setError(null)}>{error}</Alert>}

        <div>
          <label className="label" htmlFor="email">
            Email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            className="input"
            placeholder="you@example.com"
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
          />
        </div>

        <div>
          <label className="label" htmlFor="password">
            Password
          </label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            required
            className="input"
            placeholder="••••••••"
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
          />
        </div>

        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting && <Spinner className="h-4 w-4" />}
          {submitting ? 'Signing in' : 'Sign in'}
        </button>
      </form>

      {google.loading ? (
        <div className="my-4 h-10" />
      ) : google.enabled ? (
        <>
          <div className="my-4 flex items-center gap-3 text-xs uppercase tracking-wide text-slate-400">
            <span className="h-px flex-1 bg-slate-200" />
            or
            <span className="h-px flex-1 bg-slate-200" />
          </div>
          <a href="/oauth2/authorization/google" className="btn-secondary w-full">
            <GoogleMark />
            Continue with Google
          </a>
        </>
      ) : (
        <p className="mt-4 text-center text-xs text-slate-400">
          Google sign-in is not configured on this server.
        </p>
      )}

      <p className="mt-6 text-center text-sm text-slate-600">
        No account yet?{' '}
        <Link to="/register" className="font-semibold text-brand-600 hover:underline">
          Create one
        </Link>
      </p>
    </AuthShell>
  );
}

function AuthShell({ title, subtitle, children }) {
  return (
    <div className="grid min-h-screen place-items-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-6 flex flex-col items-center text-center">
          <span className="grid h-12 w-12 place-items-center rounded-xl bg-brand-600 text-xl font-bold text-white">
            Q
          </span>
          <h1 className="mt-3 text-2xl font-semibold text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
        </div>

        <div className="card p-6">{children}</div>
      </div>
    </div>
  );
}

function GoogleMark() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.4a5.5 5.5 0 01-2.4 3.6v3h3.9c2.3-2.1 3.6-5.2 3.6-8.8z"
      />
      <path
        fill="#34A853"
        d="M12 24c3.2 0 5.9-1.1 7.9-2.9l-3.9-3a7.2 7.2 0 01-10.7-3.8h-4v3.1A12 12 0 0012 24z"
      />
      <path fill="#FBBC05" d="M5.3 14.3a7.1 7.1 0 010-4.6v-3.1h-4a12 12 0 000 10.8l4-3.1z" />
      <path
        fill="#EA4335"
        d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.4-3.4A11.6 11.6 0 0012 0 12 12 0 001.3 6.6l4 3.1A7.2 7.2 0 0112 4.8z"
      />
    </svg>
  );
}

export function defaultHome(role) {
  if (role === 'ADMIN') return '/admin';
  if (role === 'STAFF') return '/staff';
  return '/dashboard';
}

/** Turns a normalised API error into readable text plus per-field hints. */
export function describe(error) {
  const violations = error?.violations ?? [];
  const detail = violations.length
    ? violations.map((item) => `${item.field}: ${item.message}`).join(' ')
    : null;
  return detail ? `${error.message} (${detail})` : error?.message ?? 'Something went wrong.';
}