// RoadGuard Impact Score: how points are earned (public), my contributions, and the opt-in monthly leaderboard.
import { api } from '../api.js';
import { state } from '../state.js';
import { h, icon, mount, skeleton, errorState, emptyState, pageHeader, toast } from '../ui.js';
import { fmtDate, timeAgo } from '../format.js';

export async function impactPage({ container, isCurrent }) {
  const me = h('div'); const board = h('div'); const rulesBox = h('div');
  const month = h('input', { type: 'month', value: new Date().toISOString().slice(0, 7), 'aria-label': 'Leaderboard month' });

  async function loadMe() {
    if (!state.me) { mount(me, h('div', { class: 'banner info' }, h('span', null, 'Log in to see your own points, badges and contribution history.'), h('a', { class: 'btn small primary', href: '/login' }, 'Log in'))); return; }
    mount(me, skeleton(4));
    try {
      const s = await api.get('/api/impact/me');
      if (!isCurrent()) return;
      const box = h('input', { type: 'checkbox', checked: s.showOnLeaderboard });
      box.addEventListener('change', async () => {
        try { state.prefs = await api.put('/api/users/me/preferences', { showOnLeaderboard: box.checked }); toast(box.checked ? 'You are on the leaderboard with a short name.' : 'You are no longer on the leaderboard.', 'success'); loadBoard(); }
        catch (e) { toast(e.message, 'error'); box.checked = !box.checked; }
      });
      mount(me,
        h('div', { class: 'stat-grid' },
          h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Total points'), h('div', { class: 'value' }, s.totalPoints)),
          h('div', { class: 'card stat ok' }, h('div', { class: 'label' }, 'This month'), h('div', { class: 'value' }, s.pointsThisMonth)),
          h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Accepted reports'), h('div', { class: 'value' }, s.validatedReports), h('div', { class: 'sub' }, s.resolvedReports + ' resolved')),
          h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Confirmations'), h('div', { class: 'value' }, s.confirmations), h('div', { class: 'sub' }, 'rewarded answers'))),
        h('div', { class: 'grid two' },
          h('div', { class: 'card' }, h('h3', null, 'Badges'), h('div', { class: 'badge-grid' }, s.badges.map((b) => h('div', { class: 'achv' + (b.earned ? ' earned' : '') }, icon(b.earned ? 'check' : 'shield', 18), h('div', null, h('strong', null, b.name), h('div', { class: 'small muted' }, b.description), h('div', { class: 'small tech' }, b.earned ? 'Earned' : 'Not yet')))))),
          h('div', { class: 'card' }, h('h3', null, 'Your contribution history'), s.events.length ? h('ul', { class: 'list-plain' }, s.events.slice(0, 12).map((e) => h('li', null, h('span', null, h('span', { class: 'ref' }, e.reference), ' · ', e.description, h('div', { class: 'hint' }, fmtDate(e.at))), h('strong', { class: 'tech' }, '+' + e.points))))
            : emptyState('No points yet', 'Points appear once a report is accepted by staff or you confirm someone else\'s hazard.'))),
        h('label', { class: 'toggle', style: 'margin-top:14px' }, box, 'Show me on the public monthly leaderboard (short name only, e.g. "Asha K.")'));
    } catch (e) { mount(me, errorState(e, loadMe)); }
  }

  async function loadBoard() {
    mount(board, skeleton(3));
    try {
      const lb = await api.get('/api/impact/leaderboard', { month: month.value });
      if (!isCurrent()) return;
      mount(board, lb.rows.length ? h('div', { class: 'table-wrap' }, h('table', null, h('thead', null, h('tr', null, ['Rank', 'Contributor', 'Points'].map((c, i) => h('th', { class: i === 2 ? 'num' : '' }, c)))),
        h('tbody', null, lb.rows.map((r) => h('tr', null, h('td', null, '#' + r.rank), h('td', null, r.displayName), h('td', { class: 'num' }, r.points)))))) : emptyState('No one on the board yet', lb.note), h('p', { class: 'hint' }, lb.note));
    } catch (e) { mount(board, errorState(e, loadBoard)); }
  }

  async function loadStreet() {
    const box = h('div', { class: 'card street-card' });
    mount(street, box);
    if (!state.me) { mount(box, h('h3', null, 'Your street'), h('p', { class: 'muted' }, 'Log in to see what your confirmations and reports have led to.')); return; }
    mount(box, skeleton(3));
    try {
      const s = await api.get('/api/impact/street');
      if (!isCurrent()) return;
      mount(box, h('h3', null, 'Your street'),
        h('div', { class: 'stat-grid street-stats' },
          h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'You confirmed'), h('div', { class: 'value' }, s.confirmations), h('div', { class: 'sub' }, 'hazards')),
          h('div', { class: 'card stat ok' }, h('div', { class: 'label' }, 'Since then resolved'), h('div', { class: 'value' }, s.confirmationsOnHazardsNowResolved)),
          h('div', { class: 'card stat warn' }, h('div', { class: 'label' }, 'Still open'), h('div', { class: 'value' }, s.confirmationsOnHazardsStillOpen)),
          h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Your reports resolved'), h('div', { class: 'value' }, s.yourReportsResolved), h('div', { class: 'sub' }, s.yourReportsOpen + ' still open'))),
        s.items.length ? h('ul', { class: 'list-plain' }, s.items.map((i) => h('li', null, h('span', null, h('span', { class: 'ref' }, i.reportId), ' · ' + i.hazardType + (i.roadName ? ' on ' + i.roadName : ''), h('div', { class: 'hint' }, i.outcome)), h('span', { class: 'hint' }, timeAgo(i.at))))) : h('p', { class: 'muted' }, 'You have not confirmed any hazard yet. Open the map and answer "Is it still there?" on a hazard.'),
        h('p', { class: 'hint' }, s.note));
    } catch (e) { mount(box, errorState(e, loadStreet)); }
  }

  async function loadRules() {
    try {
      const r = await api.get('/api/impact/rules');
      mount(rulesBox, h('ul', { class: 'explain' }, r.rules.map((t) => h('li', null, t))));
    } catch (e) { mount(rulesBox, errorState(e, loadRules)); }
  }
  month.addEventListener('change', loadBoard);

  const street = h('div');
  mount(container, pageHeader('Impact score', 'Recognition for useful reports and honest confirmations'), me, street,
    h('div', { class: 'grid two', style: 'margin-top:16px' },
      h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h3', null, 'Monthly leaderboard'), month), board),
      h('div', { class: 'card' }, h('h3', null, 'How points work'), rulesBox)));
  await Promise.all([loadMe(), loadStreet(), loadBoard(), loadRules()]);
}
