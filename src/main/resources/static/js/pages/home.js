// Home: what RoadGuard is, the main actions, headline numbers and the latest reports. Full charts live on the Dashboard page.
import { api } from '../api.js';
import { state, includeSample, setIncludeSample, isAdmin } from '../state.js';
import { h, icon, mount, skeleton, skeletonCards, errorState, emptyState, severityBadge, statusBadge, sampleBadge } from '../ui.js';
import { fmtNum, timeAgo } from '../format.js';
import { t } from '../i18n/index.js';

export async function homePage({ container, isCurrent }) {
  // Background video of traffic (stock footage). Muted so autoplay is allowed; autoplay is skipped when the visitor asks for reduced motion.
  const reduce = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const video = h('video', { class: 'hero-video', muted: true, loop: true, playsinline: true, preload: 'metadata', autoplay: !reduce, 'aria-hidden': 'true', tabindex: '-1' },
    h('source', { src: '/media/highway-traffic.mp4', type: 'video/mp4' }));
  video.muted = true;   // set the property as well as the attribute: browsers check the property for autoplay
  const hero = h('section', { class: 'hero', 'aria-label': 'Background video of highway traffic' },
    video,
    h('div', { class: 'hero-note' }, t('home.heroNote')),
    h('div', { class: 'hero-panel' },
      h('p', { class: 'hero-kicker' }, 'RoadGuard'),
      h('h1', null, t('home.heroTitle')),
      h('p', null, t('home.heroBody')),
      h('div', { class: 'btn-row' },
        h('a', { class: 'btn primary', href: '/report' }, icon('plus', 16), t('nav.reportCta')),
        h('a', { class: 'btn', href: '/map' }, icon('map', 16), t('home.exploreMap')),
        isAdmin() ? h('a', { class: 'btn primary', href: '/admin' }, icon('gear', 16), t('nav.adminConsole')) : null)));
  const body = h('div');
  mount(container, hero, body);

  async function load() {
    if (!state.me) {   // headline figures are personal: guests see the explanation only
      mount(body, emptyState(t('home.guestTitle'), t('home.guestBody'), h('a', { class: 'btn primary', href: '/login' }, t('nav.login'))));
      return;
    }
    mount(body, skeletonCards(4), skeleton(5));
    try {
      const d = await api.get('/api/dashboard', { includeSample: includeSample() });
      if (isCurrent()) draw(d);
    } catch (e) { if (isCurrent()) mount(body, errorState(e, load)); }
  }

  function stat(label, value, sub, cls = '') {
    return h('div', { class: 'card stat ' + cls }, h('div', { class: 'label' }, label), h('div', { class: 'value' }, value), sub ? h('div', { class: 'sub' }, sub) : null);
  }

  function draw(d) {
    const tot = d.totals;
    const banner = d.sampleComplaintCount > 0 ? h('div', { class: 'banner' },
      h('span', null, icon('info', 16), ' ', d.includesSampleData ? 'Figures include ' + fmtNum(d.sampleComplaintCount) + ' labelled sample records next to ' + fmtNum(d.userComplaintCount) + ' real report(s).' : 'Sample data is hidden: showing only ' + fmtNum(d.userComplaintCount) + ' real report(s).'),
      h('button', { class: 'btn small', type: 'button', onclick: async () => { await setIncludeSample(!includeSample()); load(); } }, d.includesSampleData ? 'Hide sample data' : 'Show sample data')) : null;

    const steps = h('div', { class: 'grid three how' },
      [['1', 'Report', 'Choose the hazard type, add a photo and drop a pin. Possible duplicates are shown before you submit.'],
       ['2', 'Verify', 'Other people confirm that a hazard is still there or gone. Their answers help reviewers; they never change the official status.'],
       ['3', 'Follow the repair', 'Staff move a report from Under Review to Action Planned, Repair in Progress and Resolved, with notes and optional repair photos.']]
        .map(([n, title, text]) => h('div', { class: 'card' }, h('div', { class: 'step-num tech' }, n), h('h3', null, title), h('p', { class: 'muted' }, text))));

    const recent = d.recentReports.length ? h('div', { class: 'table-wrap' }, h('table', null,
      h('thead', null, h('tr', null, ['Report', 'Hazard', 'Road', 'Severity', 'Status', 'Reported'].map((c) => h('th', null, c)))),
      h('tbody', null, d.recentReports.slice(0, 6).map((r) => h('tr', null,
        h('td', null, h('span', { class: 'ref' }, r.complaintId), r.sample ? [' ', sampleBadge()] : null),
        h('td', null, r.category), h('td', null, r.roadName), h('td', null, severityBadge(r.severity)), h('td', null, statusBadge(r.status)), h('td', null, timeAgo(r.createdAt))))))) : emptyState('No reports yet', 'Be the first to report a hazard.', h('a', { class: 'btn primary', href: '/report' }, 'Report a Hazard'));

    mount(body, banner,
      h('div', { class: 'stat-grid' },
        stat(t('home.statHazards'), fmtNum(tot.totalReportedIssues), 'excluding rejected and duplicate reports'),
        stat(t('home.statOpen'), fmtNum(tot.openComplaints), 'being reviewed or repaired', 'warn'),
        stat(t('home.statResolved'), fmtNum(tot.resolved), 'marked resolved by staff', 'ok'),
        stat(t('home.statHigh'), fmtNum(tot.highSeverityOpen), 'rated High by reporters or staff', 'danger')),
      h('h2', null, t('home.howItWorks')), steps,
      h('div', { class: 'card-head', style: 'margin-top:24px' }, h('h2', null, t('home.latestReports')), h('a', { class: 'btn small', href: '/dashboard' }, t('home.openDashboard'))), recent,
      h('p', { class: 'hint', style: 'margin-top:14px' }, 'RoadGuard is a community platform. Submitting a report here does not send it to any government authority.'));
  }
  await load();
}
