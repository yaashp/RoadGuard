// Thin fetch wrapper: JSON in/out, CSRF header on every state-changing call, uniform errors.

export class ApiError extends Error {
  constructor(status, body, fallback) {
    super((body && body.message) || fallback || 'Request failed');
    this.status = status;
    this.code = (body && body.code) || '';
    this.fieldErrors = (body && body.fieldErrors) || {};
    this.details = (body && body.details) || null;
  }
}

const listeners = new Set();
/** Called when the server says the session is gone (401 on a request that needed login). */
export function onUnauthorized(fn) { listeners.add(fn); }

function cookie(name) {
  const m = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return m ? decodeURIComponent(m.split('=').slice(1).join('=')) : null;
}

async function ensureCsrf() {
  if (!cookie('XSRF-TOKEN')) await fetch('/api/health', { credentials: 'same-origin' });
}

function queryString(query) {
  const p = new URLSearchParams();
  Object.entries(query || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  });
  const s = p.toString();
  return s ? '?' + s : '';
}

async function request(method, path, { json, form, query, quiet401 } = {}) {
  const headers = { Accept: 'application/json' };
  if (method !== 'GET') {
    await ensureCsrf();
    headers['X-XSRF-TOKEN'] = cookie('XSRF-TOKEN') || '';
  }
  let body;
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(json);
  } else if (form) {
    body = form; // the browser sets the multipart boundary
  }
  let res;
  try {
    res = await fetch(path + queryString(query), { method, headers, body, credentials: 'same-origin' });
  } catch (e) {
    throw new ApiError(0, null, 'Cannot reach the server. Check your connection and try again.');
  }
  let data = null;
  if (res.status !== 204) {
    const text = await res.text();
    if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
  }
  if (!res.ok) {
    const err = new ApiError(res.status, data, 'Request failed (' + res.status + ')');
    if (res.status === 401 && !quiet401) listeners.forEach((fn) => fn(err));
    throw err;
  }
  return data;
}

export const api = {
  get: (path, query, opts) => request('GET', path, { query, ...opts }),
  post: (path, json, opts) => request('POST', path, { json: json === undefined ? {} : json, ...opts }),
  put: (path, json) => request('PUT', path, { json }),
  patch: (path, json) => request('PATCH', path, { json }),
  del: (path) => request('DELETE', path),
  upload: (path, form) => request('POST', path, { form }),
};
