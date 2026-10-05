import axios from 'axios';

const TOKEN_KEY = 'queueflow.token';

/**
 * Single axios instance for the whole app.
 *
 * The base URL is empty in development so requests go through the Vite proxy on
 * the same origin as the app, which keeps CORS and cookies identical to
 * production. Set VITE_API_BASE_URL when the API lives on another host.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  withCredentials: true,
  timeout: 20000,
});

export function readToken() {
  return window.localStorage.getItem(TOKEN_KEY);
}

export function writeToken(token) {
  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token);
  } else {
    window.localStorage.removeItem(TOKEN_KEY);
  }
}

api.interceptors.request.use((config) => {
  const token = readToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Normalises every backend failure into a predictable shape.
 *
 * The backend always answers with the ApiError envelope
 * {timestamp, status, error, message, path, violations}, so the UI can always
 * show `message` and, for validation failures, the per-field violations.
 */
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isCancel(error)) {
      return Promise.reject(error);
    }

    const status = error.response?.status;
    const payload = error.response?.data;

    const normalised = {
      status: status ?? 0,
      message:
        payload?.message ??
        error.response?.statusText ??
        (error.code === 'ECONNABORTED'
          ? 'The server took too long to respond. Please try again.'
          : 'Unable to reach the server. Please check your connection.'),
      violations: Array.isArray(payload?.violations) ? payload.violations : [],
      path: payload?.path ?? null,
    };

    // 401 on an authenticated request means the token is gone or expired:
    // drop it and bounce to login exactly once.
    if (status === 401 && !error.config?.skipAuthRedirect) {
      const hadToken = Boolean(readToken());
      writeToken(null);
      if (hadToken && window.location.pathname !== '/login') {
        window.sessionStorage.setItem(
          'queueflow.sessionExpired',
          'Your session has expired. Please sign in again.',
        );
        window.location.assign('/login');
      }
    }

    return Promise.reject(Object.assign(new Error(normalised.message), normalised));
  },
);

export default api;