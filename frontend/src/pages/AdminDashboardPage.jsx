import { useCallback, useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { adminApi } from '../api/endpoints';
import { CounterStatusBadge, humanise } from '../components/Badges';
import { Alert, PageLoader, Spinner, StatTile } from '../components/Feedback';
import { describe } from './LoginPage';

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'users', label: 'Users' },
  { key: 'organizations', label: 'Organizations' },
  { key: 'services', label: 'Services' },
  { key: 'counters', label: 'Counters' },
];

const STATUS_COLOURS = {
  WAITING: '#f59e0b',
  CALLED: '#3b82f6',
  SERVING: '#10b981',
  COMPLETED: '#64748b',
  SKIPPED: '#f43f5e',
  CANCELLED: '#94a3b8',
};

/**
 * Administration console.
 *
 * Everything here is a real admin endpoint: /api/admin/** is restricted to the
 * ADMIN role on the server, and it is the only place where STAFF and ADMIN
 * accounts can be created, because public registration always produces a USER.
 */
export function AdminDashboardPage() {
  const [tab, setTab] = useState('overview');

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Administration</h1>
        <p className="text-sm text-slate-500">Manage organizations, services, counters and staff accounts.</p>
      </header>

      <nav className="flex flex-wrap gap-1 border-b border-slate-200">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors ${
              tab === item.key
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {tab === 'overview' && <OverviewTab />}
      {tab === 'users' && <UsersTab />}
      {tab === 'organizations' && <OrganizationsTab />}
      {tab === 'services' && <ServicesTab />}
      {tab === 'counters' && <CountersTab />}
    </div>
  );
}

