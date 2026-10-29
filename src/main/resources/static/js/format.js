// Formatting helpers (dates, distances, durations).

const dtf = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const dtfTime = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

export const fmtDate = (iso) => (iso ? dtf.format(new Date(iso)) : '—');
export const fmtDateTime = (iso) => (iso ? dtfTime.format(new Date(iso)) : '—');

export function timeAgo(iso) {
  if (!iso) return '';
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return Math.floor(s / 60) + ' min ago';
  if (s < 86400) return Math.floor(s / 3600) + ' h ago';
  if (s < 86400 * 30) return Math.floor(s / 86400) + ' d ago';
  return fmtDate(iso);
}

export function fmtDistance(m) {
  if (m == null) return '—';
  return m >= 1000 ? (m / 1000).toFixed(1) + ' km' : Math.round(m) + ' m';
}

export function fmtDuration(sec) {
  if (sec == null) return '—';
  const min = Math.round(sec / 60);
  if (min < 60) return min + ' min';
  return Math.floor(min / 60) + ' h ' + (min % 60) + ' min';
}

export const fmtNum = (n) => (n == null ? '—' : new Intl.NumberFormat().format(n));
export const fmtCoord = (n) => Number(n).toFixed(5);
export const pct = (n) => (n == null ? '—' : n + '%');
