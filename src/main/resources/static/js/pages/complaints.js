// "My complaints": filterable list and a detail page with progress tracker, photo, map and timeline.
import { api } from '../api.js';
import { state } from '../state.js';
import { h, icon, mount, skeleton, errorState, emptyState, severityBadge, statusBadge, sampleBadge, pageHeader, debounce, toast, field } from '../ui.js';
import { fmtDate, fmtDateTime, timeAgo, fmtCoord } from '../format.js';
import { createMap, issueMarker, mapsAvailable } from '../maps.js';
import { queuedReports, removeQueued, updateQueued, flushQueue } from '../queue.js';

/** Authority loop panel: record which ward or department you believe is responsible, prepare a letter, and see any staff-recorded response. */
export async function authorityPanel(complaintId) {
  const box = h('div', { class: 'card authority' });
  async function draw() {
    mount(box, skeleton(3));
    try {
      const v = await api.get('/api/complaints/' + complaintId + '/authority');
      const name = h('input', { type: 'text', maxlength: '120', value: v.authorityName || '', placeholder: 'e.g. Ward Office, Ward 12 (Roads department)', 'aria-label': 'Responsible ward or department' });
      const save = h('button', { class: 'btn small', type: 'button', onclick: async () => {
        try { await api.put('/api/complaints/' + complaintId + '/authority', { authorityName: name.value.trim() }); toast('Saved.', 'success'); draw(); }
        catch (e) { toast((e.fieldErrors && e.fieldErrors.authorityName) || e.message, 'error'); }
      } }, 'Save');
      const letterBtn = h('button', { class: 'btn small primary', type: 'button', onclick: async () => {
        try {
          const l = await api.get('/api/complaints/' + complaintId + '/letter');
          const ta = h('textarea', { rows: '16', readonly: true, 'aria-label': 'Letter text' }, l.text);
          const copy = h('button', { class: 'btn small', type: 'button', onclick: async () => { try { await navigator.clipboard.writeText(l.text); toast('Letter copied.', 'success'); } catch (e) { ta.select(); toast('Select the text and copy it.', 'info'); } } }, 'Copy text');
          const print = h('button', { class: 'btn small', type: 'button', onclick: () => window.print() }, 'Print');
          const mail = h('a', { class: 'btn small', href: 'mailto:?subject=' + encodeURIComponent('Road hazard ' + l.reportId) + '&body=' + encodeURIComponent(l.text) }, 'Open in e-mail');
          const dlg = modalOpen('Letter to the authority (draft)', h('div', null, h('p', { class: 'banner warn small' }, l.disclaimer), ta,
            h('div', { class: 'dialog-actions' }, copy, print, mail, h('button', { class: 'btn ghost', type: 'button', onclick: () => dlg.close() }, 'Close'))));
          draw();
        } catch (e) { toast(e.message, 'error'); }
      } }, icon('swap', 14), 'Prepare letter');
      mount(box,
        h('h3', null, 'Take it to the authority'),
        h('p', { class: 'hint' }, v.disclaimer),
        h('label', { class: 'label-text' }, 'Responsible ward or department (your best guess)'),
        h('div', { class: 'btn-row' }, h('div', { style: 'flex:1;min-width:200px' }, name), save),
        h('div', { class: 'btn-row', style: 'margin-top:10px' }, letterBtn, v.letterExportedAt ? h('span', { class: 'muted small' }, 'Letter prepared ' + timeAgo(v.letterExportedAt)) : null),
        h('div', { class: 'response-box' }, h('strong', null, 'Authority response'),
          v.authorityResponse ? h('p', null, v.authorityResponse, h('span', { class: 'hint' }, ' · recorded ' + timeAgo(v.authorityResponseAt))) : h('p', { class: 'muted' }, 'None recorded by RoadGuard staff yet.')));
    } catch (e) { mount(box, errorState(e, draw)); }
  }
  await draw();
  return box;
}

/** Plain dialog used by the letter preview (kept local, no new dependency). */
function modalOpen(title, content) {
  const overlay = h('div', { class: 'overlay', role: 'dialog', 'aria-modal': 'true', 'aria-label': title });
  overlay.appendChild(h('div', { class: 'dialog wide' }, h('h3', null, title), content));
  document.body.appendChild(overlay);
  const close = () => overlay.remove();
  overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
  return { close };
}

const FLOW = ['Submitted', 'Under Review', 'Action Planned', 'Repair in Progress', 'Resolved'];
const CLOSED = ['Resolved', 'Rejected', 'Duplicate'];

