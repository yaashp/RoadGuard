// Client-side router on real URL paths (History API), so pages can be indexed at /map, /complaints/12 and so on.
// A page function renders into the container and may return a cleanup function. Root-relative links are handled
// here, so navigation never reloads the app. Modified clicks (new tab, etc.) keep the browser's default.
import { state, isAdmin, consoleOnly, citizenMode, setCitizenMode } from './state.js';
import { clear, h, icon } from './ui.js';
import { applySeo, crumbsFor } from './seo.js';

// Pages an administrator in console-only mode may open. Everything else is a citizen page.
const CONSOLE_PATHS = ['/admin', '/settings', '/notifications', '/login', '/register'];

const routes = [];
let cleanup = null;
let token = 0;
let crumbEl = null;

export function route(pattern, page, access = null) {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/:[^/]+/g, (m) => { keys.push(m.slice(1)); return '([^/]+)'; }) + '$');
  routes.push({ re, keys, page, access });
}

/** Current path and query string, read from the URL. */
export function parseHash() {
  const path = location.pathname || '/';
  const query = {};
  new URLSearchParams(location.search).forEach((v, k) => { query[k] = v; });
  return { path, query };
}

export function navigate(path) {
  const target = path.startsWith('/') ? path : '/' + path;
  if (target === location.pathname + location.search) return;
  history.pushState(null, '', target);
  window.dispatchEvent(new Event('routechange'));
}

export function currentPath() { return parseHash().path; }

document.addEventListener('click', (e) => {
  if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
  const a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
  if (!a || a.target || a.hasAttribute('download')) return;
  const href = a.getAttribute('href');
  if (!href || !href.startsWith('/') || href.startsWith('//') || href.startsWith('/api/')) return;
  e.preventDefault();
  navigate(href);
});
window.addEventListener('popstate', () => window.dispatchEvent(new Event('routechange')));

function setCrumbs(container, path) {
  if (crumbEl) { crumbEl.remove(); crumbEl = null; }
  if (!path || !container.parentNode) return;
  const items = crumbsFor(path);
  if (items.length < 2) return;
  crumbEl = h('nav', { class: 'crumbs', 'aria-label': 'Breadcrumb' },
    h('ol', null, items.map((c, i) => h('li', null,
      i === items.length - 1 ? h('span', { 'aria-current': 'page' }, c.name) : h('a', { href: c.path }, c.name)))));
  container.parentNode.insertBefore(crumbEl, container);
}

export async function render(container) {
  const my = ++token;
  if (cleanup) { try { cleanup(); } catch (e) { console.error(e); } cleanup = null; }
  const { path, query } = parseHash();
  clear(container);
  setCrumbs(container, null);
  const match = routes.find((r) => r.re.test(path));
  if (!match) { applySeo(path); container.appendChild(notFound()); return; }
  if (match.access && !state.me) { sessionStorage.setItem('rg_after_login', path + location.search); navigate('/login'); return; }
  if (match.access === 'admin' && state.me.role !== 'admin') { applySeo(path); container.appendChild(forbidden()); return; }
  if (isAdmin() && path === '/admin' && citizenMode()) setCitizenMode(false);   // opening the console leaves citizen mode
  if (consoleOnly() && !CONSOLE_PATHS.includes(path)) { navigate('/admin'); return; }
  const params = {};
  const m = path.match(match.re);
  match.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); });
  window.scrollTo(0, 0);
  applySeo(path);
  setCrumbs(container, path);
  try {
    const result = await match.page({ container, params, query, isCurrent: () => my === token });
    if (my !== token) { if (typeof result === 'function') result(); return; }
    cleanup = typeof result === 'function' ? result : null;
  } catch (e) {
    console.error(e);
    if (my === token) container.appendChild(h('div', { class: 'empty error' }, icon('alert', 28), h('h4', null, 'This page failed to load'), h('p', null, e.message || 'Unexpected error')));
  }
}

const notFound = () => h('div', { class: 'empty' }, icon('map', 28),
  h('h1', null, 'Page not found'),
  h('p', { class: 'muted' }, 'The page you are looking for does not exist or has moved.'),
  h('div', { class: 'btn-row' },
    h('a', { class: 'btn primary', href: '/' }, 'Go to the home page'),
    h('a', { class: 'btn', href: '/map' }, 'Open the hazard map'),
    h('a', { class: 'btn', href: '/report' }, 'Report a hazard')));
const forbidden = () => h('div', { class: 'empty error' }, icon('shield', 28), h('h1', null, 'Administrators only'), h('p', null, 'Your account does not have access to this page.'), h('a', { class: 'btn', href: '/' }, 'Back to the home page'));
