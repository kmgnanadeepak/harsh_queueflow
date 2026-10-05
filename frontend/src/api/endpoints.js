import api from './client';

/** All API endpoints use /api prefix which is proxied to Spring Boot by Vite. */
const base = '/api';

export const authApi = {
  register: (payload) => api.post(`${base}/auth/register`, payload),
  login: (payload) => api.post(`${base}/auth/login`, payload),
  me: () => api.get(`${base}/auth/me`),
  logout: () => api.post(`${base}/auth/logout`),
  googleStatus: () => api.get(`${base}/auth/google/status`, { skipAuthRedirect: true }),
};

export const catalogApi = {
  organizations: () => api.get(`${base}/organizations/active`),
  services: (organizationId) =>
    api.get(`${base}/services/active`, {
      params: organizationId ? { organizationId } : undefined,
      skipAuthRedirect: true,
    }),
  service: (id) => api.get(`${base}/services/${id}`),
  countersForService: (id) => api.get(`${base}/services/${id}/counters`),
  queue: (serviceId) => api.get(`${base}/queue/${serviceId}`),
  queueStatus: (serviceId) => api.get(`${base}/queue/${serviceId}/status`),
};

export const tokenApi = {
  generate: (payload) => api.post(`${base}/tokens`, payload),
  mine: (page = 0, size = 20) => api.get(`${base}/tokens/my`, { params: { page, size } }),
  current: () => api.get(`${base}/tokens/my/current`),
  byId: (id) => api.get(`${base}/tokens/my/${id}`),
  cancel: (id) => api.post(`${base}/tokens/my/${id}/cancel`),
  history: (page = 0, size = 20) => api.get(`${base}/tokens/history`, { params: { page, size } }),
};

export const staffApi = {
  dashboard: (counterId) => api.get(`${base}/staff/dashboard`, { params: { counterId } }),
  counters: () => api.get(`${base}/staff/counters`),
  callNext: (payload) => api.post(`${base}/staff/tokens/call-next`, payload),
  start: (id) => api.post(`${base}/staff/tokens/${id}/start`),
  complete: (id) => api.post(`${base}/staff/tokens/${id}/complete`),
  skip: (id) => api.post(`${base}/staff/tokens/${id}/skip`),
  setCounterStatus: (id, status) =>
    api.post(`${base}/staff/counters/${id}/status`, null, { params: { status } }),
};

export const adminApi = {
  users: (params) => api.get(`${base}/admin/users`, { params }),
  createUser: (payload) => api.post(`${base}/admin/users`, payload),
  updateUser: (id, payload) => api.put(`${base}/admin/users/${id}`, payload),
  deactivateUser: (id) => api.delete(`${base}/admin/users/${id}`),

  organizations: (params) => api.get(`${base}/admin/organizations`, { params }),
  createOrganization: (payload) => api.post(`${base}/admin/organizations`, payload),
  updateOrganization: (id, payload) => api.put(`${base}/admin/organizations/${id}`, payload),
  deleteOrganization: (id) => api.delete(`${base}/admin/organizations/${id}`),

  services: (params) => api.get(`${base}/admin/services`, { params }),
  createService: (payload) => api.post(`${base}/admin/services`, payload),
  updateService: (id, payload) => api.put(`${base}/admin/services/${id}`, payload),
  deleteService: (id) => api.delete(`${base}/admin/services/${id}`),

  counters: (params) => api.get(`${base}/admin/counters`, { params }),
  createCounter: (payload) => api.post(`${base}/admin/counters`, payload),
  updateCounter: (id, payload) => api.put(`${base}/admin/counters/${id}`, payload),
  deleteCounter: (id) => api.delete(`${base}/admin/counters/${id}`),

  dashboard: () => api.get(`${base}/admin/dashboard`),
  analytics: () => api.get(`${base}/admin/analytics`),
  broadcast: (message) => api.post(`${base}/admin/broadcast`, null, { params: { message } }),
};

export const notificationApi = {
  list: (page = 0, size = 20) => api.get(`${base}/notifications`, { params: { page, size } }),
  unreadCount: () => api.get(`${base}/notifications/unread-count`),
  markRead: (id) => api.put(`${base}/notifications/${id}/read`),
  markAllRead: () => api.put(`${base}/notifications/read-all`),
};