import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { notificationApi } from '../api/endpoints';
import { useAuth } from '../context/AuthContext';
import { humanise } from './Badges';

function relativeTime(iso) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.round(diff / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

/**
 * Notification centre. The list is fetched over HTTP and kept fresh by the
 * /topic/notifications/{id} STOMP subscription mounted in AppLayout, which
 * triggers refetch on every push so read state stays truthful.
 */
export function NotificationBell({ refreshSignal }) {
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { user } = useAuth();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [unreadResponse, listResponse] = await Promise.all([
        notificationApi.unreadCount(),
        notificationApi.list(0, 10),
      ]);
      setUnread(unreadResponse.data.unreadCount ?? 0);
      setItems(listResponse.data.content ?? []);
    } catch {
      // The bell is non-critical: a failure must not break the page.
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load, refreshSignal]);

  const markAll = async () => {
    try {
      await notificationApi.markAllRead();
      await load();
    } catch {
      /* ignored */
    }
  };

  const openNotification = async (item) => {
    try {
      if (!item.read) {
        await notificationApi.markRead(item.id);
      }
      await load();
    } catch {
      /* ignored */
    }
    if (item.tokenId) {
      navigate('/dashboard');
    }
    setOpen(false);
  };

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className="relative rounded-full p-2 text-slate-600 hover:bg-slate-100"
        aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}
      >
        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M15 17h5l-1.4-1.4A2 2 0 0118 14.2V11a6 6 0 10-12 0v3.2c0 .5-.2 1-.6 1.4L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>
        {unread > 0 && (
          <span className="absolute right-0 top-0 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <>
          <button
            type="button"
            aria-hidden="true"
            tabIndex={-1}
            className="fixed inset-0 z-10 cursor-default"
            onClick={() => setOpen(false)}
          />
          <div className="absolute right-0 z-20 mt-2 w-80 animate-fade-in rounded-xl border border-slate-200 bg-white shadow-lg">
            <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
              <p className="text-sm font-semibold text-slate-800">
                Notifications{user ? ` for ${user.name}` : ''}
              </p>
              {unread > 0 && (
                <button type="button" onClick={markAll} className="text-xs font-semibold text-brand-600 hover:underline">
                  Mark all read
                </button>
              )}
            </div>

            <div className="max-h-80 overflow-y-auto">
              {loading && <p className="px-4 py-6 text-center text-sm text-slate-500">Loading…</p>}
              {!loading && items.length === 0 && (
                <p className="px-4 py-6 text-center text-sm text-slate-500">No notifications yet.</p>
              )}
              {items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => openNotification(item)}
                  className={`block w-full border-b border-slate-50 px-4 py-3 text-left last:border-0 hover:bg-slate-50 ${
                    item.read ? '' : 'bg-brand-50/60'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-semibold text-slate-500">{humanise(item.type)}</span>
                    <span className="text-[11px] text-slate-400">{relativeTime(item.createdAt)}</span>
                  </div>
                  <p className="mt-0.5 text-sm text-slate-700">{item.message}</p>
                </button>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}