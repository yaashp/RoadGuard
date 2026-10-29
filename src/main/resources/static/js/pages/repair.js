// Public repair-proof page for one resolved hazard, and the "Fixed recently" feed.
import { api } from '../api.js';
import { h, mount, pageHeader, errorState, emptyState, skeleton, severityBadge, statusBadge } from '../ui.js';
import { fmtDate, fmtDateTime, timeAgo } from '../format.js';

export async function repairPage({ container, params, isCurrent }) {
  mount(container, skeleton(6));
  let r;
  try { r = await api.get('/api/repairs/' + encodeURIComponent(params.id)); }
  catch (e) { if (isCurrent()) mount(container, h('a', { href: '/repairs' }, '← Fixed recently'), e.status === 404 ? emptyState('No repair record', 'This reference does not exist, or the report has not reached a resolved status.', h('a', { class: 'btn', href: '/repairs' }, 'See fixed hazards')) : errorState(e, () => location.reload())); return; }
  if (!isCurrent()) return;
  const photo = (kind, label) => h('figure', { class: 'proof-fig' },
    h('img', { loading: 'lazy', decoding: 'async',  class: 'detail-photo', src: '/api/hazards/' + r.hazardId + '/' + (kind === 'after' ? 'resolution-photo' : 'photo'), alt: label + ' photo of the hazard', loading: 'lazy' }),
    h('figcaption', null, label));
  mount(container,
    h('p', null, h('a', { href: '/repairs' }, '← Fixed recently')),
    pageHeader(r.hazardType + ' · ' + (r.roadName || 'Unnamed road'), 'Reference ' + r.reportId),
    h('div', { class: 'row-badges' }, statusBadge(r.status), severityBadge(r.severity)),
    h('div', { class: 'card' }, h('h2', null, 'Before and after'),
      h('div', { class: 'proof-grid' },
        r.hasBeforePhoto ? photo('before', 'Reported (before)') : h('div', { class: 'hint' }, 'No before photo was attached.'),
        r.hasAfterPhoto ? photo('after', 'Repair (after)') : h('div', { class: 'hint' }, 'No after photo has been added by staff yet.'))),
    h('div', { class: 'grid two', style: 'margin-top:16px' },
      h('div', { class: 'card' }, h('h3', null, 'Dates'),
        h('dl', { class: 'kv' },
          h('dt', null, 'Reported'), h('dd', null, fmtDateTime(r.reportedAt)),
          h('dt', null, 'Resolved'), h('dd', null, r.resolvedAt ? fmtDateTime(r.resolvedAt) : 'Not recorded as resolved'),
          h('dt', null, 'Time taken'), h('dd', null, r.daysToResolve != null ? r.daysToResolve + ' day' + (r.daysToResolve === 1 ? '' : 's') : '-'),
          h('dt', null, 'Location'), h('dd', null, (r.roadName || 'Unnamed road') + ' · ', h('span', { class: 'tech' }, r.approxLat + ', ' + r.approxLng)))),
      h('div', { class: 'card' }, h('h3', null, 'Recorded response'), r.authorityResponse
        ? h('div', null, h('p', null, r.authorityResponse), h('p', { class: 'hint' }, 'Recorded ' + fmtDate(r.authorityResponseAt)))
        : h('p', { class: 'muted' }, 'No authority response has been recorded for this report.'))),
    r.reopenedAfterResolution ? h('div', { class: 'banner warn', style: 'margin-top:16px' }, 'This hazard was marked resolved and later reopened.') : null,
    h('div', { class: 'card', style: 'margin-top:16px' }, h('h2', null, 'Progress'),
      h('ol', { class: 'mini-timeline' }, r.timeline.map((t) => h('li', null, h('span', null, t.stage), h('span', { class: 'when' }, fmtDateTime(t.at)))))),
    h('div', { class: 'banner info small', style: 'margin-top:16px' }, r.disclaimer));
}

export async function fixedFeedPage({ container, isCurrent }) {
  mount(container, pageHeader('Fixed recently', 'Hazards that RoadGuard staff have marked resolved, most recent first'), skeleton(4));
  try {
    const list = await api.get('/api/repairs/fixed', { limit: 30 });
    if (!isCurrent()) return;
    mount(container, pageHeader('Fixed recently', 'Hazards that RoadGuard staff have marked resolved, most recent first. Only real reports appear here.'),
      list.length ? h('div', { class: 'fixed-grid' }, list.map((c) => h('a', { class: 'card fixed-card', href: '/repair/' + encodeURIComponent(c.reportId) },
        h('div', { class: 'row-badges' }, statusBadge('Resolved'), c.hasAfterPhoto ? h('span', { class: 'badge' }, 'After photo') : null),
        h('h3', null, c.hazardType), h('div', { class: 'muted' }, c.roadName || 'Unnamed road'),
        h('div', { class: 'hint tech' }, c.reportId + ' · resolved ' + timeAgo(c.resolvedAt))))) : emptyState('Nothing marked fixed yet', 'When staff mark a hazard resolved, it appears here with its before and after photos.'));
  } catch (e) { if (isCurrent()) mount(container, errorState(e, () => location.reload())); }
}
