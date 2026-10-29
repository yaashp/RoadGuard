// Application state: who is logged in, server reference data, presentation preferences.
import { api, ApiError } from './api.js';

export const state = { me: null, meta: null, prefs: null, unread: 0 };
const subs = new Set();
export const subscribe = (fn) => { subs.add(fn); return () => subs.delete(fn); };
export const emit = () => subs.forEach((fn) => fn(state));

export async function loadMeta() { state.meta = await api.get('/api/meta'); return state.meta; }

export async function loadMe() {
  try {
    state.me = await api.get('/api/auth/me', null, { quiet401: true });
    state.prefs = await api.get('/api/users/me/preferences');
    applyTheme(state.prefs.theme);
  } catch (e) {
    if (!(e instanceof ApiError) || e.status !== 401) throw e;
    state.me = null; state.prefs = null;
  }
  emit();
  return state.me;
}

export function setSession(user) { state.me = user; emit(); }

export function clearSession() { state.me = null; state.prefs = null; state.unread = 0; try { sessionStorage.removeItem('rg_citizen_mode'); } catch (e) { /* ignore */ } emit(); }

export async function refreshUnread() {
  if (!state.me) { state.unread = 0; emit(); return; }
  try { state.unread = (await api.get('/api/notifications/unread-count')).unread; } catch (e) { /* ignore: badge is cosmetic */ }
  emit();
}

// ---- theme (non-sensitive presentation preference; may live in localStorage) ----
export function applyTheme(theme) {
  const t = theme === 'dark' ? 'dark' : 'light';   // light is the RoadGuard look; an older saved value ("system") counts as light
  document.documentElement.setAttribute('data-theme', t);
  try { localStorage.setItem('rg_theme', t); } catch (e) { /* storage may be unavailable */ }
}
export function storedTheme() { try { return localStorage.getItem('rg_theme') || 'light'; } catch (e) { return 'light'; } }

// ---- "include sample data" switch used by dashboard, map, history, planner ----
export function includeSample() {
  if (state.prefs) return state.prefs.showSampleData;
  try { return localStorage.getItem('rg_sample') !== 'false'; } catch (e) { return true; }
}
export async function setIncludeSample(value) {
  try { localStorage.setItem('rg_sample', String(value)); } catch (e) { /* ignore */ }
  if (state.prefs) { state.prefs = await api.put('/api/users/me/preferences', { showSampleData: value }); }
}

export const isAdmin = () => !!state.me && state.me.role === 'admin';

// ---- admin mode ----
// Administrators start in the console only. "Switch to citizen site" turns citizen mode on for this browser session.
export function citizenMode() { try { return sessionStorage.getItem('rg_citizen_mode') === '1'; } catch (e) { return false; } }
export function setCitizenMode(on) {
  try { if (on) sessionStorage.setItem('rg_citizen_mode', '1'); else sessionStorage.removeItem('rg_citizen_mode'); } catch (e) { /* ignore */ }
  emit();
}
/** True when an administrator is in console-only mode: citizen pages are not available. */
export const consoleOnly = () => isAdmin() && !citizenMode();