export function stepper(status) {
  const rejected = status === 'Rejected' || status === 'Duplicate';
  const steps = rejected ? ['Submitted', 'Under Review', status] : FLOW;
  const idx = status === 'Resolved' ? steps.length : steps.indexOf(status);
  return h('ol', { class: 'steps' + (rejected ? ' rejected' : ''), 'aria-label': 'Progress: ' + status }, steps.map((s, i) => h('li', { class: i < idx ? 'done' : i === idx ? 'current' : '', 'aria-current': i === idx ? 'step' : null },
    h('span', { class: 'dot' }, i < idx ? icon('check', 12) : rejected && i === idx ? icon('x', 12) : null), s)));
}

export function timelineList(entries) {
  const label = (t) => {
    if (t.eventType === 'SUBMITTED') return 'Complaint submitted';
    if (t.eventType === 'SEVERITY_CHANGE') return 'Severity changed: ' + t.from + ' → ' + t.to;
    if (t.eventType === 'IMPORTED') return 'Record imported (status: ' + t.to + ')';
    if (t.eventType === 'RESOLUTION_EVIDENCE') return 'Repair photo added';
    if (t.eventType === 'DUPLICATE_MARKED') return 'Marked as a duplicate';
    if (t.eventType === 'FLAG_DISMISSED') return 'Community flag reviewed and dismissed';
    return 'Status changed: ' + t.from + ' → ' + t.to;
  };
  return h('ul', { class: 'timeline' }, entries.map((t) => h('li', { class: t.eventType === 'SEVERITY_CHANGE' ? 'sev' : '' },
    h('strong', null, label(t)), t.note ? h('div', null, t.note) : null, h('div', { class: 'when' }, fmtDateTime(t.at) + ' · ' + t.actor))));
}

function card(c) {
  const open = !CLOSED.includes(c.status);
  return h('a', { class: 'complaint-card ' + (c.status === 'Rejected' ? 'rejected' : open ? 'open' : ''), href: '/complaints/' + c.id },
    h('span', { class: 'bar' }),
    h('div', { class: 'body' }, h('div', null, h('span', { class: 'ref' }, c.complaintId), ' ', c.sample ? sampleBadge() : null),
      h('h3', { style: 'margin:4px 0 0' }, c.issueType + ' · ' + c.roadName),
      h('div', { class: 'meta' }, h('span', null, icon('clock', 14), ' ', fmtDate(c.createdAt)), h('span', null, c.address || ''), c.hasImage ? h('span', null, icon('camera', 14), ' photo') : null)),
    h('div', { class: 'side' }, statusBadge(c.status), severityBadge(c.severity), h('span', { class: 'muted small' }, open ? 'Unresolved' : c.status === 'Resolved' ? 'Resolved' : 'Closed')));
}

