// Chart.js wrappers using the palette of the site, theme-aware.
const PALETTE = ['#E65100', '#2E403B', '#4A5D57', '#F29B5B', '#7F948B', '#B53F00', '#A9BBB2', '#1E5A3C', '#C9A227', '#6E8A7D'];
export const STATUS_COLORS = { Submitted: '#A9BBB2', 'Under Review': '#7F948B', 'Action Planned': '#4A5D57', 'Repair in Progress': '#E65100', Resolved: '#2E403B', Rejected: '#C7D3CC', Duplicate: '#D9E2DD' };
export const SEVERITY_COLORS = { High: '#E65100', Medium: '#F2B263', Low: '#7F948B' };

function css(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

function base(extra = {}) {
  const ink = css('--muted', '#4A5D57'), grid = css('--line', '#D1DDD6');
  return {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { labels: { color: ink, boxWidth: 12 } }, tooltip: { padding: 10 } },
    scales: {}, ...extra, _ink: ink, _grid: grid,
  };
}

function axes(ink, grid, extra = {}) {
  return { x: { ticks: { color: ink }, grid: { display: false }, ...(extra.x || {}) }, y: { beginAtZero: true, ticks: { color: ink, precision: 0 }, grid: { color: grid }, ...(extra.y || {}) } };
}

export class ChartSet {
  constructor() { this.charts = []; }
  add(canvas, config) {
    if (!window.Chart) return null;
    const c = new window.Chart(canvas, config);
    this.charts.push(c);
    return c;
  }
  destroy() { this.charts.forEach((c) => { try { c.destroy(); } catch (e) { /* already gone */ } }); this.charts = []; }

  doughnut(canvas, labels, values, colors) {
    const o = base();
    return this.add(canvas, { type: 'doughnut', data: { labels, datasets: [{ data: values, backgroundColor: colors || labels.map((_, i) => PALETTE[i % PALETTE.length]), borderWidth: 2, borderColor: css('--surface', '#FFFFFF') }] }, options: { responsive: true, maintainAspectRatio: false, cutout: '62%', plugins: { legend: { position: 'bottom', labels: o.plugins.legend.labels } } } });
  }

  bar(canvas, labels, values, { color = '#2E403B', horizontal = false, label = 'Count' } = {}) {
    const o = base();
    return this.add(canvas, { type: 'bar', data: { labels, datasets: [{ label, data: values, backgroundColor: color, borderRadius: 6, maxBarThickness: 36 }] },
      options: { responsive: true, maintainAspectRatio: false, indexAxis: horizontal ? 'y' : 'x', plugins: { legend: { display: false } }, scales: axes(o._ink, o._grid, horizontal ? { x: { beginAtZero: true, grid: { color: o._grid }, ticks: { color: o._ink, precision: 0 } }, y: { grid: { display: false }, ticks: { color: o._ink } } } : {}) } });
  }

  /** series: [{label, data, color}] */
  line(canvas, labels, series) {
    const o = base();
    return this.add(canvas, { type: 'line', data: { labels, datasets: series.map((s) => ({ label: s.label, data: s.data, borderColor: s.color, backgroundColor: s.color + '22', tension: 0.3, fill: true, pointRadius: 3 })) },
      options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false }, plugins: { legend: { labels: o.plugins.legend.labels } }, scales: axes(o._ink, o._grid) } });
  }

  stacked(canvas, labels, series) {
    const o = base();
    return this.add(canvas, { type: 'bar', data: { labels, datasets: series.map((s) => ({ label: s.label, data: s.data, backgroundColor: s.color, borderRadius: 4 })) },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { labels: o.plugins.legend.labels } }, scales: { x: { stacked: true, ticks: { color: o._ink }, grid: { display: false } }, y: { stacked: true, beginAtZero: true, ticks: { color: o._ink, precision: 0 }, grid: { color: o._grid } } } } });
  }
}
