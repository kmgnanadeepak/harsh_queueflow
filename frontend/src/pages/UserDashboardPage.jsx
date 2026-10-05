import { useCallback, useEffect, useMemo, useState } from 'react';

import { catalogApi, tokenApi } from '../api/endpoints';
import { PriorityBadge, TokenStatusBadge } from '../components/Badges';
import { Alert, EmptyState, PageLoader, Spinner, StatTile } from '../components/Feedback';
import { useAuth } from '../context/AuthContext';
import { useRealtime } from '../hooks/useRealtime';
import { describe } from './LoginPage';

/**
 * /api/tokens/my/current answers 204 with an empty body when the user has no
 * active token, which Axios surfaces as '' rather than null.
 */
function orNull(data) {
  return data === undefined || data === null || data === '' ? null : data;
}

/**
 * Customer dashboard.
 *
 * "Take a token" is a real POST /api/tokens call - the server owns the token
 * number, the position and the estimated wait, and the page then follows that
 * single token over /topic/token/{id} so the counter call appears instantly.
 */
export function UserDashboardPage() {
  const { user } = useAuth();

  const [services, setServices] = useState([]);
  const [current, setCurrent] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState(null);
  const [status, setStatus] = useState(null);
  const [busy, setBusy] = useState(false);
  const [selectedService, setSelectedService] = useState('');
  const [priority, setPriority] = useState('NORMAL');

  const loadCurrent = useCallback(async () => {
    try {
      const { data } = await tokenApi.current();
      setCurrent(orNull(data));
    } catch {
      setCurrent(null);
    }
  }, []);

  const loadHistory = useCallback(async () => {
    try {
      const { data } = await tokenApi.history(0, 5);
      setHistory(data.content ?? []);
    } catch {
      setHistory([]);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all([catalogApi.services(), tokenApi.current(), tokenApi.history(0, 5)])
      .then(([servicesResponse, currentResponse, historyResponse]) => {
        if (cancelled) return;
        const list = servicesResponse.data ?? [];
        setServices(list);
        setSelectedService((value) => value || (list[0] ? String(list[0].id) : ''));
        setCurrent(orNull(currentResponse.data));
        setHistory(historyResponse.data?.content ?? []);
      })
      .catch((error) => {
        if (!cancelled) setActionError(describe(error));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // Queue status poll for the selected service: live counts for everyone in the
  // queue, without exposing anybody else's identity.
  useEffect(() => {
    if (!selectedService) return undefined;
    let active = true;

    const load = async () => {
      try {
        const { data } = await catalogApi.queueStatus(selectedService);
        if (active) setStatus(data);
      } catch {
        /* the banner is supplementary */
      }
    };

    load();
    const timer = setInterval(load, 15000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [selectedService]);

  const subscriptions = useMemo(() => {
    const destinations = [];
    if (current?.id) destinations.push(`/topic/token/${current.id}`);
    if (current?.serviceId) destinations.push(`/topic/queue/${current.serviceId}`);
    return destinations;
  }, [current?.id, current?.serviceId]);

  useRealtime({
    enabled: Boolean(current?.id),
    subscriptions,
    onEvent: () => {
      loadCurrent();
      loadHistory();
    },
  });

  const takeToken = async () => {
    if (!selectedService) return;
    setBusy(true);
    setActionError(null);
    try {
      const { data } = await tokenApi.generate({
        serviceId: Number(selectedService),
        priority,
      });
      setCurrent(data);
      await loadHistory();
    } catch (error) {
      setActionError(describe(error));
    } finally {
      setBusy(false);
    }
  };

  const cancelToken = async () => {
    if (!current?.id) return;
    setBusy(true);
    setActionError(null);
    try {
      await tokenApi.cancel(current.id);
      await loadCurrent();
      await loadHistory();
    } catch (error) {
      setActionError(describe(error));
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return <PageLoader label="Loading your queue" />;
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Hello, {user?.name}</h1>
        <p className="text-sm text-slate-500">Take a token and watch your place in line.</p>
      </header>

      {actionError && <Alert onDismiss={() => setActionError(null)}>{actionError}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Your token" value={current?.tokenNumber ?? '—'} />
        <StatTile label="Position" value={current?.position ?? '—'} tone="brand" />
        <StatTile
          label="Estimated wait"
          value={current ? `${current.estimatedWaitingMinutes} min` : '—'}
          tone="warning"
        />
        <StatTile label="People ahead" value={current?.peopleAhead ?? 0} />
      </div>

      {current ? (
        <section className="card p-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-sm text-slate-500">{current.serviceName}</p>
              <p className="mt-1 text-4xl font-bold tracking-tight text-slate-900">{current.tokenNumber}</p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <TokenStatusBadge status={current.status} />
                <PriorityBadge priority={current.priority} />
                {current.counterNumber && (
                  <span className="badge bg-slate-100 text-slate-700">Counter {current.counterNumber}</span>
                )}
              </div>
            </div>

            <div className="text-right">
              <p className="text-xs uppercase tracking-wide text-slate-500">
                {current.status === 'WAITING'
                  ? 'You are in the waiting line'
                  : current.status === 'CALLED'
                    ? 'Please go to your counter'
                    : current.status === 'SERVING'
                      ? 'You are being served'
                      : humanSentence(current.status)}
              </p>
              {current.status === 'CALLED' && current.counterNumber && (
                <p className="mt-1 text-3xl font-bold text-brand-700">{current.counterNumber}</p>
              )}
            </div>
          </div>

          {current.status === 'WAITING' && (
            <div className="mt-6 flex justify-end">
              <button type="button" className="btn-secondary" onClick={cancelToken} disabled={busy}>
                {busy && <Spinner className="h-4 w-4" />} Cancel my token
              </button>
            </div>
          )}
        </section>
      ) : (
        <section className="card p-6">
          <h2 className="text-lg font-semibold text-slate-900">Take a token</h2>
          <p className="mt-1 text-sm text-slate-500">
            Pick a service and a priority. The server allocates your number and place in line.
          </p>

          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="service">
                Service
              </label>
              <select
                id="service"
                className="input"
                value={selectedService}
                onChange={(event) => setSelectedService(event.target.value)}
              >
                {services.length === 0 && <option value="">No active services</option>}
                {services.map((service) => (
                  <option key={service.id} value={service.id}>
                    {service.name} · {service.organizationName} · ~{service.averageServiceTime} min
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="label" htmlFor="priority">
                Priority
              </label>
              <select
                id="priority"
                className="input"
                value={priority}
                onChange={(event) => setPriority(event.target.value)}
              >
                <option value="NORMAL">Normal</option>
                <option value="ELDERLY">Elderly</option>
                <option value="DISABLED">Disabled</option>
                <option value="EMERGENCY">Emergency</option>
              </select>
            </div>
          </div>

          <p className="mt-2 text-xs text-slate-500">
            Priority is recorded with your token and is visible to the counter staff. Please choose honestly.
          </p>

          <div className="mt-4 flex justify-end">
            <button
              type="button"
              className="btn-primary"
              onClick={takeToken}
              disabled={busy || !selectedService || services.length === 0}
            >
              {busy && <Spinner className="h-4 w-4" />} Take a token
            </button>
          </div>
        </section>
      )}

      {status && (
        <section className="card p-6">
          <h2 className="text-lg font-semibold text-slate-900">Live queue</h2>
          <div className="mt-4 grid gap-4 sm:grid-cols-4">
            <StatTile label="Waiting" value={status.waitingCount} />
            <StatTile label="Called" value={status.calledCount} tone="brand" />
            <StatTile label="Being served" value={status.servingCount} tone="success" />
            <StatTile label="Counters open" value={status.availableCounters} />
          </div>
        </section>
      )}

      <section className="card p-6">
        <h2 className="text-lg font-semibold text-slate-900">Recent tokens</h2>
        {history.length === 0 ? (
          <div className="mt-4">
            <EmptyState title="No past tokens yet" description="Tokens you complete, skip or cancel appear here." />
          </div>
        ) : (
          <ul className="mt-4 divide-y divide-slate-100">
            {history.map((token) => (
              <li key={token.id} className="flex items-center justify-between gap-4 py-3">
                <div>
                  <p className="font-medium text-slate-800">{token.tokenNumber}</p>
                  <p className="text-xs text-slate-500">{token.serviceName}</p>
                </div>
                <TokenStatusBadge status={token.status} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function humanSentence(status) {
  switch (status) {
    case 'COMPLETED':
      return 'This visit is complete';
    case 'SKIPPED':
      return 'This token was skipped';
    case 'CANCELLED':
      return 'This token was cancelled';
    default:
      return 'No active token';
  }
}