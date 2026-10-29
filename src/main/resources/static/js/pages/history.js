// Road history: real complaint history per road, trends, repeat problems and period comparison.
import { api } from '../api.js';
import { navigate } from '../router.js';
import { state, includeSample, setIncludeSample, isAdmin } from '../state.js';
import { h, icon, mount, skeleton, skeletonCards, errorState, emptyState, severityBadge, statusBadge, sampleBadge, pageHeader, debounce, field } from '../ui.js';
import { ChartSet, STATUS_COLORS, SEVERITY_COLORS } from '../charts.js';
import { fmtDate, fmtNum, pct } from '../format.js';

const trendText = (t, p) => (t === 'up' ? 'up ' + (p == null ? '' : p + '%') : t === 'down' ? 'down ' + (p == null ? '' : Math.abs(p) + '%') : t === 'new' ? 'new activity (none before)' : 'no change');

export async function historyPage({ container, query, isCurrent }) {
  const charts = new ChartSet();
  if (!state.me) {   // road history is personal: nothing is shown to guests
    mount(container, pageHeader('Road history', 'Log in to see your own reports'),
      emptyState('Log in to see your road history', 'Figures here cover only the reports you submitted.', h('a', { class: 'btn primary', href: '/login' }, 'Log in')));
    return () => charts.destroy();
  }
  const scopeNote = isAdmin() ? 'All users, system-wide (admin view)' : 'Your reports only';
  const sampleBanner = (onChange) => {
    const box = h('input', { type: 'checkbox', checked: includeSample() });
    box.addEventListener('change', async () => { await setIncludeSample(box.checked); onChange(); });
    return h('label', { class: 'toggle' }, box, 'Include sample data (labelled)');
  };

  async function overview() {
    charts.destroy();
    const results = h('div'); const hot = h('div'); const cmp = h('div');
    const q = h('input', { type: 'search', placeholder: 'Search a road, e.g. "Linking Road"', 'aria-label': 'Search roads' });
    const period = h('select', { 'aria-label': 'Comparison period' }, [30, 60, 90, 180].map((d) => h('option', { value: d }, 'Last ' + d + ' days vs the ' + d + ' before')));

    const roadTable = (rows) => rows.length ? h('div', { class: 'table-wrap' }, h('table', null,
      h('thead', null, h('tr', null, ['Road', 'Complaints', 'Open', 'Resolved', 'High severity', 'Last reported'].map((c, i) => h('th', { class: i > 0 && i < 5 ? 'num' : '' }, c)))),
      h('tbody', null, rows.map((r) => h('tr', { class: 'clickable', tabindex: '0', onclick: () => { navigate('/history?road=' + encodeURIComponent(r.road)); }, onkeydown: (e) => { if (e.key === 'Enter') navigate('/history?road=' + encodeURIComponent(r.road)); } },
        h('td', null, h('a', { href: '/history?road=' + encodeURIComponent(r.road) }, r.road)), h('td', { class: 'num' }, r.totalComplaints), h('td', { class: 'num' }, r.openComplaints), h('td', { class: 'num' }, r.resolvedComplaints), h('td', { class: 'num' }, r.highSeverity), h('td', null, fmtDate(r.lastReportedAt))))))) : emptyState('No roads found', 'No complaints match this search yet.');

    async function search() {
      const text = q.value.trim();
      if (!text) { mount(results); return; }
      mount(results, skeleton(3));
      try { const rows = await api.get('/api/roads/search', { q: text, includeSample: includeSample() }); if (isCurrent()) mount(results, h('h2', null, 'Search results'), roadTable(rows.map((r) => ({ ...r, hotspot: false })))); }
      catch (e) { mount(results, errorState(e, search)); }
    }
    async function loadHot() {
      mount(hot, skeleton(4));
      try {
        const rows = await api.get('/api/roads/hotspots', { limit: 10, includeSample: includeSample() });
        if (!isCurrent()) return;
        mount(hot, h('h2', null, 'Most frequently reported roads'), rows.length ? h('div', { class: 'table-wrap' }, h('table', null,
          h('thead', null, h('tr', null, ['Road', 'Complaints', 'Open', 'High severity', 'Most common problem'].map((c, i) => h('th', { class: i > 0 && i < 4 ? 'num' : '' }, c)))),
          h('tbody', null, rows.map((r) => h('tr', { class: 'clickable', onclick: () => { navigate('/history?road=' + encodeURIComponent(r.road)); } },
            h('td', null, h('a', { href: '/history?road=' + encodeURIComponent(r.road) }, r.road)), h('td', { class: 'num' }, r.totalComplaints), h('td', { class: 'num' }, r.openComplaints), h('td', { class: 'num' }, r.highSeverity), h('td', null, r.topCategory)))))) : emptyState('No complaint history yet', 'Roads appear here once problems have been reported.'));
      } catch (e) { mount(hot, errorState(e, loadHot)); }
    }
    async function loadCmp() {
      mount(cmp, skeleton(2));
      try {
        const c = await api.get('/api/roads/compare', { periodDays: period.value, includeSample: includeSample() });
        if (!isCurrent()) return;
        const col = (title, s) => h('div', { class: 'card stat' }, h('div', { class: 'label' }, title), h('div', { class: 'value' }, fmtNum(s.complaints)), h('div', { class: 'sub' }, s.highSeverity + ' high severity · ' + s.resolved + ' resolved'), h('div', { class: 'sub' }, fmtDate(s.from) + ' – ' + fmtDate(s.to)));
        mount(cmp, h('div', { class: 'stat-grid' }, col('Current period', c.current), col('Previous period', c.previous),
          h('div', { class: 'card stat ' + (c.trend === 'up' ? 'danger' : c.trend === 'down' ? 'ok' : 'warn') }, h('div', { class: 'label' }, 'Change in complaints'), h('div', { class: 'value' }, c.changePercent == null ? (c.trend === 'new' ? 'new' : '0%') : (c.changePercent > 0 ? '+' : '') + c.changePercent + '%'), h('div', { class: 'sub' }, 'Complaint volume is ' + trendText(c.trend, c.changePercent)))));
      } catch (e) { mount(cmp, errorState(e, loadCmp)); }
    }
    q.addEventListener('input', debounce(search, 350));
    period.addEventListener('change', loadCmp);
    mount(container, pageHeader('Road history', 'Past complaints on each road · ' + scopeNote, sampleBanner(overview)),
      h('div', { class: 'card', style: 'margin-bottom:16px' }, field('Find a road', q)), results,
      h('div', { style: 'margin-top:16px' }, hot),
      h('div', { style: 'margin-top:24px' }, h('div', { class: 'card-head' }, h('h2', null, 'Compare time periods'), period), cmp));
    await Promise.all([loadHot(), loadCmp()]);
  }

  async function detail() {
    charts.destroy();
    mount(container, h('p', null, h('a', { href: '/history' }, '← All roads')), skeletonCards(4), skeleton(6));
    let d;
    try { d = await api.get('/api/roads/history', { road: query.road, includeSample: includeSample() }); }
    catch (e) { if (isCurrent()) mount(container, h('p', null, h('a', { href: '/history' }, '← All roads')), e.status === 404 ? emptyState('No history for this road', 'No complaints match "' + query.road + '"' + (includeSample() ? '.' : ' (sample data is hidden).')) : errorState(e, detail)); return; }
    if (!isCurrent()) return;
    const s = d.summary, r = d.resolution, vc = d.recentChange;
    const cv = (title, id) => { const c = h('canvas', { id, role: 'img', 'aria-label': title }); return { c, card: h('div', { class: 'card' }, h('h3', null, title), h('div', { class: 'chart-box' }, c)) }; };
    const weekly = cv('Complaints per week (12 weeks)', 'h-w'), monthly = cv('Complaints per month (12 months)', 'h-m'), sev = cv('Severity by month', 'h-s'), cat = cv('Problem types', 'h-c');
    mount(container, h('p', null, h('a', { href: '/history' }, '← All roads')),
      pageHeader(s.road, 'Complaint history', sampleBanner(detail)),
      h('div', { class: 'banner info small' }, icon('info', 16), h('span', null, d.dataNote)),
      h('div', { class: 'stat-grid' },
        h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Total complaints'), h('div', { class: 'value' }, fmtNum(s.totalComplaints)), h('div', { class: 'sub' }, 'last on ' + fmtDate(s.lastReportedAt))),
        h('div', { class: 'card stat warn' }, h('div', { class: 'label' }, 'Still open'), h('div', { class: 'value' }, fmtNum(s.openComplaints))),
        h('div', { class: 'card stat ok' }, h('div', { class: 'label' }, 'Resolved'), h('div', { class: 'value' }, fmtNum(r.resolved)), h('div', { class: 'sub' }, 'rate ' + pct(r.resolutionRatePercent) + (r.avgDaysToResolve != null ? ' · avg ' + r.avgDaysToResolve + ' days' : ''))),
        h('div', { class: 'card stat danger' }, h('div', { class: 'label' }, 'High severity'), h('div', { class: 'value' }, fmtNum(s.highSeverity))),
        h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Last ' + vc.periodDays + ' days'), h('div', { class: 'value' }, fmtNum(vc.current)), h('div', { class: 'sub' }, 'previous ' + vc.periodDays + ' days: ' + vc.previous + ' · volume ' + trendText(vc.trend, vc.changePercent)))),
      h('div', { class: 'grid two' }, weekly.card, monthly.card), h('div', { class: 'grid two', style: 'margin-top:16px' }, sev.card, cat.card),
      h('div', { class: 'grid two', style: 'margin-top:16px' },
        h('div', { class: 'card' }, h('h3', null, 'Repeated problems on this road'), d.repeatedIssues.length ? h('ul', { class: 'list-plain' }, d.repeatedIssues.map((x) => h('li', null, h('span', null, h('strong', null, x.category), ' reported ' + x.count + ' times'), h('span', { class: 'muted small' }, fmtDate(x.firstReported) + ' – ' + fmtDate(x.lastReported))))) : emptyState('No repeats', 'No problem type has been reported more than once here.')),
        h('div', { class: 'card' }, h('h3', null, 'Outcome of complaints'), h('ul', { class: 'list-plain' }, h('li', null, 'Resolved', h('strong', null, r.resolved)), h('li', null, 'Open', h('strong', null, r.open)), h('li', null, 'Rejected', h('strong', null, r.rejected))))),
      h('h2', { style: 'margin-top:24px' }, 'All complaints on this road'),
      h('div', { class: 'table-wrap' }, h('table', null,
        h('thead', null, h('tr', null, ['Reference', 'Problem', 'Severity', 'Status', 'Reported', 'Resolved'].map((c) => h('th', null, c)))),
        h('tbody', null, d.records.map((x) => h('tr', null, h('td', null, h('span', { class: 'ref' }, x.complaintId), x.sample ? [' ', sampleBadge()] : null), h('td', null, x.category), h('td', null, severityBadge(x.severity)), h('td', null, statusBadge(x.status)), h('td', null, fmtDate(x.createdAt)), h('td', null, x.resolvedAt ? fmtDate(x.resolvedAt) : '—')))))));
    charts.line(weekly.c, d.weekly.map((p) => p.label), [{ label: 'Reported', data: d.weekly.map((p) => p.reported), color: '#E65100' }, { label: 'Resolved', data: d.weekly.map((p) => p.resolved), color: '#2E403B' }]);
    charts.bar(monthly.c, d.monthly.map((p) => p.label), d.monthly.map((p) => p.reported), { color: '#4A5D57', label: 'Reported' });
    charts.stacked(sev.c, d.severityMonthly.map((p) => p.label), [{ label: 'High', data: d.severityMonthly.map((p) => p.high), color: SEVERITY_COLORS.High }, { label: 'Medium', data: d.severityMonthly.map((p) => p.medium), color: SEVERITY_COLORS.Medium }, { label: 'Low', data: d.severityMonthly.map((p) => p.low), color: SEVERITY_COLORS.Low }]);
    charts.doughnut(cat.c, Object.keys(d.byCategory), Object.values(d.byCategory));
  }

  if (query.road) await detail(); else await overview();
  return () => charts.destroy();
}
