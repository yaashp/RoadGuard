// Administrator interface: overview, complaint management with timeline, road issue management.
// Every action here is also enforced on the server; hiding this page from normal users is only a convenience.
import { api } from '../api.js';
import { state, includeSample, setIncludeSample } from '../state.js';
import { h, icon, mount, skeleton, skeletonCards, errorState, emptyState, severityBadge, statusBadge, sampleBadge, pageHeader, debounce, field, toast, confirmDialog, modal, showFieldErrors } from '../ui.js';
import { ChartSet, STATUS_COLORS, SEVERITY_COLORS } from '../charts.js';
import { fmtDate, fmtDateTime, fmtNum, timeAgo, pct, fmtCoord } from '../format.js';
import { timelineList, stepper } from './complaints.js';
import { createMap, issueMarker, pickMarker, mapsAvailable, raw, fit } from '../maps.js';

// One line of text describing a road needing attention, e.g. "3 open · 1 high".
function roadSummary(r) { return r.openIssues + ' open' + (r.highSeverity ? ' · ' + r.highSeverity + ' high' : ''); }

export async function adminPage({ container, query, isCurrent }) {
  const meta = state.meta;
  const charts = new ChartSet();
  let tab = ['overview', 'queue', 'analytics', 'complaints', 'roads', 'audit'].includes(query.tab) ? query.tab : (query.complaint ? 'complaints' : 'overview');
  const body = h('div');
  const tabsEl = h('div', { class: 'tabs', role: 'tablist' });
  let refreshComplaints = () => {};

  function drawTabs() {
    mount(tabsEl, [['overview', 'Overview'], ['queue', 'Priority queue'], ['analytics', 'Analytics'], ['complaints', 'Complaints'], ['roads', 'Road issues'], ['audit', 'Audit trail']].map(([k, l]) => h('button', { class: 'tab' + (tab === k ? ' active' : ''), role: 'tab', 'aria-selected': String(tab === k), type: 'button', onclick: () => { tab = k; drawTabs(); show(); } }, l)));
  }
  function show() { charts.destroy(); if (tab === 'overview') overview(); else if (tab === 'queue') queue(); else if (tab === 'complaints') complaints(); else if (tab === 'analytics') analytics(); else if (tab === 'audit') audit(); else roads(); }

  // ------------------------------------------------------------------ overview: operational snapshot (charts live in Analytics)
  async function overview() {
    mount(body, skeletonCards(6), skeleton(5));
    try {
      const o = await api.get('/api/admin/overview', { includeSample: includeSample() });
      if (!isCurrent() || tab !== 'overview') return;
      const d = o.summary, t = d.totals;
      const sampleToggle = h('input', { type: 'checkbox', checked: includeSample() });
      sampleToggle.addEventListener('change', async () => { await setIncludeSample(sampleToggle.checked); overview(); });
      const latest = d.recentReports.slice(0, 5);
      mount(body,
        h('div', { class: 'banner' }, h('span', null, d.sampleComplaintCount + ' sample and ' + d.userComplaintCount + ' real complaint(s) in the database.'), h('label', { class: 'toggle' }, sampleToggle, 'Include sample data')),
        h('div', { class: 'stat-grid' },
          h('div', { class: 'card stat warn' }, h('div', { class: 'label' }, 'Awaiting review'), h('div', { class: 'value' }, fmtNum(o.awaitingReview)), h('div', { class: 'sub' }, 'status \"Submitted\" (all data)')),
          h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Total complaints'), h('div', { class: 'value' }, fmtNum(t.totalComplaints))),
          h('div', { class: 'card stat warn' }, h('div', { class: 'label' }, 'Open'), h('div', { class: 'value' }, fmtNum(t.openComplaints))),
          h('div', { class: 'card stat ok' }, h('div', { class: 'label' }, 'Resolved'), h('div', { class: 'value' }, fmtNum(t.resolved)), h('div', { class: 'sub' }, 'rate ' + pct(d.conditions.resolutionRatePercent))),
          h('div', { class: 'card stat danger' }, h('div', { class: 'label' }, 'High-severity open'), h('div', { class: 'value' }, fmtNum(t.highSeverityOpen))),
          h('div', { class: 'card stat' }, h('div', { class: 'label' }, 'Registered users'), h('div', { class: 'value' }, fmtNum(o.registeredUsers)))),
        h('div', { class: 'card', style: 'margin-top:16px' },
          h('div', { class: 'card-head' }, h('h3', null, 'Latest 5 submissions'), h('button', { class: 'btn small', type: 'button', onclick: () => { tab = 'complaints'; drawTabs(); show(); } }, 'All complaints')),
          latest.length ? h('div', { class: 'table-wrap' }, h('table', null, h('thead', null, h('tr', null, ['Reference', 'Category', 'Road', 'Severity', 'Status', 'When'].map((c) => h('th', null, c)))),
            h('tbody', null, latest.map((r) => h('tr', null, h('td', null, h('span', { class: 'ref' }, r.complaintId), r.sample ? [' ', sampleBadge()] : null), h('td', null, r.category), h('td', null, r.roadName), h('td', null, severityBadge(r.severity)), h('td', null, statusBadge(r.status)), h('td', null, timeAgo(r.createdAt))))))) : emptyState('No submissions yet')));
    } catch (e) { if (isCurrent()) mount(body, errorState(e, overview)); }
  }

  // ------------------------------------------------------------------ analytics: every chart and trend metric
  async function analytics() {
    mount(body, skeletonCards(6), skeleton(5));
    try {
      const o = await api.get('/api/admin/overview', { includeSample: includeSample() });
      if (!isCurrent() || tab !== 'analytics') return;
      const d = o.summary, t = d.totals;
      const sampleToggle = h('input', { type: 'checkbox', checked: includeSample() });
      sampleToggle.addEventListener('change', async () => { await setIncludeSample(sampleToggle.checked); analytics(); });
      const banner = h('div', { class: 'banner' }, h('span', null, fmtNum(t.totalComplaints) + ' complaint(s) in the current data set.'), h('label', { class: 'toggle' }, sampleToggle, 'Include sample data'));
      if (t.totalComplaints === 0) { mount(body, banner, emptyState('No analytics yet', 'Charts appear once complaints have been submitted.')); return; }

      const cv = (title, id) => { const c = h('canvas', { id, role: 'img', 'aria-label': title }); return { c, card: h('div', { class: 'card' }, h('h3', null, title), h('div', { class: 'chart-box' }, c)) }; };
      const st = cv('Complaint status breakdown', 'an-status'), ct = cv('Issue categories distribution', 'an-cat'), wk = cv('Reports per week (last 8 weeks)', 'an-week'), mo = cv('Reports per month (last 6 months)', 'an-month'), sv = cv('Severity by month', 'an-sev');
      const attention = d.conditions.roadsNeedingAttention;
      const attentionCard = h('div', { class: 'card' }, h('h3', null, 'Roads needing attention'), attention.length
        ? h('ul', { class: 'list-plain' }, attention.map((r) => h('li', null, h('a', { href: '/history?road=' + encodeURIComponent(r.road) }, r.road), h('span', { class: 'muted small' }, roadSummary(r)))))
        : emptyState('Nothing open', 'No road currently has open complaints.'));

      mount(body, banner,
        h('div', { class: 'grid two' }, st.card, ct.card),
        h('div', { class: 'grid two', style: 'margin-top:16px' }, wk.card, mo.card),
        h('div', { class: 'grid side', style: 'margin-top:16px' }, sv.card, attentionCard));

      const labels = Object.keys(d.byStatus);
      charts.doughnut(st.c, labels, labels.map((l) => d.byStatus[l]), labels.map((l) => STATUS_COLORS[l]));
      charts.bar(ct.c, Object.keys(d.byCategory), Object.values(d.byCategory), { horizontal: true, color: '#E65100', label: 'Complaints' });
      charts.line(wk.c, d.weekly.map((p) => p.label), [
        { label: 'Reported', data: d.weekly.map((p) => p.reported), color: '#E65100' },
        { label: 'Resolved', data: d.weekly.map((p) => p.resolved), color: '#2E403B' }]);
      charts.bar(mo.c, d.monthly.map((p) => p.label), d.monthly.map((p) => p.reported), { color: '#4A5D57', label: 'Reported' });
      charts.stacked(sv.c, d.severityMonthly.map((p) => p.label), [
        { label: 'High', data: d.severityMonthly.map((p) => p.high), color: SEVERITY_COLORS.High },
        { label: 'Medium', data: d.severityMonthly.map((p) => p.medium), color: SEVERITY_COLORS.Medium },
        { label: 'Low', data: d.severityMonthly.map((p) => p.low), color: SEVERITY_COLORS.Low }]);
    } catch (e) { if (isCurrent()) mount(body, errorState(e, analytics)); }
  }

  // ------------------------------------------------------------------ complaints
  function complaints() {
    let page = 0;
    const sel = (opts, all) => h('select', null, h('option', { value: '' }, all), opts.map((o) => h('option', { value: o }, o)));
    const q = h('input', { type: 'search', placeholder: 'Reference, road, description…' });
    const status = sel(meta.statuses, 'All'), severity = sel(meta.severities, 'All'), cat = sel(meta.categories.map((c) => c.label), 'All');
    const from = h('input', { type: 'date' }), to = h('input', { type: 'date' });
    const sort = h('select', null, h('option', { value: 'newest' }, 'Newest first'), h('option', { value: 'oldest' }, 'Oldest first'));
    const sampleBox = h('input', { type: 'checkbox', checked: includeSample() });
    const table = h('div'); const pager = h('div', { class: 'pager' });

    async function load() {
      mount(table, skeleton(6));
      try {
        const r = await api.get('/api/admin/complaints', { q: q.value.trim(), status: status.value, severity: severity.value, category: cat.value, from: from.value, to: to.value, sort: sort.value, includeSample: sampleBox.checked, page, size: 12 });
        if (!isCurrent() || tab !== 'complaints') return;
        mount(table, r.items.length ? h('div', { class: 'table-wrap' }, h('table', null,
          h('thead', null, h('tr', null, ['Reference', 'Category', 'Road', 'Severity', 'Status', 'Reporter', 'Reported'].map((c) => h('th', null, c)))),
          h('tbody', null, r.items.map((c) => h('tr', { class: 'clickable', tabindex: '0', onclick: () => openComplaint(c.id), onkeydown: (e) => { if (e.key === 'Enter') openComplaint(c.id); } },
            h('td', null, h('span', { class: 'ref' }, c.complaintId), c.sample ? [' ', sampleBadge()] : null), h('td', null, c.issueType), h('td', null, c.roadName), h('td', null, severityBadge(c.severity)), h('td', null, statusBadge(c.status)), h('td', null, c.userName), h('td', null, fmtDate(c.createdAt))))))) : emptyState('No complaints match', 'Try different filters.'));
        mount(pager, h('span', { class: 'muted small' }, r.totalItems + ' complaint(s) · page ' + (r.totalPages ? r.page + 1 : 0) + ' of ' + r.totalPages),
          h('button', { class: 'btn small', type: 'button', disabled: r.page <= 0, onclick: () => { page--; load(); } }, '‹ Previous'), h('button', { class: 'btn small', type: 'button', disabled: r.page + 1 >= r.totalPages, onclick: () => { page++; load(); } }, 'Next ›'));
      } catch (e) { if (isCurrent()) mount(table, errorState(e, load)); }
    }
    refreshComplaints = load;
    const reset = () => { page = 0; load(); };
    q.addEventListener('input', debounce(reset, 350));
    [status, severity, cat, from, to, sort].forEach((el) => el.addEventListener('change', reset));
    sampleBox.addEventListener('change', async () => { await setIncludeSample(sampleBox.checked); reset(); });
    mount(body, h('div', { class: 'card', style: 'margin-bottom:16px' }, h('div', { class: 'filters' }, field('Area, road or reference', q), field('Status', status), field('Severity', severity), field('Category', cat), field('From', from), field('To', to), field('Sort', sort),
      h('label', { class: 'toggle' }, sampleBox, 'Include sample data'))), table, pager);
    load();
  }

  async function openComplaint(id) {
    let d;
    try { d = await api.get('/api/admin/complaints/' + id); } catch (e) { toast(e.message, 'error'); return; }
    const content = h('div'); const dlg = modal('Complaint ' + d.complaint.complaintId, content, { wide: true });
    let map = null;
    const closeOrig = dlg.close;
    dlg.close = () => { if (map) map.remove(); closeOrig(); };
    dlg.card.querySelector('.dialog-actions .btn').addEventListener('click', () => { if (map) map.remove(); });

    function draw(d) {
      const c = d.complaint;
      if (map) { map.remove(); map = null; }
      const mapEl = h('div', { class: 'map-box small' });
      const nextOptions = c.allowedNextStatuses.filter((x) => x !== 'Duplicate');
      const statusSel = h('select', { name: 'status' }, nextOptions.map((s) => h('option', { value: s }, s)));
      const note = h('textarea', { name: 'note', maxlength: '500', placeholder: 'Note for the timeline (required when rejecting)', style: 'min-height:70px' });
      const sevSel = h('select', { name: 'severity' }, meta.severities.filter((s) => s !== c.severity).map((s) => h('option', { value: s }, s)));
      const sevNote = h('input', { name: 'note', type: 'text', maxlength: '500', placeholder: 'Reason (optional)' });
      const statusForm = h('div', null, field('Move to', statusSel), field('Note', note), h('button', { class: 'btn primary', type: 'button', disabled: !nextOptions.length, onclick: async () => {
        const to = statusSel.value;
        if (!(await confirmDialog({ title: 'Change status?', message: 'Move ' + c.complaintId + ' from "' + c.status + '" to "' + to + '"? The reporter will be notified.', confirmText: 'Change to ' + to, danger: to === 'Rejected' }))) return;
        try { await api.patch('/api/admin/complaints/' + c.id + '/status', { status: to, note: note.value }); toast('Status changed to ' + to + '.', 'success'); draw(await api.get('/api/admin/complaints/' + c.id)); refreshComplaints(); }
        catch (e) { if (!showFieldErrors(statusForm, e.fieldErrors)) toast(e.message, 'error'); }
      } }, 'Update status'));
      const sevForm = h('div', null, field('Change severity to', sevSel), field('Reason', sevNote), h('button', { class: 'btn', type: 'button', onclick: async () => {
        if (!(await confirmDialog({ title: 'Change severity?', message: 'Change severity of ' + c.complaintId + ' from ' + c.severity + ' to ' + sevSel.value + '?', confirmText: 'Change severity' }))) return;
        try { await api.patch('/api/admin/complaints/' + c.id + '/severity', { severity: sevSel.value, note: sevNote.value }); toast('Severity updated.', 'success'); draw(await api.get('/api/admin/complaints/' + c.id)); refreshComplaints(); }
        catch (e) { toast(e.message, 'error'); }
      } }, 'Update severity'));
      const dupRef = h('input', { name: 'duplicateOf', type: 'text', placeholder: 'e.g. RG-2026-00431' });
      const dupNote = h('input', { name: 'note', type: 'text', maxlength: '500', placeholder: 'Why is it the same hazard?' });
      const dupForm = h('div', null, field('Original report', dupRef), field('Explanation', dupNote), h('button', { class: 'btn', type: 'button', disabled: !c.allowedNextStatuses.includes('Duplicate'), onclick: async () => {
        if (!(await confirmDialog({ title: 'Mark as duplicate?', message: c.complaintId + ' will be hidden from the public map and linked to ' + dupRef.value + '. It is kept, and you can undo this.', confirmText: 'Mark as duplicate' }))) return;
        try { await api.post('/api/admin/complaints/' + c.id + '/duplicate', { duplicateOf: dupRef.value, note: dupNote.value }); toast('Marked as duplicate.', 'success'); draw(await api.get('/api/admin/complaints/' + c.id)); refreshComplaints(); }
        catch (e) { if (!showFieldErrors(dupForm, e.fieldErrors)) toast(e.message, 'error'); }
      } }, 'Mark as duplicate'));
      const evFile = h('input', { type: 'file', accept: 'image/jpeg,image/png,image/webp', 'aria-label': 'Repair photo' });
      const canEvidence = ['Repair in Progress', 'Resolved'].includes(c.status);
      const evForm = h('div', null, field('After photo (optional)', evFile, { hint: canEvidence ? 'Shown publicly as repair evidence. The original photo is kept.' : 'Available once the repair is in progress or resolved.' }),
        h('button', { class: 'btn', type: 'button', disabled: !canEvidence, onclick: async () => {
          if (!evFile.files[0]) { toast('Choose a photo first.', 'info'); return; }
          const f = new FormData(); f.append('image', evFile.files[0]);
          try { await api.upload('/api/admin/complaints/' + c.id + '/resolution-photo', f); toast('Repair photo added.', 'success'); draw(await api.get('/api/admin/complaints/' + c.id)); refreshComplaints(); }
          catch (e) { toast((e.fieldErrors && e.fieldErrors.image) || e.message, 'error'); }
        } }, 'Upload repair photo'));
      mount(content, h('div', { style: 'margin-bottom:12px' }, statusBadge(c.status), ' ', severityBadge(c.severity), ' ', c.sample ? sampleBadge() : null), stepper(c.status),
        h('div', { class: 'grid two', style: 'margin-top:12px' },
          h('div', { class: 'grid' },
            h('div', null, h('h3', null, 'Details'), h('dl', { class: 'kv' }, h('dt', null, 'Category'), h('dd', null, c.issueType), h('dt', null, 'Road'), h('dd', null, c.roadName), h('dt', null, 'Address'), h('dd', null, c.address || '—'),
              h('dt', null, 'Coordinates'), h('dd', null, fmtCoord(c.lat) + ', ' + fmtCoord(c.lng)), h('dt', null, 'Reported'), h('dd', null, fmtDateTime(c.createdAt)), h('dt', null, 'Description'), h('dd', null, c.description || '—'),
              h('dt', null, 'Reporter'), h('dd', null, c.userName + ' · ' + d.reporterEmail + (d.reporterPhone ? ' · ' + d.reporterPhone : '')))),
            c.hasImage ? h('img', { loading: 'lazy', decoding: 'async',  class: 'detail-photo', src: c.imageUrl, alt: 'Photo for ' + c.complaintId }) : h('div', { class: 'hint' }, 'No photo attached.'),
            c.hasResolutionPhoto ? h('div', null, h('h3', null, 'Repair evidence'), h('img', { loading: 'lazy', decoding: 'async',  class: 'detail-photo', src: c.resolutionPhotoUrl, alt: 'Repair evidence for ' + c.complaintId })) : null, mapEl),
          h('div', { class: 'grid' }, h('div', null, h('h3', null, 'Manage status'), statusForm), h('div', null, h('h3', null, 'Severity'), sevForm), h('div', null, h('h3', null, 'Repair evidence'), evForm), h('div', null, h('h3', null, 'Duplicate review'), dupForm), h('div', null, h('h3', null, 'Timeline'), timelineList(d.timeline)))));
      if (mapsAvailable()) { map = createMap(mapEl, meta, { center: [c.lat, c.lng], zoom: 16 }); issueMarker(meta, { type: c.issueType, lat: c.lat, lng: c.lng, severity: c.severity, status: c.status, sample: c.sample }).addTo(map); }
    }
    draw(d);
  }

  // ------------------------------------------------------------------ priority queue, hotspots, community flags
  async function queue() {
    mount(body, skeletonCards(6), skeleton(5));
    let map = null;
    try {
      const r = await api.get('/api/admin/insights', { includeSample: includeSample() });
      if (!isCurrent() || tab !== 'queue') return;
      const c = r.counters;
      const card = (label, value, cls = '') => h('div', { class: 'card stat ' + cls }, h('div', { class: 'label' }, label), h('div', { class: 'value' }, fmtNum(value)));
      const flagText = { POSSIBLY_GONE: 'Several people say it may be gone', REPORTED_STILL_PRESENT: 'Marked resolved, but reported still there' };
      const dismiss = (q) => {
        const note = h('textarea', { name: 'note', maxlength: '500', placeholder: 'What did you check, and what did you decide?', style: 'min-height:80px' });
        const dlg = modal('Dismiss community flag', h('div', null, h('p', { class: 'small muted' }, 'Older community votes stop counting after this. The decision is recorded in the audit trail.'), field('Your decision', note)));
        const ok = h('button', { class: 'btn primary', type: 'button', onclick: async () => {
          try { await api.post('/api/admin/hazards/' + q.pinId + '/dismiss-flag', { note: note.value }); dlg.close(); toast('Flag dismissed.', 'success'); queue(); }
          catch (e) { toast((e.fieldErrors && e.fieldErrors.note) || e.message, 'error'); }
        } }, 'Dismiss flag');
        dlg.card.querySelector('.dialog-actions').prepend(ok);
      };
      const hot = h('div', { class: 'map-box small', role: 'img', 'aria-label': 'Map of hazard hotspots' });
      mount(body,
        h('div', { class: 'banner info' }, icon('info', 16), h('span', null, r.disclaimer)),
        h('div', { class: 'stat-grid' }, card('Total reports', c.totalReports), card('Active hazards', c.activeHazards, 'warn'), card('High priority', c.highPriority, 'danger'), card('Under review', c.underReview), card('Verified resolutions', c.verifiedResolutions, 'ok'), card('Community flags', c.flagged, c.flagged ? 'danger' : '')),
        h('h2', null, 'Priority queue'),
        r.priorityQueue.length ? h('div', { class: 'table-wrap' }, h('table', null,
          h('thead', null, h('tr', null, ['Priority', 'Report', 'Hazard', 'Status', 'Community', 'Age', 'Needs review', ''].map((x) => h('th', null, x)))),
          h('tbody', null, r.priorityQueue.map((q) => h('tr', null,
            h('td', null, severityBadge(q.bucket), ' ', h('span', { class: 'tech' }, q.score), h('details', null, h('summary', { class: 'small' }, 'Why?'), h('ul', { class: 'explain' }, q.factors.map((f) => h('li', null, f))))),
            h('td', null, q.complaintDbId ? h('button', { class: 'link-btn ref', type: 'button', onclick: () => openComplaint(q.complaintDbId) }, q.complaintId) : h('span', { class: 'muted' }, 'Admin pin'), q.sample ? [' ', sampleBadge()] : null),
            h('td', null, q.category, h('div', { class: 'hint' }, (q.road || '') + ' · ' + q.severity)), h('td', null, statusBadge(q.status)),
            h('td', { class: 'tech' }, q.stillThere + ' still · ' + q.gone + ' gone'), h('td', { class: 'tech' }, q.ageDays + ' d'),
            h('td', null, q.flag ? h('span', { class: 'badge sev-medium' }, flagText[q.flag]) : h('span', { class: 'muted' }, '-')),
            h('td', null, q.flag && !q.sample ? h('button', { class: 'btn small', type: 'button', onclick: () => dismiss(q) }, 'Dismiss flag') : null)))))) : emptyState('Nothing to review', 'There are no open hazards in the queue.'),
        h('h2', { style: 'margin-top:24px' }, 'Hotspots'),
        h('p', { class: 'hint' }, 'Areas of about 500 m with at least two open hazards. Based on reported locations only.'),
        r.hotspots.length ? h('div', { class: 'grid side' }, hot, h('div', { class: 'card' }, h('ul', { class: 'list-plain' }, r.hotspots.map((x) => h('li', null, h('span', null, h('strong', null, x.openHazards + ' open'), ' · ' + x.topCategory, h('div', { class: 'hint' }, x.roads.join(', '))), h('span', { class: 'tech' }, x.highSeverity + ' high')))))) : emptyState('No hotspots', 'No area has two or more open hazards.'));
      if (r.hotspots.length && mapsAvailable()) {
        const L = raw();
        map = createMap(hot, meta);
        const layers = r.hotspots.map((x) => L.circle([x.lat, x.lng], { radius: 250 + 60 * x.openHazards, color: '#fb7185', weight: 2, fillColor: '#fb7185', fillOpacity: 0.25 }).addTo(map).bindPopup(x.openHazards + ' open hazards: ' + x.roads.join(', ')));
        fit(map, [L.featureGroup(layers)], 30);
        charts.charts.push({ destroy: () => map.remove() });    // removed when the tab changes
      }
    } catch (e) { if (isCurrent()) mount(body, errorState(e, queue)); }
  }

  async function audit() {
    mount(body, skeleton(6));
    try {
      const rows = await api.get('/api/admin/audit', { limit: 100 });
      if (!isCurrent() || tab !== 'audit') return;
      mount(body, h('p', { class: 'hint' }, 'Every administrative change, newest first. Entries cannot be edited or deleted from the application.'),
        rows.length ? h('div', { class: 'table-wrap' }, h('table', null, h('thead', null, h('tr', null, ['When', 'Administrator', 'Action', 'Report', 'Detail'].map((x) => h('th', null, x)))),
          h('tbody', null, rows.map((a) => h('tr', null, h('td', { class: 'tech' }, fmtDateTime(a.at)), h('td', null, a.admin), h('td', null, a.action), h('td', null, h('span', { class: 'ref' }, a.complaintId)), h('td', null, a.detail)))))) : emptyState('No administrative changes yet'));
    } catch (e) { if (isCurrent()) mount(body, errorState(e, audit)); }
  }

  // ------------------------------------------------------------------ road issues
  async function roads() {
    mount(body, skeleton(6));
    const q = h('input', { type: 'search', placeholder: 'Filter by road or category…' });
    const list = h('div');
    let rows = [];
    function draw() {
      const text = q.value.trim().toLowerCase();
      const shown = rows.filter((r) => !text || (r.roadName + ' ' + r.type).toLowerCase().includes(text));
      mount(list, shown.length ? h('div', { class: 'table-wrap' }, h('table', null,
        h('thead', null, h('tr', null, ['ID', 'Category', 'Road', 'Severity', 'Status', 'Origin', 'Reports', ''].map((c) => h('th', null, c)))),
        h('tbody', null, shown.map((r) => h('tr', null, h('td', null, r.id), h('td', null, r.type), h('td', null, r.roadName), h('td', null, severityBadge(r.severity)), h('td', null, statusBadge(r.status)),
          h('td', null, r.source === 'SAMPLE' ? sampleBadge() : h('span', { class: 'badge' }, r.source === 'ADMIN' ? 'Added by admin' : 'Citizen report'), r.complaintId ? h('div', { class: 'muted small' }, r.complaintId) : null), h('td', { class: 'num' }, r.reportCount),
          h('td', null, h('div', { class: 'btn-row' }, h('button', { class: 'btn small', type: 'button', onclick: () => editor(r) }, icon('edit', 14), 'Edit'), h('button', { class: 'btn small danger', type: 'button', onclick: () => remove(r) }, icon('trash', 14), 'Delete')))))))) : emptyState('No road issues', 'Add one, or wait for citizen reports.'));
    }
    async function load() {
      try { rows = await api.get('/api/admin/road-issues'); if (isCurrent() && tab === 'roads') draw(); } catch (e) { mount(list, errorState(e, load)); }
    }
    async function remove(r) {
      if (!(await confirmDialog({ title: 'Delete road issue?', message: 'Delete the ' + r.type + ' pin on ' + r.roadName + '? Pins that belong to citizen complaints cannot be deleted.', confirmText: 'Delete', danger: true }))) return;
      try { await api.del('/api/admin/road-issues/' + r.id); toast('Road issue deleted.', 'success'); load(); } catch (e) { toast(e.message, 'error'); }
    }
    function editor(r) {
      const linked = r && r.complaintRef != null;
      let marker = null; let map = null;
      let lat = r ? r.lat : null, lng = r ? r.lng : null;
      const type = h('select', { name: 'type' }, meta.categories.map((c) => h('option', { value: c.label, selected: r && r.type === c.label }, c.label)));
      const sev = h('select', { name: 'severity' }, meta.severities.map((s) => h('option', { value: s, selected: r ? r.severity === s : s === 'Medium' }, s)));
      const stat = h('select', { name: 'status', disabled: linked }, meta.statuses.map((s) => h('option', { value: s, selected: r ? r.status === s : s === 'Submitted' }, s)));
      const road = h('input', { name: 'roadName', type: 'text', value: r ? r.roadName : '', maxlength: '120' });
      const coord = h('div', { class: 'hint', 'data-field': 'location' }, lat != null ? fmtCoord(lat) + ', ' + fmtCoord(lng) : 'Click the map to choose the location.');
      const mapEl = h('div', { class: 'map-box small' });
      const form = h('div', null, h('div', { class: 'form-grid' }, field('Category', type), field('Severity', sev), field('Status', stat, { hint: linked ? 'Controlled by the complaint.' : null }), field('Road name', road)),
        linked ? h('p', { class: 'hint' }, 'This pin belongs to complaint ' + r.complaintId + '; its location and status follow the complaint.') : h('div', { class: 'field', 'data-field': 'location' }, h('label', null, 'Location'), mapEl, coord, h('div', { class: 'error-text', role: 'alert' })));
      const dlg = modal(r ? 'Edit road issue' : 'Add road issue', form, { wide: true });
      const save = h('button', { class: 'btn primary', type: 'button', onclick: async () => {
        const payload = { type: type.value, severity: sev.value, roadName: road.value, ...(linked ? {} : { status: stat.value, lat, lng }) };
        try { if (r) await api.put('/api/admin/road-issues/' + r.id, payload); else await api.post('/api/admin/road-issues', payload); if (map) map.remove(); dlg.close(); toast('Road issue saved.', 'success'); load(); }
        catch (e) { if (!showFieldErrors(form, e.fieldErrors)) toast(e.message, 'error'); }
      } }, 'Save');
      dlg.card.querySelector('.dialog-actions').prepend(save);
      dlg.card.querySelector('.dialog-actions .btn:last-child').addEventListener('click', () => { if (map) map.remove(); });
      if (!linked && mapsAvailable()) {
        map = createMap(mapEl, meta, lat != null ? { center: [lat, lng], zoom: 15 } : {});
        const place = (la, ln) => { lat = la; lng = ln; coord.textContent = fmtCoord(la) + ', ' + fmtCoord(ln); if (marker) marker.setLatLng([la, ln]); else marker = pickMarker([la, ln], '!').addTo(map); };
        if (lat != null) place(lat, lng);
        map.on('click', (e) => place(e.latlng.lat, e.latlng.lng));
      }
    }
    q.addEventListener('input', debounce(draw, 200));
    mount(body, h('div', { class: 'btn-row', style: 'margin-bottom:14px;justify-content:space-between' }, h('div', { style: 'flex:1;max-width:340px' }, q), h('button', { class: 'btn primary', type: 'button', onclick: () => editor(null) }, icon('plus', 16), 'Add road issue')),
      h('p', { class: 'hint' }, 'Pins created from citizen complaints follow the complaint; manage those by changing the complaint instead. Sample pins and pins added here can be edited or deleted.'), list);
    await load();
  }

  mount(container, h('div', { class: 'console-head' }, h('div', { class: 'console-kicker' }, 'Staff only · every change is audited'), pageHeader('Administrator console', 'Manage complaints, hazards and statistics')), tabsEl, body);
  drawTabs(); show();
  if (query.complaint) openComplaint(query.complaint);
  return () => charts.destroy();
}
