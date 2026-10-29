import { api } from '../api.js';
import { h, icon, mount, skeleton, errorState, emptyState, pageHeader, toast, classBadge, confirmDialog, modal, field } from '../ui.js';
import { timeAgo, fmtDistance, fmtDuration } from '../format.js';
import { routeTabs } from './routetabs.js';

export async function savedPage({ container, isCurrent }) {
  const list = h('div', { class: 'grid' });
  let routes = [];

  function trend(r) {
    if (r.lastScore == null || r.previousScore == null) return null;
    const d = r.lastScore - r.previousScore;
    if (d === 0) return h('span', { class: 'muted small' }, 'unchanged since last check');
    return h('span', { class: d > 0 ? 'trend-up' : 'trend-down' }, icon(d > 0 ? 'arrowUp' : 'arrowDown', 14), ' ', (d > 0 ? '+' : '') + d + ' since last check (was ' + r.previousScore + ')');
  }

  function card(r) {
    return h('div', { class: 'card' },
      h('div', { class: 'card-head' }, h('h3', null, r.name), r.lastClassification ? classBadge(r.lastClassification) : h('span', { class: 'badge' }, 'Not scored yet')),
      h('p', { class: 'muted' }, r.originLabel + '  →  ' + r.destinationLabel),
      r.lastScore != null ? h('div', null,
        h('div', { class: 'score' }, String(r.lastScore), h('small', null, '/ 100 estimated safety score')),
        h('div', { class: 'route-stats', style: 'margin:6px 0' }, h('span', null, r.lastHazardCount + ' hazard(s) near the best route'), h('span', null, fmtDistance(r.lastDistanceMeters)), h('span', null, fmtDuration(r.lastDurationSeconds)), h('span', null, 'checked ' + timeAgo(r.lastAnalyzedAt))),
        trend(r)) : h('p', { class: 'hint' }, 'No score yet (the routing service was unavailable when this route was saved). Use "Re-check" to calculate it.'),
      h('div', { class: 'btn-row', style: 'margin-top:12px' },
        h('a', { class: 'btn primary small', href: '/planner?saved=' + r.id }, icon('route', 14), 'Open in planner'),
        h('button', { class: 'btn small', type: 'button', onclick: (e) => recheck(r, e.currentTarget) }, 'Re-check now'),
        h('button', { class: 'btn small', type: 'button', onclick: () => rename(r) }, icon('edit', 14), 'Rename'),
        h('button', { class: 'btn small danger', type: 'button', onclick: () => remove(r) }, icon('trash', 14), 'Delete')));
  }

  function draw() {
    mount(list, routes.length ? routes.map(card) : emptyState('No saved routes yet', 'Plan a route and press "Save" to keep it here and track its hazard exposure over time.', h('a', { class: 'btn primary', href: '/planner' }, 'Plan a route')));
  }

  async function recheck(r, btn) {
    btn.disabled = true; btn.textContent = 'Checking…';
    try {
      const res = await api.post('/api/saved-routes/' + r.id + '/analyze');
      routes = routes.map((x) => (x.id === r.id ? res.route : x)); draw();
      toast(res.change == null ? 'Latest estimated score: ' + res.latestScore + '/100.' : res.change === 0 ? 'Score unchanged at ' + res.latestScore + '/100.' : 'Score is now ' + res.latestScore + '/100 (' + (res.change > 0 ? '+' : '') + res.change + ' vs ' + res.previousScore + ').', res.change < 0 ? 'error' : 'success');
    } catch (e) { toast(e.message, 'error'); btn.disabled = false; btn.textContent = 'Re-check now'; }
  }

  async function remove(r) {
    if (!(await confirmDialog({ title: 'Delete saved route?', message: 'Delete "' + r.name + '"? This cannot be undone.', confirmText: 'Delete', danger: true }))) return;
    try { await api.del('/api/saved-routes/' + r.id); routes = routes.filter((x) => x.id !== r.id); draw(); toast('Route deleted.', 'success'); } catch (e) { toast(e.message, 'error'); }
  }

  function rename(r) {
    const name = h('input', { name: 'name', type: 'text', value: r.name, maxlength: '80' });
    const body = h('div', null, field('Route name', name));
    const dlg = modal('Rename route', body);
    const save = h('button', { class: 'btn primary', type: 'button', onclick: async () => {
      try { const upd = await api.patch('/api/saved-routes/' + r.id, { name: name.value }); routes = routes.map((x) => (x.id === r.id ? upd : x)); draw(); dlg.close(); toast('Route renamed.', 'success'); }
      catch (e) { toast((e.fieldErrors && e.fieldErrors.name) || e.message, 'error'); }
    } }, 'Save');
    dlg.card.querySelector('.dialog-actions').prepend(save);
    name.focus();
  }

  async function load() {
    mount(list, skeleton(5));
    try { routes = await api.get('/api/saved-routes'); if (isCurrent()) draw(); } catch (e) { if (isCurrent()) mount(list, errorState(e, load)); }
  }
  mount(container, pageHeader('Safe routes', 'Your regular journeys and their latest estimated hazard exposure', h('a', { class: 'btn primary', href: '/planner' }, icon('plus', 16), 'Plan a route')), routeTabs('saved'), list);
  await load();
}