export async function complaintsPage({ container, isCurrent }) {
  const meta = state.meta;
  let all = [];
  let chip = 'all';
  const sel = (name, options, label) => field(label, h('select', { name }, h('option', { value: '' }, 'All'), options.map((o) => h('option', { value: o }, o))));
  const q = h('input', { name: 'q', type: 'search', placeholder: 'Reference, road, description…' });
  const status = sel('status', meta.statuses, 'Status'); const severity = sel('severity', meta.severities, 'Severity');
  const cat = sel('category', meta.categories.map((c) => c.label), 'Category');
  const from = h('input', { name: 'from', type: 'date' }); const to = h('input', { name: 'to', type: 'date' });
  const sort = h('select', { name: 'sort' }, h('option', { value: 'newest' }, 'Newest first'), h('option', { value: 'oldest' }, 'Oldest first'));
  const results = h('div', { class: 'complaint-list' });
  const chips = h('div', { class: 'chips' });

  const params = () => ({ q: q.value.trim(), status: status.querySelector('select').value, severity: severity.querySelector('select').value, category: cat.querySelector('select').value, from: from.value, to: to.value, sort: sort.value });

  function drawChips() {
    const open = all.filter((c) => !CLOSED.includes(c.status)).length;
    const resolved = all.filter((c) => c.status === 'Resolved').length;
    const defs = [['all', 'All (' + all.length + ')'], ['open', 'Unresolved (' + open + ')'], ['resolved', 'Resolved (' + resolved + ')'], ['rejected', 'Rejected (' + (all.length - open - resolved) + ')']];
    mount(chips, defs.map(([k, label]) => h('button', { class: 'chip' + (chip === k ? ' active' : ''), type: 'button', onclick: () => { chip = k; drawChips(); drawList(); } }, label)));
  }
  function drawList() {
    const list = all.filter((c) => chip === 'all' || (chip === 'open' && !CLOSED.includes(c.status)) || (chip === 'resolved' && c.status === 'Resolved') || (chip === 'rejected' && c.status === 'Rejected'));
    mount(results, list.length ? list.map(card) : emptyState(all.length ? 'Nothing in this view' : 'No reports found',
      all.length ? 'Try another tab or clear the filters.' : 'You have not reported any road problems that match these filters.', h('a', { class: 'btn primary', href: '/report' }, 'Report a problem')));
  }
  async function load() {
    mount(results, skeleton(5));
    try {
      const data = await api.get('/api/complaints/mine', params());
      if (!isCurrent()) return;
      all = data; drawChips(); drawList();
    } catch (e) { if (isCurrent()) mount(results, errorState(e, load)); }
  }
  const debounced = debounce(load, 350);
  [q, from, to, sort].forEach((el) => el.addEventListener(el === q ? 'input' : 'change', el === q ? debounced : load));
  [status, severity, cat].forEach((f) => f.querySelector('select').addEventListener('change', load));

  const pending = h('div');
  async function drawPending() {
    const items = await queuedReports().catch(() => []);
    mount(pending, items.length ? h('div', { class: 'card pending-card' }, h('h3', null, 'Waiting to upload (' + items.length + ')'),
      h('ul', { class: 'list-plain' }, items.map((it) => h('li', null,
        h('span', null, h('span', { class: 'status-pill ' + (it.state === 'uploading' ? 'uploading' : it.state === 'attention' ? 'attention' : 'saved') }, it.state === 'uploading' ? 'Uploading' : it.state === 'attention' ? 'Needs attention' : 'Saved on this device'),
          ' ', it.fields.issueType, ' · ', (it.fields.roadName || it.fields.address || (Number(it.fields.lat).toFixed(4) + ', ' + Number(it.fields.lng).toFixed(4))), it.message ? h('div', { class: 'hint' }, it.message) : null),
        h('span', { class: 'btn-row' },
          it.state === 'attention' && it.code === 'POSSIBLE_DUPLICATE' ? h('button', { class: 'btn small', type: 'button', onclick: async () => { await updateQueued(it.id, { allowDuplicate: true, state: 'saved', message: '' }); await flushQueue(); load(); } }, 'Submit anyway') : null,
          h('button', { class: 'btn small', type: 'button', onclick: async () => { await updateQueued(it.id, { state: 'saved', message: '' }); const sent = await flushQueue(); if (sent.length) { toast('Submitted ' + sent[0].complaintId + '.', 'success'); load(); } else toast('Still waiting for a connection.', 'info'); } }, 'Retry'),
          h('button', { class: 'btn small danger', type: 'button', onclick: async () => { await removeQueued(it.id); } }, 'Discard'))))))
      : null);
  }
  const onQueue = () => { if (isCurrent()) drawPending(); };
  window.addEventListener('rg-queue', onQueue);
  mount(container, pageHeader('My reports', 'Everything you have reported, with its current progress', h('a', { class: 'btn primary', href: '/report' }, icon('plus', 16), 'Report a hazard')), pending,
    h('div', { class: 'card', style: 'margin-bottom:16px' }, h('div', { class: 'filters' }, field('Search', q), status, severity, cat, field('From', from), field('To', to), field('Sort', sort),
      h('button', { class: 'btn ghost', type: 'button', onclick: () => { [q, from, to].forEach((e) => { e.value = ''; }); [status, severity, cat].forEach((f) => { f.querySelector('select').value = ''; }); sort.value = 'newest'; load(); } }, 'Clear filters'))),
    chips, results);
  await Promise.all([load(), drawPending()]);
  return () => window.removeEventListener('rg-queue', onQueue);
}

