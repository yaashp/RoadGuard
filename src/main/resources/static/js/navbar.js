// Navbar: brand + live status | search (Cmd/Ctrl+K) | report, alerts, account. Rendered with the same DOM helper as the rest of the app.
// Styles: css/navbar.css. Colours: css/theme.css.
import { h, icon, mount } from './ui.js';
import { state, isAdmin, consoleOnly, setCitizenMode } from './state.js';
import { navigate } from './router.js';
import { t } from './i18n/index.js';

// Centre links. "Reports" is the signed-in user's own reports; "Analytics" is the road history and trends.
const PRIMARY = [
  { path: '/', key: 'nav.home', match: (p) => p === '/' },
  { path: '/dashboard', key: 'nav.dashboard', match: (p) => p === '/dashboard' },
  { path: '/map', key: 'nav.hazards', match: (p) => p === '/map' },
  { path: '/complaints', key: 'nav.reports', match: (p) => p === '/complaints' || p.startsWith('/complaints/') || p === '/report' },
  { path: '/history', key: 'nav.analytics', match: (p) => p === '/history' },
];
// Everything else stays reachable from the "More" menu, so nothing was removed from the site.
const MORE = [
  { path: '/report', key: 'nav.reportCta', match: (p) => p === '/report' },
  { path: '/planner', key: 'nav.safeRoutes', match: (p) => p === '/planner' || p === '/saved' },
  { path: '/community', key: 'nav.community', match: (p) => p === '/community' || p.startsWith('/community/') },
  { path: '/repairs', key: 'nav.fixed', match: (p) => p === '/repairs' || p.startsWith('/repair/') },
  { path: '/impact', key: 'nav.impact', match: (p) => p === '/impact' },
  { path: '/admin', key: 'nav.admin', match: (p) => p === '/admin', adminOnly: true },
];

let openMenu = null;          // 'more' | 'user' | null
let redraw = () => {};
let drawerOpen = false;       // the slide-in navigation drawer (left)
let closeDrawer = () => {};

// The drawer lives in its own container on <body>. The topbar is sticky with a backdrop filter, which would trap a
// fixed-position child inside the 60px bar, so the drawer and its backdrop are mounted outside the topbar.
function drawerHost() {
  let el = document.getElementById('nav-drawer');
  if (!el) { el = document.createElement('div'); el.id = 'nav-drawer'; document.body.appendChild(el); }
  return el;
}
let listenersReady = false;
const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent || '');

function focusSoon(selector) { setTimeout(() => { const el = document.querySelector(selector); if (el) el.focus(); }, 0); }

function ensureListeners() {
  if (listenersReady) return;
  listenersReady = true;
  // Outside-click closes the open menu. The check uses the click's path, recorded when the click starts: the menu's own
  // click handler re-renders the navbar, which detaches the clicked element, so e.target.closest() would already be empty.
  document.addEventListener('click', (e) => {
    if (!openMenu) return;
    const path = e.composedPath ? e.composedPath() : [];
    const inside = path.some((n) => n && n.classList && (n.classList.contains('user-menu') || n.classList.contains('more')));
    if (!inside) { openMenu = null; redraw(); }
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && openMenu) {
      const was = openMenu; openMenu = null; redraw();
      focusSoon(was === 'user' ? '.avatar' : '.more-btn');
    } else if (e.key === 'Escape' && drawerOpen) {
      closeDrawer();
    }
    if ((e.metaKey || e.ctrlKey) && String(e.key).toLowerCase() === 'k') { e.preventDefault(); openPalette(); }
  });
}

