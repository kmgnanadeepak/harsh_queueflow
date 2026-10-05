const TOKEN_STYLES = {
  WAITING: 'bg-amber-100 text-amber-800',
  CALLED: 'bg-brand-100 text-brand-800',
  SERVING: 'bg-emerald-100 text-emerald-800',
  COMPLETED: 'bg-slate-100 text-slate-600',
  SKIPPED: 'bg-rose-100 text-rose-700',
  CANCELLED: 'bg-slate-100 text-slate-500',
};

const PRIORITY_STYLES = {
  EMERGENCY: 'bg-rose-600 text-white',
  ELDERLY: 'bg-amber-500 text-white',
  DISABLED: 'bg-violet-500 text-white',
  NORMAL: 'bg-slate-200 text-slate-700',
};

const COUNTER_STYLES = {
  AVAILABLE: 'bg-emerald-100 text-emerald-700',
  BUSY: 'bg-brand-100 text-brand-700',
  OFFLINE: 'bg-slate-200 text-slate-500',
};

export function TokenStatusBadge({ status }) {
  if (!status) return null;
  return (
    <span className={`badge ${TOKEN_STYLES[status] ?? 'bg-slate-100 text-slate-600'}`}>
      {humanise(status)}
    </span>
  );
}

export function PriorityBadge({ priority }) {
  if (!priority) return null;
  return (
    <span className={`badge ${PRIORITY_STYLES[priority] ?? PRIORITY_STYLES.NORMAL}`}>
      {humanise(priority)}
    </span>
  );
}

export function CounterStatusBadge({ status }) {
  if (!status) return null;
  return (
    <span className={`badge ${COUNTER_STYLES[status] ?? 'bg-slate-100 text-slate-600'}`}>
      {humanise(status)}
    </span>
  );
}

export function humanise(value) {
  if (!value) return '';
  return String(value)
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}