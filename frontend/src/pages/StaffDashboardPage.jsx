import { useCallback, useEffect, useMemo, useState } from 'react';

import { staffApi } from '../api/endpoints';
import {
  CounterStatusBadge,
  PriorityBadge,
  TokenStatusBadge,
} from '../components/Badges';
import { Alert, EmptyState, PageLoader, Spinner, StatTile } from '../components/Feedback';
import { useRealtime } from '../hooks/useRealtime';
import { describe } from './LoginPage';

/**
 * Counter dashboard for STAFF (and ADMIN).
 *
 * One call - /api/staff/dashboard - returns the counter, its waiting line, the
 * token currently being served and today's counters. Every button maps to a real
 * state transition that the server validates; the page then follows the queue
 * topic so a colleague calling another counter shows up immediately.
 */
export function StaffDashboardPage() {
  const [counterId, setCounterId] = useState('');
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(
    async (requestedCounterId) => {
      try {
        const { data } = await staffApi.dashboard(requestedCounterId || undefined);
        setDashboard(data);
        setCounterId((value) => value || (data.counter?.id ? String(data.counter.id) : ''));
      } catch (requestError) {
        setError(describe(requestError));
      }
    },
    [],
  );

  useEffect(() => {
    load('')
      .then(() => setLoading(false))
      .catch(() => setLoading(false));
  }, [load]);

  const serviceId = dashboard?.counter?.serviceId;
  const subscriptions = useMemo(
    () => (serviceId ? [`/topic/queue/${serviceId}`] : []),
    [serviceId],
  );

  useRealtime({
    enabled: Boolean(serviceId),
    subscriptions,
    onEvent: () => load(counterId),
  });

  const run = async (action, successMessage) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(successMessage);
      await load(counterId);
    } catch (requestError) {
      setError(describe(requestError));
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return <PageLoader label="Loading your counter" />;
  }

  if (!dashboard?.counter) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-semibold text-slate-900">Counter dashboard</h1>
        <Alert>{error ?? 'No counter is assigned to your account yet.'}</Alert>
      </div>
    );
  }

  const { counter, stats, waitingQueue = [], currentToken, availableCounters = [], recentCompleted = [] } =
    dashboard;

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Counter dashboard</h1>
          <p className="text-sm text-slate-500">
            {counter.organizationName} · {counter.serviceName} · avg {counter.averageServiceTime} min
          </p>
        </div>

        <div className="flex items-center gap-3">
          {availableCounters.length > 0 && (
            <select
              className="input w-auto"
              value={counterId}
              onChange={(event) => {
                setCounterId(event.target.value);
                load(event.target.value);
              }}
              aria-label="Select counter"
            >
              <option value={counter.id}>{counter.counterNumber}</option>
              {availableCounters
                .filter((item) => item.id !== counter.id)
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.counterNumber}
                  </option>
                ))}
            </select>
          )}
          <CounterStatusBadge status={counter.status} />
        </div>
      </header>

      {error && <Alert onDismiss={() => setError(null)}>{error}</Alert>}
      {notice && (
        <Alert variant="success" onDismiss={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <StatTile label="Waiting" value={stats.waiting} tone="warning" />
        <StatTile label="Serving" value={stats.serving} tone="success" />
        <StatTile label="Completed today" value={stats.completedToday} />
        <StatTile label="Skipped today" value={stats.skippedToday} tone="danger" />
        <StatTile label="Emergency waiting" value={stats.emergencyWaiting} tone="danger" />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="card p-6 lg:col-span-1">
          <h2 className="text-lg font-semibold text-slate-900">Now serving</h2>
          {currentToken ? (
            <div className="mt-4 rounded-xl border border-brand-200 bg-brand-50 p-4">
              <p className="text-3xl font-bold text-brand-800">{currentToken.tokenNumber}</p>
              <p className="mt-1 text-sm text-slate-600">{currentToken.ownerName}</p>
              <div className="mt-2 flex items-center gap-2">
                <PriorityBadge priority={currentToken.priority} />
                <TokenStatusBadge status={currentToken.status} />
              </div>
              <p className="mt-2 text-xs text-slate-500">
                Waiting {currentToken.waitingMinutes} min · called {formatTime(currentToken.calledAt)}
              </p>

              <div className="mt-4 flex flex-wrap gap-2">
                {currentToken.status === 'CALLED' && (
                  <button
                    type="button"
                    className="btn-primary"
                    disabled={busy}
                    onClick={() => run(() => staffApi.start(currentToken.id), 'Marked as serving.')}
                  >
                    Start serving
                  </button>
                )}
                {currentToken.status !== 'COMPLETED' && (
                  <button
                    type="button"
                    className="btn-primary"
                    disabled={busy}
                    onClick={() => run(() => staffApi.complete(currentToken.id), 'Marked as completed.')}
                  >
                    Complete
                  </button>
                )}
                <button
                  type="button"
                  className="btn-danger"
                  disabled={busy}
                  onClick={() => run(() => staffApi.skip(currentToken.id), 'Token skipped.')}
                >
                  Skip
                </button>
              </div>
            </div>
          ) : (
            <div className="mt-4">
              <EmptyState
                title="No token at this counter"
                description="Call the next person in the queue when you are ready."
              />
            </div>
          )}

          <button
            type="button"
            className="btn-primary mt-4 w-full"
            disabled={busy || !currentToken}
            onClick={() =>
              run(
                () => staffApi.callNext({ counterId: counter.id, confirmCounter: true }),
                'Next token called.',
              )
            }
          >
            {busy && <Spinner className="h-4 w-4" />} Call next token
          </button>
        </section>

        <section className="card p-6 lg:col-span-2">
          <h2 className="text-lg font-semibold text-slate-900">Waiting queue</h2>
          <p className="mt-1 text-sm text-slate-500">
            Priority first, with ageing so nobody waits indefinitely.
          </p>

          {waitingQueue.length === 0 ? (
            <div className="mt-4">
              <EmptyState title="The queue is empty" description="No one is waiting for this service right now." />
            </div>
          ) : (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="py-2">Token</th>
                    <th className="py-2">Customer</th>
                    <th className="py-2">Priority</th>
                    <th className="py-2">Waiting</th>
                    <th className="py-2 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {waitingQueue.map((row, index) => (
                    <tr key={row.id}>
                      <td className="py-3 font-medium text-slate-800">
                        {row.tokenNumber}
                        {index === 0 && (
                          <span className="ml-2 badge bg-brand-100 text-brand-700">next</span>
                        )}
                      </td>
                      <td className="py-3 text-slate-600">{row.ownerName}</td>
                      <td className="py-3">
                        <PriorityBadge priority={row.priority} />
                      </td>
                      <td className="py-3 tabular-nums text-slate-600">{row.waitingMinutes} min</td>
                      <td className="py-3 text-right">
                        <div className="flex justify-end gap-2">
                          <button
                            type="button"
                            className="btn-ghost px-2 py-1 text-xs"
                            disabled={busy}
                            onClick={() =>
                              run(() => staffApi.callNext({ counterId: counter.id, confirmCounter: true }),
                                `Called ${row.tokenNumber}.`)
                            }
                          >
                            Call
                          </button>
                          <button
                            type="button"
                            className="btn-ghost px-2 py-1 text-xs"
                            disabled={busy}
                            onClick={() => run(() => staffApi.skip(row.id), `Skipped ${row.tokenNumber}.`)}
                          >
                            Skip
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>

      <section className="card p-6">
        <h2 className="text-lg font-semibold text-slate-900">Completed today</h2>
        {recentCompleted.length === 0 ? (
          <div className="mt-4">
            <EmptyState title="Nothing completed yet today" />
          </div>
        ) : (
          <ul className="mt-4 divide-y divide-slate-100">
            {recentCompleted.map((row) => (
              <li key={row.id} className="flex items-center justify-between gap-4 py-3 text-sm">
                <div>
                  <p className="font-medium text-slate-800">{row.tokenNumber}</p>
                  <p className="text-xs text-slate-500">{row.ownerName}</p>
                </div>
                <span className="text-xs text-slate-500">{formatTime(row.completedAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function formatTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}