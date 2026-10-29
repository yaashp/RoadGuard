// Entry point: loads reference data and the session, draws the navigation bar and starts the router.
import { api, onUnauthorized } from './api.js';
import { state, loadMeta, loadMe, subscribe, refreshUnread, applyTheme, storedTheme, clearSession, isAdmin } from './state.js';
import { route, render, navigate, currentPath } from './router.js';
import { toast, errorState, mount, skeleton } from './ui.js';
import { applyLanguage, storedLanguage, t } from './i18n/index.js';

import { homePage } from './pages/home.js';
import { dashboardPage } from './pages/dashboard.js';
import { impactPage } from './pages/impact.js';
import { flushQueue } from './queue.js';
import { renderNavbar, closeMenus } from './navbar.js';
import { communityPage, communityPostPage, communityProfilePage } from './pages/community.js';
import { repairPage, fixedFeedPage } from './pages/repair.js';
import { loginPage, registerPage } from './pages/auth.js';
import { reportPage } from './pages/report.js';
import { complaintsPage, complaintDetailPage } from './pages/complaints.js';
import { mapPage } from './pages/map.js';
import { plannerPage } from './pages/planner.js';
import { savedPage } from './pages/saved.js';
import { historyPage } from './pages/history.js';
import { adminPage } from './pages/admin.js';
import { notificationsPage } from './pages/notifications.js';
import { settingsPage } from './pages/settings.js';
import { contactPage } from './pages/contact.js';
import { privacyPage, termsPage } from './pages/legal.js';

route('/', homePage);
route('/login', loginPage);
route('/register', registerPage);
route('/report', reportPage, 'user');
route('/complaints', complaintsPage, 'user');
route('/complaints/:id', complaintDetailPage, 'user');
route('/dashboard', dashboardPage);
route('/impact', impactPage);
route('/community', communityPage);
route('/repairs', fixedFeedPage);
route('/repair/:id', repairPage);
route('/community/posts/:id', communityPostPage);
route('/community/users/:id', communityProfilePage);
route('/map', mapPage);
route('/planner', plannerPage, 'user');
route('/saved', savedPage, 'user');
route('/history', historyPage);
route('/admin', adminPage, 'admin');
route('/notifications', notificationsPage, 'user');
route('/settings', settingsPage, 'user');
route('/privacy', privacyPage);
route('/terms', termsPage);
route('/contact', contactPage);

const view = document.getElementById('view');
const topbar = document.getElementById('topbar');
let menuOpen = false;

let apiStatus = null;   // 'up' | 'down' | null (unknown until the first check)

async function checkHealth() {
  try {
    const r = await fetch('/api/health', { cache: 'no-store' });
    const j = await r.json();
    apiStatus = r.ok && j.status === 'UP' ? 'up' : 'down';
  } catch (e) { apiStatus = 'down'; }
  drawNav();
}

async function toggleTheme() {
  const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  applyTheme(next);
  if (state.me) { try { state.prefs = await api.put('/api/users/me/preferences', { theme: next }); } catch (e) { /* theme still applied locally */ } }
  drawNav();
  render(view);   // charts read theme colours when they are created
}

function drawNav() {
  renderNavbar(topbar, {
    path: currentPath(), menuOpen, apiStatus, online: navigator.onLine, redraw: drawNav,
    toggleMenu: () => { menuOpen = !menuOpen; drawNav(); }, closePanel: () => { menuOpen = false; },
    toggleTheme, logout,
  });
}

async function logout() {
  try { await api.post('/api/auth/logout'); } catch (e) { /* ignore: clearing locally anyway */ }
  clearSession();
  toast(t('settings.loggedOut'), 'success');
  navigate('/');
}

onUnauthorized(() => {
  if (state.me) {
    clearSession();
    toast(t('app.sessionExpired'), 'error');
    sessionStorage.setItem('rg_after_login', location.pathname + location.search);
    navigate('/login');
  }
});

subscribe(drawNav);
window.addEventListener('languagechange', () => { drawNav(); if (ready) render(view); });   // settings changed the language
async function syncQueue() {
  if (!state.me || navigator.onLine === false) return;
  const sent = await flushQueue();
  sent.forEach((c) => toast(t('app.savedSubmitted', { id: c.complaintId }), 'success'));
  if (sent.length) refreshUnread();
}
window.addEventListener('online', () => { checkHealth(); syncQueue(); });
window.addEventListener('offline', () => { apiStatus = 'down'; drawNav(); });
let ready = false;   // pages read reference data (meta, session); nothing renders until startup has loaded it
window.addEventListener('routechange', () => { menuOpen = false; closeMenus(); drawNav(); if (ready) render(view); refreshUnread(); syncQueue(); });   // keeps the bell badge fresh as people navigate
setInterval(() => { if (!document.hidden) { refreshUnread(); checkHealth(); } }, 60000);

async function start() {
  applyTheme(storedTheme());
  applyLanguage(storedLanguage());
  mount(view, skeleton(6));
  drawNav();
  checkHealth();
  try {
    await loadMeta();
    await loadMe();
    if (state.prefs) applyLanguage(state.prefs.language);   // the account's saved language wins over this browser's
    ready = true;
  } catch (e) {
    mount(view, errorState(e, () => location.reload()));
    return;
  }
  drawNav();
  refreshUnread();
  syncQueue();
  render(view);
}

start();
