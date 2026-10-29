// Citizen dashboard: the signed-in user's view of road safety. Headline figures, quick actions, the latest five
// reports and the roads that still need attention. Charts and the activity feed are admin-only (Admin > Analytics).
import { api } from '../api.js';
import { state, includeSample, setIncludeSample, isAdmin } from '../state.js';
import { h, icon, mount, skeleton, skeletonCards, errorState, emptyState, severityBadge, statusBadge, sampleBadge, pageHeader } from '../ui.js';
import { fmtNum, timeAgo, pct } from '../format.js';

const RECENT_LIMIT = 5;

export async function dashboardPage({ container, isCurrent }) {
  const body = h('div');
  const scopeNote = isAdmin() ? 'All users, system-wide (admin view)' : 'Your reports only';
  mount(container, pageHeader('Dashboard', 'Live figures from ' + (isAdmin() ? 'reported hazards' : 'your own reports') + ' · ' + scopeNote,
    h('a', { class: 'btn', href: '/history' }, icon('clock', 16), 'Road history'), h('a', { class: 'btn', href: '/impact' }, icon('shield', 16), 'Impact score'),
    isAdmin() ? h('a', { class: 'btn primary', href: '/admin' }, icon('gear', 16), 'Admin console') : null), body);

  async function load() {
    if (!state.me) {   // figures are personal: guests see a login prompt only
      mount(body, emptyState('Log in to see your dashboard', 'Figures here cover only the reports you submitted.', h('a', { class: 'btn primary', href: '/login' }, 'Log in')));
      return;
    }
    mount(body, skeletonCards(6), skeleton(6));
    try {
      const d = await api.get('/api/dashboard', { includeSample: includeSample() });
      if (isCurrent()) draw(d);
    } catch (e) { if (isCurrent()) mount(body, errorState(e, load)); }
  }

  function stat(label, value, sub, cls = '', tip) {
    return h('div', { class: 'card stat ' + cls, 'data-tip': tip }, h('div', { class: 'label' }, label), h('div', { class: 'value' }, value), sub ? h('div', { class: 'sub' }, sub) : null);
  }

  function draw(d) {
    const t = d.totals;
    const sampleBanner = d.sampleComplaintCount > 0 ? h('div', { class: 'banner' },
      h('span', null, icon('info', 16), ' ', d.includesSampleData
        ? 'These figures include ' + fmtNum(d.sampleComplaintCount) + ' sample (demonstration) complaints next to ' + fmtNum(d.userComplaintCount) + ' real report(s).'
        : 'Sample data is hidden. Showing only the ' + fmtNum(d.userComplaintCount) + ' real report(s).'),
      h('button', { class: 'btn small', type: 'button', onclick: async () => { await setIncludeSample(!includeSample()); load(); } }, d.includesSampleData ? 'Hide sample data' : 'Show sample data')) : null;

    const actions = h('div', { class: 'quick-actions' },
      h('a', { class: 'quick', href: '/report' }, icon('plus', 22), h('span', null, 'Report an issue', h('small', null, 'Pothole, streetlight, flooding…'))),
      h('a', { class: 'quick', href: '/complaints' }, icon('list', 22), h('span', null, 'View my complaints', h('small', null, 'Track progress and history'))),
      h('a', { class: 'quick alt', href: '/map' }, icon('map', 22), h('span', null, 'Open the map', h('small', null, 'See hazards near you'))),
      h('a', { class: 'quick alt', href: '/planner' }, icon('route', 22), h('span', null, 'Plan a safer route', h('small', null, 'Compare hazard exposure'))));

    const stats = h('div', { class: 'stat-grid' },
      stat('Road issues on map', fmtNum(t.totalReportedIssues), 'excluding rejected reports', '', 'Number of hazard pins currently on the map'),
      stat('Open complaints', fmtNum(t.openComplaints), 'of ' + fmtNum(t.totalComplaints) + ' filed', 'warn', 'Submitted, under review, assigned or in progress'),
      stat('Under review', fmtNum(t.underReview), 'being checked by staff', ''),
      stat('Resolved', fmtNum(t.resolved), t.rejected ? fmtNum(t.rejected) + ' rejected' : 'none rejected', 'ok'),
      stat('High-severity hazards', fmtNum(t.highSeverityOpen), 'open on the map', 'danger', 'Open road issues rated High'),
      stat('Resolution rate', pct(d.conditions.resolutionRatePercent), d.conditions.avgDaysToResolve != null ? 'avg ' + d.conditions.avgDaysToResolve + ' days to resolve' : 'no timed resolutions yet', 'ok', 'Resolved ÷ (resolved + open). Average time uses recorded status changes only.'));

    if (t.totalComplaints === 0) {
      mount(body, sampleBanner, actions, stats,
        emptyState('No complaints yet', 'Once road problems are reported, the latest reports and roads needing attention appear here.', h('a', { class: 'btn primary', href: '/report' }, 'Report the first issue')));
      return;
    }

    const latest = d.recentReports.slice(0, RECENT_LIMIT);
    const recent = latest.length ? h('div', { class: 'table-wrap' }, h('table', null,
      h('thead', null, h('tr', null, ['Reference', 'Category', 'Road', 'Severity', 'Status', 'Reported'].map((c) => h('th', null, c)))),
      h('tbody', null, latest.map((r) => h('tr', null,
        h('td', null, h('span', { class: 'ref' }, r.complaintId), r.sample ? [' ', sampleBadge()] : null),
        h('td', null, r.category), h('td', null, r.roadName), h('td', null, severityBadge(r.severity)), h('td', null, statusBadge(r.status)), h('td', null, timeAgo(r.createdAt))))))) : emptyState('No reports yet');

    const attention = d.conditions.roadsNeedingAttention.length ? h('ul', { class: 'list-plain' }, d.conditions.roadsNeedingAttention.map((r) => h('li', null,
      h('a', { href: '/history?road=' + encodeURIComponent(r.road) }, r.road),
      h('span', { class: 'muted small' }, r.openIssues + ' open' + (r.highSeverity ? ' · ' + r.highSeverity + ' high' : ''))))) : emptyState('Nothing open', 'No road currently has open complaints.');

    mount(body, sampleBanner, actions, stats,
      h('div', { class: 'grid side', style: 'margin-top:16px' },
        h('div', null, h('div', { class: 'card-head' }, h('h2', null, 'Recent reports'), h('span', { class: 'hint' }, 'Latest ' + RECENT_LIMIT)), recent),
        h('div', { class: 'card' }, h('h3', null, 'Roads needing attention'), attention)));
  }

  await load();
}
