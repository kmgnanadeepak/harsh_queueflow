import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { authApi } from '../api/endpoints';
import { Alert, Spinner } from '../components/Feedback';
import { useAuth } from '../context/AuthContext';
import { describe, defaultHome } from './LoginPage';

const EMPTY = { name: '', email: '', phone: '', password: '', confirmPassword: '' };

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [google, setGoogle] = useState({ enabled: false, loading: true });

  useEffect(() => {
    authApi
      .googleStatus()
      .then(({ data }) => setGoogle({ enabled: Boolean(data.enabled), loading: false }))
      .catch(() => setGoogle({ enabled: false, loading: false }));
  }, []);

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  const submit = async (event) => {
    event.preventDefault();
    setError(null);

    if (form.password !== form.confirmPassword) {
      setError('The two passwords do not match.');
      return;
    }

    setSubmitting(true);
    try {
      const user = await register({
        name: form.name.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        password: form.password,
      });
      navigate(defaultHome(user.role), { replace: true });
    } catch (requestError) {
      setError(describe(requestError));
      setSubmitting(false);
    }
  };

  return (
    <div className="grid min-h-screen place-items-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-6 flex flex-col items-center text-center">
          <span className="grid h-12 w-12 place-items-center rounded-xl bg-brand-600 text-xl font-bold text-white">
            Q
          </span>
          <h1 className="mt-3 text-2xl font-semibold text-slate-900">Create your account</h1>
          <p className="mt-1 text-sm text-slate-500">Join a queue in seconds, no email confirmation needed.</p>
        </div>

        <div className="card p-6">
          <form onSubmit={submit} className="space-y-4" noValidate>
            {error && <Alert onDismiss={() => setError(null)}>{error}</Alert>}

            <div>
              <label className="label" htmlFor="name">
                Full name
              </label>
              <input id="name" required className="input" value={form.name} onChange={update('name')} />
            </div>

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
                onChange={update('email')}
              />
            </div>

            <div>
              <label className="label" htmlFor="phone">
                Phone number
              </label>
              <input
                id="phone"
                type="tel"
                autoComplete="tel"
                required
                className="input"
                placeholder="+919876543210"
                value={form.phone}
                onChange={update('phone')}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="password">
                  Password
                </label>
                <input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={8}
                  className="input"
                  value={form.password}
                  onChange={update('password')}
                />
              </div>
              <div>
                <label className="label" htmlFor="confirmPassword">
                  Confirm password
                </label>
                <input
                  id="confirmPassword"
                  type="password"
                  autoComplete="new-password"
                  required
                  className="input"
                  value={form.confirmPassword}
                  onChange={update('confirmPassword')}
                />
              </div>
            </div>

            <p className="text-xs text-slate-500">
              Passwords need at least 8 characters. New accounts always start as a customer.
            </p>

            <button type="submit" className="btn-primary w-full" disabled={submitting}>
              {submitting && <Spinner className="h-4 w-4" />}
              {submitting ? 'Creating account' : 'Create account'}
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
                Continue with Google
              </a>
            </>
          ) : null}

          <p className="mt-6 text-center text-sm text-slate-600">
            Already registered?{' '}
            <Link to="/login" className="font-semibold text-brand-600 hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}