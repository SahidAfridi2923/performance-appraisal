// Small fetch wrapper: adds the JWT, parses JSON and turns error bodies into Error(message).
const TOKEN_KEY = 'appraisal.token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

let onUnauthorized = () => {};
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    if (res.status === 401 && path !== '/auth/login') onUnauthorized();
    throw new Error((data && data.message) || `Request failed (${res.status})`);
  }
  return data;
}

export const api = {
  get: (p) => request('GET', p),
  post: (p, b) => request('POST', p, b ?? {}),
  put: (p, b) => request('PUT', p, b ?? {}),
  del: (p) => request('DELETE', p),
};