function initials(name) {
  const parts = String(name || '?').trim().split(/\s+/);
  return ((parts[0] || '?')[0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

/** Arrow-key navigation inside a dropdown menu. */
function menuKeys(e) {
  const items = [...e.currentTarget.querySelectorAll('.menu-item')];
  const i = items.indexOf(document.activeElement);
  let n = null;
  if (e.key === 'ArrowDown') n = items[(i + 1) % items.length];
  else if (e.key === 'ArrowUp') n = items[(i - 1 + items.length) % items.length];
  else if (e.key === 'Home') n = items[0];
  else if (e.key === 'End') n = items[items.length - 1];
  if (n) { e.preventDefault(); n.focus(); }
}

/** Called by the app on every navigation, so an open menu never survives a page change. */
export function closeMenus() { openMenu = null; }

// Console-only centre (admins who have not switched to the citizen site).
function consoleCenter(ctx) {
  return h('nav', { class: 'nav-center console' + (ctx.menuOpen ? ' open' : ''), id: 'nav-center', 'aria-label': t('nav.adminConsole') },
    h('span', { class: 'console-badge' }, icon('gear', 14), t('nav.adminConsole')),
    h('button', { class: 'btn small', type: 'button', onclick: () => { ctx.closePanel(); setCitizenMode(true); navigate('/'); } }, t('nav.switchCitizen')));
}

export function renderNavbar(el, ctx) {
  ensureListeners();
  redraw = ctx.redraw;
  const path = ctx.path;
  const me = state.me;
  const dark = document.documentElement.getAttribute('data-theme') === 'dark';
  const consoleMode = consoleOnly();   // administrator in console-only mode: no citizen navigation
  const more = MORE.filter((m) => !m.adminOnly || isAdmin());
  const moreActive = more.some((m) => m.match(path) && !PRIMARY.some((p) => p.match(path)));

  const link = (item, cls) => h('a', { class: (cls || 'nav-link') + (item.match(path) ? ' active' : ''), href: item.path, 'aria-current': item.match(path) ? 'page' : null, role: cls ? 'menuitem' : null, onclick: () => ctx.closePanel() }, t(item.key));

  const moreBtn = h('button', { class: 'nav-link more-btn' + (moreActive ? ' active' : ''), type: 'button', 'aria-haspopup': 'menu', 'aria-expanded': String(openMenu === 'more'), 'aria-controls': 'more-menu',
    onclick: (e) => { e.stopPropagation(); openMenu = openMenu === 'more' ? null : 'more'; redraw(); focusSoon('.more-btn'); },
    onkeydown: (e) => { if (e.key === 'ArrowDown') { e.preventDefault(); openMenu = 'more'; redraw(); focusSoon('#more-menu .menu-item'); } } },
    t('nav.more'), h('span', { class: 'caret' }, icon('caret', 14)));
  const moreMenu = h('div', { class: 'dropdown more-menu', id: 'more-menu', role: 'menu', 'aria-label': t('nav.more'), hidden: openMenu !== 'more', onkeydown: menuKeys },
    h('div', { class: 'menu-heading panel-only' }, t('nav.more')), more.map((m) => link(m, 'menu-item')));

  drawerOpen = !!ctx.menuOpen;
  closeDrawer = () => { ctx.closePanel(); redraw(); focusSoon('.menu-btn'); };
  document.body.classList.toggle('nav-open', drawerOpen);
  const drawer = consoleMode ? consoleCenter(ctx) : h('nav', { class: 'nav-center' + (drawerOpen ? ' open' : ''), id: 'nav-center', 'aria-label': 'Main', 'aria-hidden': String(!drawerOpen) },
    h('div', { class: 'nav-drawer-head' }, h('span', { class: 'brand-mark' }, icon('shield', 18)), h('span', { class: 'brand-name' }, 'RoadGuard')),
    PRIMARY.map((p) => link(p)), h('div', { class: 'more' }, moreBtn, moreMenu),
    h('a', { class: 'cta panel-only', href: '/report', onclick: () => ctx.closePanel() }, icon('plus', 14), t('nav.reportCta')));
  const scrim = h('div', { class: 'nav-scrim' + (drawerOpen ? ' open' : ''), 'aria-hidden': 'true', onclick: () => closeDrawer() });
  mount(drawerHost(), h('div', { class: 'nav-drawer-root' }, scrim, drawer));

  const status = ctx.apiStatus ? h('span', { class: 'status-tag ' + ctx.apiStatus, title: ctx.apiStatus === 'up' ? t('nav.serverUp') : t('nav.serverDown'), role: 'status' },
    h('span', { class: 'status-dot' }), ctx.apiStatus === 'up' ? t('nav.live') : t('nav.serverOffline')) : null;

  const menuBtn = h('button', { class: 'icon-btn menu-btn', type: 'button', 'aria-label': drawerOpen ? t('nav.closeMenu') : t('nav.openMenu'), 'aria-expanded': String(drawerOpen), 'aria-controls': 'nav-center', onclick: () => ctx.toggleMenu() },
    h('span', { class: 'burger', 'aria-hidden': 'true' }, h('i'), h('i'), h('i')));
  const left = h('div', { class: 'nav-left' }, menuBtn,
    h('a', { class: 'brand', href: consoleMode ? '/admin' : '/', 'aria-label': 'RoadGuard home' }, h('span', { class: 'brand-mark' }, icon('shield', 18)), h('span', { class: 'brand-name' }, 'RoadGuard')), status);

  const search = h('button', { class: 'search-trigger', type: 'button', 'aria-label': 'Search or jump to a page (' + (isMac ? 'Command' : 'Control') + ' K)', onclick: () => openPalette() },
    icon('search', 16), h('span', { class: 'label' }, t('nav.search')), h('kbd', null, isMac ? '⌘K' : 'Ctrl K'));
  const cta = h('a', { class: 'cta', href: '/report' }, icon('plus', 14), t('nav.reportCta'));
  const consoleLink = isAdmin() && !consoleMode ? h('a', { class: 'btn small', href: '/admin' }, icon('gear', 14), t('nav.adminConsole')) : null;

  let account;
  if (me) {
    const unread = state.unread || 0;
    const bell = h('a', { class: 'icon-btn bell', href: '/notifications', 'aria-label': t('nav.notifications') + (unread ? ' (' + unread + ')' : ''), title: t('nav.notifications') }, icon('bell', 20), unread ? h('span', { class: 'bell-dot', 'aria-hidden': 'true' }) : null);
    const avatar = h('button', { class: 'avatar', type: 'button', 'aria-label': 'Account menu for ' + me.name, 'aria-haspopup': 'menu', 'aria-expanded': String(openMenu === 'user'), 'aria-controls': 'user-menu-list',
      onclick: (e) => { e.stopPropagation(); openMenu = openMenu === 'user' ? null : 'user'; redraw(); focusSoon('.avatar'); },
      onkeydown: (e) => { if (e.key === 'ArrowDown') { e.preventDefault(); openMenu = 'user'; redraw(); focusSoon('#user-menu-list .menu-item'); } } }, initials(me.name));
    const item = (label, href, extra) => h('a', { class: 'menu-item', role: 'menuitem', href: href, onclick: () => { openMenu = null; ctx.closePanel(); } }, label, extra || null);
    const dropdown = h('div', { class: 'dropdown user-dropdown', id: 'user-menu-list', role: 'menu', 'aria-label': 'Account', hidden: openMenu !== 'user', onkeydown: menuKeys },
      h('div', { class: 'menu-head' }, h('strong', null, me.name), h('span', null, me.email)),
      h('div', { class: 'menu-sep' }),
      item(t('nav.profile'), '/settings'),
      consoleMode ? null : item(t('nav.myReports'), '/complaints'), consoleMode ? null : item(t('nav.impact'), '/impact'),
      item(t('nav.notifications'), '/notifications', unread ? h('span', { class: 'menu-count' }, unread > 99 ? '99+' : unread) : null),
      isAdmin() && !consoleMode ? item(t('nav.adminConsole'), '/admin') : null,
      h('div', { class: 'menu-sep' }),
      h('button', { class: 'menu-item', role: 'menuitem', type: 'button', onclick: () => { openMenu = null; ctx.toggleTheme(); } }, dark ? t('nav.lightTheme') : t('nav.darkTheme')),
      h('button', { class: 'menu-item', role: 'menuitem', type: 'button', onclick: () => { openMenu = null; ctx.logout(); } }, t('nav.logout')));
    account = [bell, h('div', { class: 'user-menu' }, avatar, dropdown)];
  } else {
    account = [h('button', { class: 'icon-btn', type: 'button', 'aria-label': dark ? t('nav.lightTheme') : t('nav.darkTheme'), title: dark ? t('nav.lightTheme') : t('nav.darkTheme'), onclick: () => ctx.toggleTheme() }, icon(dark ? 'sun' : 'moon', 18)),
      h('div', { class: 'login-links' }, h('a', { class: 'btn ghost small', href: '/login' }, t('nav.login')), h('a', { class: 'btn small', href: '/register' }, t('nav.signup')))];
  }

  // Three zones: left (menu, brand, live status) | centre (search) | right (report, alerts, account).
  const centre = consoleMode ? null : h('div', { class: 'nav-search' }, search);
  const right = h('div', { class: 'nav-right' }, ctx.online === false ? h('span', { class: 'offline-chip', role: 'status' }, t('nav.offline')) : null, consoleMode ? null : cta, consoleLink, account);

  mount(el, h('div', { class: 'topbar-inner' }, left, centre, right));
}

// ------------------------------------------------------------------------------------------------ command palette

function paletteItems(query) {
  const pages = (consoleOnly() ? [
    ['Administrator console', '/admin'], ['Admin priority queue', '/admin?tab=queue'], ['Settings', '/settings'], ['Notifications', '/notifications'],
  ] : [
    ['Home', '/'], ['Hazards map', '/map'], ['Report a hazard', '/report'], ['My reports', '/complaints'], ['Safe routes', '/planner'], ['Saved routes', '/saved'],
    ['Community', '/community'], ['Fixed recently', '/repairs'], ['Dashboard', '/dashboard'], ['Analytics: road history', '/history'], ['Impact score', '/impact'],
    ['Notifications', '/notifications'], ['Settings', '/settings'],
    ...(isAdmin() ? [['Admin dashboard', '/admin'], ['Admin priority queue', '/admin?tab=queue']] : []),
  ]).map(([label, path]) => ({ label, hint: 'Go to page', path }));
  const q = query.trim();
  const lower = q.toLowerCase();
  const out = pages.filter((p) => !lower || p.label.toLowerCase().includes(lower));
  if (q.length >= 2) {
    out.push({ label: 'Search hazards for “' + q + '”', hint: 'Hazards map', path: '/map?q=' + encodeURIComponent(q) });
    out.push({ label: 'Search the community for “' + q + '”', hint: 'Community', path: '/community?q=' + encodeURIComponent(q) });
  }
  return out;
}

export function openPalette() {
  if (document.querySelector('.palette-overlay')) return;
  const input = h('input', { type: 'text', placeholder: 'Type a page name, or search hazards and the community…', role: 'combobox', 'aria-expanded': 'true', 'aria-controls': 'palette-list', 'aria-label': 'Search RoadGuard', autocomplete: 'off' });
  const list = h('ul', { class: 'palette-list', id: 'palette-list', role: 'listbox' });
  const overlay = h('div', { class: 'palette-overlay', role: 'dialog', 'aria-modal': 'true', 'aria-label': 'Search' },
    h('div', { class: 'palette' }, input, list, h('div', { class: 'palette-foot' }, h('span', null, '↑ ↓ to move'), h('span', null, 'Enter to open'), h('span', null, 'Esc to close'))));
  let items = []; let index = 0;
  const close = () => { overlay.remove(); const t = document.querySelector('.search-trigger'); if (t) t.focus(); };
  const go = (it) => { close(); navigate(it.path); };
  function draw() {
    items = paletteItems(input.value);
    index = Math.min(index, Math.max(items.length - 1, 0));
    mount(list, items.length ? items.map((it, i) => h('li', { class: 'palette-item', role: 'option', id: 'palette-opt-' + i, 'aria-selected': String(i === index), onmousemove: () => { index = i; mark(); }, onclick: () => go(it) }, h('span', null, it.label), h('small', null, it.hint)))
      : h('li', { class: 'palette-empty' }, 'Nothing matches. Try another word.'));
    input.setAttribute('aria-activedescendant', items.length ? 'palette-opt-' + index : '');
  }
  function mark() { list.querySelectorAll('.palette-item').forEach((n, i) => n.setAttribute('aria-selected', String(i === index))); input.setAttribute('aria-activedescendant', 'palette-opt-' + index); }
  input.addEventListener('input', () => { index = 0; draw(); });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); index = (index + 1) % Math.max(items.length, 1); mark(); const n = list.children[index]; if (n && n.scrollIntoView) n.scrollIntoView({ block: 'nearest' }); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); index = (index - 1 + items.length) % Math.max(items.length, 1); mark(); const n = list.children[index]; if (n && n.scrollIntoView) n.scrollIntoView({ block: 'nearest' }); }
    else if (e.key === 'Enter') { e.preventDefault(); if (items[index]) go(items[index]); }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(); }
    else if (e.key === 'Tab') { e.preventDefault(); }          // keep focus inside the dialog
  });
  overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
  document.body.appendChild(overlay);
  draw();
  input.focus();
}