export async function complaintDetailPage({ container, params, isCurrent }) {
  mount(container, skeleton(8));
  let data;
  try { data = await api.get('/api/complaints/' + encodeURIComponent(params.id)); } catch (e) {
    mount(container, h('a', { href: '/complaints' }, '← My complaints'), e.status === 404 ? emptyState('Complaint not found', 'It may not exist, or it belongs to another account.', h('a', { class: 'btn', href: '/complaints' }, 'Back to my complaints')) : errorState(e, () => location.reload()));
    return;
  }
  if (!isCurrent()) return;
  const c = data.complaint;
  const mapEl = h('div', { class: 'map-box small', role: 'img', 'aria-label': 'Map showing where the problem was reported' });
  const photoSlot = h('div');
  function drawPhoto(version) {
    mount(photoSlot, c.hasImage ? h('img', { loading: 'lazy', decoding: 'async',  class: 'detail-photo', src: c.imageUrl + '?v=' + version, alt: 'Photo submitted with ' + c.complaintId }) : h('div', { class: 'hint' }, 'No photo was attached.'));
  }
  drawPhoto(0);
  const upload = h('input', { type: 'file', accept: 'image/jpeg,image/png,image/webp', 'aria-label': 'Replace photo' });
  upload.addEventListener('change', async () => {
    const f = upload.files[0]; if (!f) return;
    if (!state.meta.image.allowedTypes.includes(f.type) || f.size > state.meta.image.maxBytes) { toast('Choose a JPEG, PNG or WebP photo under ' + Math.round(state.meta.image.maxBytes / 1048576) + ' MB.', 'error'); upload.value = ''; return; }
    const body = new FormData(); body.append('image', f);
    try { const updated = await api.upload('/api/complaints/' + c.id + '/image', body); c.hasImage = updated.hasImage; c.imageUrl = updated.imageUrl; drawPhoto(Date.now()); toast('Photo updated.', 'success'); }
    catch (e) { toast((e.fieldErrors && e.fieldErrors.image) || e.message, 'error'); }
    upload.value = '';
  });

  mount(container,
    h('p', null, h('a', { href: '/complaints' }, '← My complaints')),
    pageHeader(c.issueType + ' · ' + c.roadName, null, statusBadge(c.status), severityBadge(c.severity), c.sample ? sampleBadge() : null),
    h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h2', null, 'Progress'), h('span', { class: 'ref' }, c.complaintId)), stepper(c.status),
      c.status === 'Rejected' ? h('div', { class: 'banner error', style: 'margin-top:12px' }, 'This complaint was rejected. See the timeline below for the reason.') : null,
      c.status === 'Resolved' ? h('div', { class: 'banner', style: 'margin-top:12px' }, icon('check', 16), ' This hazard has been marked as resolved by staff.') : null,
      c.status === 'Duplicate' ? h('div', { class: 'banner info', style: 'margin-top:12px' }, 'This report was merged into ' + (c.duplicateOf || 'another report') + '. Your report and its history are kept.') : null),
    h('div', { class: 'grid side', style: 'margin-top:16px' },
      h('div', { class: 'grid' },
        h('div', { class: 'card' }, h('h3', null, 'Details'), h('dl', { class: 'kv' },
          h('dt', null, 'Reference'), h('dd', null, c.complaintId), h('dt', null, 'Category'), h('dd', null, c.issueType), h('dt', null, 'Severity'), h('dd', null, severityBadge(c.severity)),
          h('dt', null, 'Status'), h('dd', null, statusBadge(c.status)), h('dt', null, 'Submitted'), h('dd', null, fmtDateTime(c.createdAt) + ' (' + timeAgo(c.createdAt) + ')'),
          c.updatedAt ? [h('dt', null, 'Last update'), h('dd', null, fmtDateTime(c.updatedAt))] : null,
          h('dt', null, 'Road'), h('dd', null, c.roadName), h('dt', null, 'Address'), h('dd', null, c.address || '—'),
          h('dt', null, 'Coordinates'), h('dd', null, fmtCoord(c.lat) + ', ' + fmtCoord(c.lng)), h('dt', null, 'Description'), h('dd', null, c.description || '—'))),
        h('div', { class: 'card' }, h('h3', null, 'Location'), mapEl)),
      h('div', { class: 'grid' },
        h('div', { class: 'card', id: 'authority-slot' }, skeleton(2)),
        h('div', { class: 'card' }, h('h3', null, 'Public repair page'), c.status === 'Resolved' ? h('p', null, h('a', { href: '/repair/' + encodeURIComponent(c.complaintId) }, 'View the repair record (before and after)')) : h('p', { class: 'hint' }, 'A repair record appears here once staff mark this report resolved.')),
        c.hasResolutionPhoto ? h('div', { class: 'card' }, h('h3', null, 'Repair evidence'), h('img', { loading: 'lazy', decoding: 'async',  class: 'detail-photo', src: c.resolutionPhotoUrl, alt: 'Photo of the repair work added by staff' }), h('p', { class: 'hint' }, 'Added by staff. Your original photo is kept separately.')) : null,
        h('div', { class: 'card' }, h('h3', null, 'Photo'), photoSlot, h('div', { class: 'field', style: 'margin-top:10px' }, h('label', null, c.hasImage ? 'Replace photo' : 'Add a photo'), upload)),
        h('div', { class: 'card' }, h('h3', null, 'Status history'), timelineList(data.timeline)))));

  authorityPanel(c.id).then((panel) => { const slot = container.querySelector('#authority-slot'); if (slot && isCurrent()) slot.replaceWith(panel); });
  let map = null;
  if (mapsAvailable()) {
    map = createMap(mapEl, state.meta, { center: [c.lat, c.lng], zoom: 16 });
    issueMarker(state.meta, { type: c.issueType, lat: c.lat, lng: c.lng, severity: c.severity, status: c.status, sample: c.sample }).addTo(map);
  } else mapEl.textContent = 'Map unavailable.';
  return () => { if (map) map.remove(); };
}
