import { api } from '../api.js';
import { state, refreshUnread, isAdmin } from '../state.js';
import { h, icon, mount, skeleton, errorState, emptyState, pageHeader, toast } from '../ui.js';
import { timeAgo } from '../format.js';
import { navigate } from '../router.js';

export async function notificationsPage({ container, isCurrent }) {
  let page = 0;
  let items = [];
  let info = null;
  const list = h('div', { class: 'card', style: 'padding:0' });
  const more = h('div', { class: 'btn-row', style: 'justify-content:center;margin-top:12px' });
  const markAll = h('button', { class: 'btn', type: 'button', onclick: async () => {
    try { const r = await api.post('/api/notifications/read-all'); toast(r.marked ? r.marked + ' notification(s) marked as read.' : 'Nothing to mark.', 'success'); items.forEach((n) => { n.read = true; }); draw(); refreshUnread(); }
    catch (e) { toast(e.message, 'error'); }
  } }, icon('check', 16), 'Mark all as read');

  async function open(n) {
    if (!n.read) { try { await api.post('/api/notifications/' + n.id + '/read'); n.read = true; refreshUnread(); } catch (e) { /* still navigate */ } }
    if (n.type === 'HAZARD_FLAGGED' && isAdmin()) navigate('/admin?tab=queue');
    else if (n.complaintRef) navigate(isAdmin() && n.type === 'ADMIN_NEW_COMPLAINT' ? '/admin?complaint=' + n.complaintRef : '/complaints/' + n.complaintRef);
    else draw();
  }

  function draw() {
    mount(list, items.length ? items.map((n) => h('div', { class: 'notif' + (n.read ? '' : ' unread'), role: 'button', tabindex: '0', onclick: () => open(n), onkeydown: (e) => { if (e.key === 'Enter') open(n); } },
      h('span', { class: 'notif-dot', 'aria-label': n.read ? 'read' : 'unread' }),
      h('div', { class: 'nb' }, h('strong', null, n.title), h('div', null, n.message), h('div', { class: 'when' }, timeAgo(n.createdAt))))) : emptyState('No notifications yet', 'You will be told here when you submit a complaint or its status changes.'));
    mount(more, info && page + 1 < info.totalPages ? h('button', { class: 'btn', type: 'button', onclick: () => { page++; load(true); } }, 'Load more') : null);
    markAll.disabled = !items.some((n) => !n.read);
  }

  async function load(append) {
    if (!append) mount(list, skeleton(5));
    try {
      info = await api.get('/api/notifications', { page, size: 15 });
      if (!isCurrent()) return;
      items = append ? items.concat(info.items) : info.items;
      draw();
    } catch (e) { if (isCurrent()) mount(list, errorState(e, () => load(false))); }
  }

  mount(container, pageHeader('Notifications', 'In-app updates about your complaints', markAll), list, more);
  await load(false);
}