function OverviewTab() {
  const [dashboard, setDashboard] = useState(null);
  const [analytics, setAnalytics] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([adminApi.dashboard(), adminApi.analytics()])
      .then(([dashboardResponse, analyticsResponse]) => {
        setDashboard(dashboardResponse.data);
        setAnalytics(analyticsResponse.data);
      })
      .catch((requestError) => setError(describe(requestError)))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <PageLoader label="Loading dashboard" />;
  if (error) return <Alert>{error}</Alert>;

  const stats = dashboard?.stats;

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Tokens today" value={stats?.tokensToday ?? 0} tone="brand" />
        <StatTile label="Waiting now" value={stats?.waitingTokens ?? 0} tone="warning" />
        <StatTile label="Completed today" value={stats?.completedToday ?? 0} tone="success" />
        <StatTile label="Total users" value={stats?.totalUsers ?? 0} />
        <StatTile label="Organizations" value={stats?.totalOrganizations ?? 0} />
        <StatTile label="Active services" value={stats?.activeServices ?? 0} />
        <StatTile label="Active counters" value={stats?.activeCounters ?? 0} />
        <StatTile label="Tokens (all time)" value={analytics?.overview?.totalTokens ?? 0} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="card p-6">
          <h2 className="text-lg font-semibold text-slate-900">Tokens generated vs completed</h2>
          <div className="mt-4 h-72">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={analytics?.queueActivity ?? []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="generated" stroke="#3366f2" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="completed" stroke="#10b981" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className="card p-6">
          <h2 className="text-lg font-semibold text-slate-900">Token status breakdown</h2>
          <div className="mt-4 h-72">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={(analytics?.statusBreakdown ?? []).map((item) => ({
                    name: humanise(item.status),
                    value: item.count,
                  }))}
                  dataKey="value"
                  nameKey="name"
                  innerRadius="55%"
                  outerRadius="80%"
                  paddingAngle={2}
                >
                  {(analytics?.statusBreakdown ?? []).map((item) => (
                    <Cell key={item.status} fill={STATUS_COLOURS[item.status] ?? '#94a3b8'} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className="card p-6">
          <h2 className="text-lg font-semibold text-slate-900">Tokens per service</h2>
          <div className="mt-4 h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={analytics?.tokensPerService ?? []} layout="vertical" margin={{ left: 24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} />
                <YAxis type="category" dataKey="serviceName" width={140} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="tokensGenerated" fill="#3366f2" radius={[0, 4, 4, 0]} />
                <Bar dataKey="tokensCompleted" fill="#10b981" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className="card p-6">
          <h2 className="text-lg font-semibold text-slate-900">Busiest hours</h2>
          <div className="mt-4 h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={analytics?.peakPeriods ?? []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="hour" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="tokens" fill="#8b5cf6" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>

      <section className="card p-6">
        <h2 className="text-lg font-semibold text-slate-900">Live queues</h2>
        {(dashboard?.services ?? []).length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">No services configured yet.</p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="py-2">Service</th>
                  <th className="py-2">Organization</th>
                  <th className="py-2">Waiting</th>
                  <th className="py-2">Serving</th>
                  <th className="py-2">Counters</th>
                  <th className="py-2">Avg</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {dashboard.services.map((service) => (
                  <tr key={service.id}>
                    <td className="py-2 font-medium text-slate-800">{service.name}</td>
                    <td className="py-2 text-slate-600">{service.organizationName}</td>
                    <td className="py-2 tabular-nums">{service.waiting}</td>
                    <td className="py-2 tabular-nums">{service.serving}</td>
                    <td className="py-2 tabular-nums">{service.activeCounters}</td>
                    <td className="py-2 tabular-nums">{service.averageServiceTime} min</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function UsersTab() {
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [role, setRole] = useState('');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [saving, setSaving] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '', role: 'USER' });

  const load = useCallback(async () => {
    try {
      const response = await adminApi.users({
        page,
        size: 15,
        q: query || undefined,
        role: role || undefined,
      });
      setData(response.data);
    } catch (requestError) {
      setError(describe(requestError));
    }
  }, [page, query, role]);

  useEffect(() => {
    load();
  }, [load]);

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await adminApi.createUser(form);
      setNotice(`Created ${form.role} account for ${form.email}.`);
      setShowForm(false);
      setForm({ name: '', email: '', phone: '', password: '', role: 'USER' });
      await load();
    } catch (requestError) {
      setError(describe(requestError));
    } finally {
      setSaving(false);
    }
  };

  const deactivate = async (user) => {
    if (!window.confirm(`Deactivate ${user.email}? They will no longer be able to sign in.`)) return;
    setError(null);
    try {
      await adminApi.deactivateUser(user.id);
      setNotice(`${user.email} deactivated.`);
      await load();
    } catch (requestError) {
      setError(describe(requestError));
    }
  };

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-3">
          <input
            className="input w-64"
            placeholder="Search name or email"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
          />
          <select
            className="input w-40"
            value={role}
            onChange={(event) => {
              setRole(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All roles</option>
            <option value="USER">Customers</option>
            <option value="STAFF">Staff</option>
            <option value="ADMIN">Admins</option>
          </select>
        </div>
        <button type="button" className="btn-primary" onClick={() => setShowForm((value) => !value)}>
          {showForm ? 'Cancel' : 'New account'}
        </button>
      </div>

      {error && <Alert onDismiss={() => setError(null)}>{error}</Alert>}
      {notice && (
        <Alert variant="success" onDismiss={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      {showForm && (
        <form onSubmit={submit} className="card grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
          <p className="text-xs text-slate-500 sm:col-span-2 lg:col-span-3">
            Only administrators can create staff or admin accounts - public sign-up always creates a customer.
          </p>
          <input
            className="input"
            placeholder="Full name"
            required
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <input
            className="input"
            type="email"
            placeholder="email@example.com"
            required
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
          />
          <input
            className="input"
            placeholder="Phone (optional)"
            value={form.phone}
            onChange={(event) => setForm({ ...form, phone: event.target.value })}
          />
          <input
            className="input"
            type="password"
            placeholder="Temporary password"
            minLength={8}
            required
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
          />
          <select
            className="input"
            value={form.role}
            onChange={(event) => setForm({ ...form, role: event.target.value })}
          >
            <option value="USER">Customer</option>
            <option value="STAFF">Staff</option>
            <option value="ADMIN">Admin</option>
          </select>
          <div className="flex items-end">
            <button type="submit" className="btn-primary w-full" disabled={saving}>
              {saving && <Spinner className="h-4 w-4" />} Create account
            </button>
          </div>
        </form>
      )}

      <div className="card overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">Name</th>
              <th className="px-4 py-3">Email</th>
              <th className="px-4 py-3">Role</th>
              <th className="px-4 py-3">Provider</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {(data?.content ?? []).map((user) => (
              <tr key={user.id}>
                <td className="px-4 py-3 font-medium text-slate-800">{user.name}</td>
                <td className="px-4 py-3 text-slate-600">{user.email}</td>
                <td className="px-4 py-3">{humanise(user.role)}</td>
                <td className="px-4 py-3 text-slate-500">{humanise(user.provider)}</td>
                <td className="px-4 py-3">
                  <span
                    className={`badge ${
                      user.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'
                    }`}
                  >
                    {user.active ? 'Active' : 'Deactivated'}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  {user.active && (
                    <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => deactivate(user)}>
                      Deactivate
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {data?.content?.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                  No users match this filter.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Pagination page={data?.page ?? 0} totalPages={data?.totalPages ?? 0} onChange={setPage} />
    </section>
  );
}

function OrganizationsTab() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [form, setForm] = useState({ name: '', location: '', description: '' });
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setData((await adminApi.organizations({ page: 0, size: 50 })).data);
    } catch (requestError) {
      setError(describe(requestError));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await adminApi.createOrganization(form);
      setNotice(`Created ${form.name}.`);
      setForm({ name: '', location: '', description: '' });
      await load();
    } catch (requestError) {
      setError(describe(requestError));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (organization) => {
    if (!window.confirm(`Delete ${organization.name}?`)) return;
    try {
      await adminApi.deleteOrganization(organization.id);
      setNotice(`${organization.name} deleted.`);
      await load();
    } catch (requestError) {
      setError(describe(requestError));
    }
  };

  return (
    <section className="space-y-4">
      {error && <Alert onDismiss={() => setError(null)}>{error}</Alert>}
      {notice && (
        <Alert variant="success" onDismiss={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <form onSubmit={submit} className="card grid gap-3 p-4 sm:grid-cols-3">
        <input
          className="input"
          placeholder="Organization name"
          required
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
        />
        <input
          className="input"
          placeholder="Location"
          value={form.location}
          onChange={(event) => setForm({ ...form, location: event.target.value })}
        />
        <div className="flex gap-3">
          <input
            className="input"
            placeholder="Description (optional)"
            value={form.description}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
          />
          <button type="submit" className="btn-primary whitespace-nowrap" disabled={saving}>
            {saving ? 'Saving' : 'Add'}
          </button>
        </div>
      </form>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {(data?.content ?? []).map((organization) => (
          <article key={organization.id} className="card p-4">
            <h3 className="font-semibold text-slate-900">{organization.name}</h3>
            <p className="text-xs text-slate-500">{organization.location || 'No location set'}</p>
            {organization.description && (
              <p className="mt-2 text-sm text-slate-600">{organization.description}</p>
            )}
            <div className="mt-3 flex items-center justify-between">
              <span className={`badge ${organization.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                {organization.active ? 'Active' : 'Inactive'}
              </span>
              <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => remove(organization)}>
                Delete
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function ServicesTab() {
  const [data, setData] = useState(null);
  const [organizations, setOrganizations] = useState([]);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    organizationId: '',
    name: '',
    description: '',
    averageServiceTime: 10,
    active: true,
  });

  const load = useCallback(async () => {
    try {
      const [servicesResponse, organizationsResponse] = await Promise.all([
        adminApi.services({ page: 0, size: 50 }),
        adminApi.organizations({ page: 0, size: 50 }),
      ]);
      setData(servicesResponse.data);
      const list = organizationsResponse.data?.content ?? [];
      setOrganizations(list);
      setForm((current) => ({
        ...current,
        organizationId: current.organizationId || (list[0] ? String(list[0].id) : ''),
      }));
    } catch (requestError) {
      setError(describe(requestError));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await adminApi.createService({
        organizationId: Number(form.organizationId),
        name: form.name,
        description: form.description || null,
        averageServiceTime: Number(form.averageServiceTime),
        active: form.active,
      });
      setNotice(`Created service ${form.name}.`);
      setForm({ ...form, name: '', description: '' });
      await load();
    } catch (requestError) {
      setError(describe(requestError));
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (service) => {
    try {
      await adminApi.updateService(service.id, {
        organizationId: service.organizationId,
        name: service.name,
        description: service.description,
        averageServiceTime: service.averageServiceTime,
        active: !service.active,
      });
      setNotice(`${service.name} is now ${!service.active ? 'active' : 'inactive'}.`);
      await load();
    } catch (requestError) {
      setError(describe(requestError));
    }
  };

  return (
    <section className="space-y-4">
      {error && <Alert onDismiss={() => setError(null)}>{error}</Alert>}
      {notice && (
        <Alert variant="success" onDismiss={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <form onSubmit={submit} className="card grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-5">
        <select
          className="input"
          value={form.organizationId}
          onChange={(event) => setForm({ ...form, organizationId: event.target.value })}
          required
        >
          <option value="">Organization</option>
          {organizations.map((organization) => (
            <option key={organization.id} value={organization.id}>
              {organization.name}
            </option>
          ))}
        </select>
        <input
          className="input"
          placeholder="Service name"
          required
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
        />
        <input
          className="input"
          type="number"
          min={1}
          max={240}
          placeholder="Avg minutes"
          value={form.averageServiceTime}
          onChange={(event) => setForm({ ...form, averageServiceTime: event.target.value })}
        />
        <input
          className="input"
          placeholder="Description (optional)"
          value={form.description}
          onChange={(event) => setForm({ ...form, description: event.target.value })}
        />
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving ? 'Saving' : 'Add service'}
        </button>
      </form>

      <div className="card overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">Service</th>
              <th className="px-4 py-3">Organization</th>
              <th className="px-4 py-3">Avg minutes</th>
              <th className="px-4 py-3">Waiting</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {(data?.content ?? []).map((service) => (
              <tr key={service.id}>
                <td className="px-4 py-3 font-medium text-slate-800">{service.name}</td>
                <td className="px-4 py-3 text-slate-600">{service.organizationName}</td>
                <td className="px-4 py-3 tabular-nums">{service.averageServiceTime}</td>
                <td className="px-4 py-3 tabular-nums">{service.waitingCount}</td>
                <td className="px-4 py-3">
                  <span className={`badge ${service.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                    {service.active ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => toggle(service)}>
                    {service.active ? 'Deactivate' : 'Activate'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function CountersTab() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [form, setForm] = useState({ organizationId: '', serviceId: '', counterNumber: '', name: '', staffId: '' });
  const [options, setOptions] = useState({ organizations: [], services: [], staff: [] });
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setData((await adminApi.counters({ page: 0, size: 50 })).data);
      const [organizationsResponse, servicesResponse, staffResponse] = await Promise.all([
        adminApi.organizations({ page: 0, size: 50 }),
        adminApi.services({ page: 0, size: 100 }),
        adminApi.users({ page: 0, size: 100, role: 'STAFF' }),
      ]);
      const organizations = organizationsResponse.data?.content ?? [];
      setOptions({
        organizations,
        services: servicesResponse.data?.content ?? [],
        staff: staffResponse.data?.content ?? [],
      });
      setForm((current) => ({
        ...current,
        organizationId: current.organizationId || (organizations[0] ? String(organizations[0].id) : ''),
      }));
    } catch (requestError) {
      setError(describe(requestError));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await adminApi.createCounter({
        organizationId: Number(form.organizationId),
        serviceId: Number(form.serviceId),
        counterNumber: form.counterNumber,
        name: form.name || null,
        staffId: form.staffId ? Number(form.staffId) : null,
        status: 'AVAILABLE',
        active: true,
      });
      setNotice(`Created counter ${form.counterNumber}.`);
      setForm({ ...form, counterNumber: '', name: '' });
      await load();
    } catch (requestError) {
      setError(describe(requestError));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (counter) => {
    if (!window.confirm(`Delete counter ${counter.counterNumber}?`)) return;
    try {
      await adminApi.deleteCounter(counter.id);
      setNotice(`Counter ${counter.counterNumber} removed.`);
      await load();
    } catch (requestError) {
      setError(describe(requestError));
    }
  };

  return (
    <section className="space-y-4">
      {error && <Alert onDismiss={() => setError(null)}>{error}</Alert>}
      {notice && (
        <Alert variant="success" onDismiss={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <form onSubmit={submit} className="card grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-6">
        <select
          className="input"
          required
          value={form.organizationId}
          onChange={(event) => setForm({ ...form, organizationId: event.target.value, serviceId: '' })}
        >
          <option value="">Organization</option>
          {options.organizations.map((organization) => (
            <option key={organization.id} value={organization.id}>
              {organization.name}
            </option>
          ))}
        </select>
        <select
          className="input"
          required
          value={form.serviceId}
          onChange={(event) => setForm({ ...form, serviceId: event.target.value })}
        >
          <option value="">Service</option>
          {options.services
            .filter((service) => String(service.organizationId) === String(form.organizationId))
            .map((service) => (
              <option key={service.id} value={service.id}>
                {service.name}
              </option>
            ))}
        </select>
        <input
          className="input"
          placeholder="Counter number"
          required
          value={form.counterNumber}
          onChange={(event) => setForm({ ...form, counterNumber: event.target.value })}
        />
        <input
          className="input"
          placeholder="Label (optional)"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
        />
        <select
          className="input"
          value={form.staffId}
          onChange={(event) => setForm({ ...form, staffId: event.target.value })}
        >
          <option value="">Unassigned</option>
          {options.staff.map((user) => (
            <option key={user.id} value={user.id}>
              {user.name}
            </option>
          ))}
        </select>
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving ? 'Saving' : 'Add counter'}
        </button>
      </form>

      <div className="card overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">Counter</th>
              <th className="px-4 py-3">Service</th>
              <th className="px-4 py-3">Staff</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Serving</th>
              <th className="px-4 py-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {(data?.content ?? []).map((counter) => (
              <tr key={counter.id}>
                <td className="px-4 py-3 font-medium text-slate-800">{counter.counterNumber}</td>
                <td className="px-4 py-3 text-slate-600">{counter.serviceName}</td>
                <td className="px-4 py-3 text-slate-600">{counter.staffName ?? 'Unassigned'}</td>
                <td className="px-4 py-3">
                  <CounterStatusBadge status={counter.status} />
                </td>
                <td className="px-4 py-3 text-slate-600">{counter.currentTokenNumber ?? '—'}</td>
                <td className="px-4 py-3 text-right">
                  <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => remove(counter)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-end gap-2">
      <button type="button" className="btn-secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span className="text-sm text-slate-600">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="btn-secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Next
      </button>
    </div>
  );
}