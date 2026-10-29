// Explore Map. Non-admin users see ONLY their own hazard reports (server-scoped with mine=true, and the list is
// filtered the same way). Administrators see every hazard. The map/list view toggle comes from the citizen refresh;
// the list is hidden by default under the map and revealed by "Show All Reports".
import { api } from '../api.js';
import { state, isAdmin, includeSample, setIncludeSample } from '../state.js';
import { h, icon, mount, field, toast, debounce, pageHeader, severityBadge, statusBadge, sampleBadge, errorState, emptyState, skeleton } from '../ui.js';
import { createMap, issueMarker, layerGroup, pickMarker, locate, legend, fit, mapsAvailable, raw, categoryBadge } from '../maps.js';
import { placeSearch } from '../places.js';
import { fmtDateTime, timeAgo, fmtDistance } from '../format.js';
import { navigate } from '../router.js';

const rad = (d) => (d * Math.PI) / 180;
function meters(a, b) {
  const dLat = rad(b.lat - a.lat), dLng = rad(b.lng - a.lng);
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371008.8 * Math.asin(Math.min(1, Math.sqrt(x)));
}
const SEV_RANK = { High: 3, Medium: 2, Low: 1 };

/** One marker per underlying hazard: reports of the same type within 15 m are shown together (nothing is merged in the database). */
export function groupIssues(issues) {
  const groups = [];
  [...issues].sort((a, b) => (SEV_RANK[b.severity] || 0) - (SEV_RANK[a.severity] || 0) || (b.stillThere - a.stillThere)).forEach((i) => {
    const g = groups.find((x) => x.primary.type === i.type && meters(x.primary, i) <= 15 && x.primary.sample === i.sample);
    if (g) g.others.push(i); else groups.push({ primary: i, others: [] });
  });
  return groups;
}

export async function mapPage({ container, query, isCurrent }) {
  const meta = state.meta;
  const admin = isAdmin();
  let map = null; let layer = null; let temp = null; let nearLayer = null; let picking = false; let pickingFor = 'report';
  let view = mapsAvailable() ? 'map' : 'list';   // 'map' or 'list' (the full-width list)
  let listOpen = false;                          // list under the map, revealed by "Show All Reports"
  let near = null; let groups = []; let selected = null; let firstFit = true;
  const L = raw();

  // ---- filters ----
  const q = h('input', { type: 'search', placeholder: 'Road name or hazard type…', 'aria-label': 'Search hazards', value: query.q || '' });
  const cat = h('select', { 'aria-label': 'Hazard type' }, h('option', { value: '' }, 'All hazard types'), meta.categories.map((c) => h('option', { value: c.label }, c.label)));
  const sev = h('select', { 'aria-label': 'Severity' }, h('option', { value: '' }, 'Any severity'), meta.severities.map((s) => h('option', { value: s }, s)));
  const st = h('select', { 'aria-label': 'Status' }, h('option', { value: '' }, 'Any status'), meta.statuses.map((s) => h('option', { value: s }, s)));
  const age = h('select', { 'aria-label': 'Report age' }, [['', 'Any time'], ['1', 'Last 24 hours'], ['7', 'Last 7 days'], ['30', 'Last 30 days'], ['90', 'Last 90 days']].map(([v, l]) => h('option', { value: v }, l)));
  const within = h('select', { 'aria-label': 'Distance from you', hidden: true }, [['', 'Any distance'], ['1000', 'Within 1 km'], ['3000', 'Within 3 km'], ['5000', 'Within 5 km'], ['10000', 'Within 10 km']].map(([v, l]) => h('option', { value: v }, l)));
  const resolved = h('input', { type: 'checkbox', checked: state.prefs ? state.prefs.mapShowResolved : false });
  const sample = h('input', { type: 'checkbox', checked: includeSample() });
  const summary = h('div', { class: 'muted small', role: 'status' }, 'Loading hazards…');
  const errorBox = h('div', { class: 'banner error', hidden: true, role: 'alert' });
  const tileNote = h('div', { class: 'banner warn', hidden: true }, 'Map tiles could not be loaded. Check your connection; the list view and filters still work.');
  const mapEl = h('div', { class: 'map-box tall', role: 'application', 'aria-label': 'Hazard map' });
  const listEl = h('div', { class: 'complaint-list', id: 'hazard-report-list', hidden: true });
  const listToggle = h('button', { class: 'btn list-toggle', type: 'button', 'aria-controls': 'hazard-report-list', 'aria-expanded': 'false', onclick: () => { listOpen = !listOpen; applyView(); } }, 'Show All Reports');
  const panel = h('aside', { class: 'card hazard-panel', hidden: true, 'aria-label': 'Hazard details' });
  const viewBtns = h('div', { class: 'segmented', role: 'group', 'aria-label': 'View' });
  const pickBtn = h('button', { class: 'btn small', type: 'button', 'aria-pressed': 'false', onclick: () => setPicking(!picking, 'report') }, icon('plus', 14), 'Pick a spot to report');
  const nearBtn = h('button', { class: 'btn small', type: 'button', onclick: nearMe }, icon('locate', 14), 'Near me');

  function drawViewBtns() {
    mount(viewBtns, ['map', 'list'].map((v) => h('button', { type: 'button', class: view === v ? 'active' : '', disabled: v === 'map' && !mapsAvailable(), 'aria-pressed': String(view === v), onclick: () => { view = v; drawViewBtns(); applyView(); } }, v === 'map' ? 'Map' : 'List')));
  }

  // Map view: the map, legend and the "Show All Reports" button under it; the list appears only when that button is pressed.
  // List view: the full list replaces the map.
  function applyView() {
    const mapView = view === 'map' && mapsAvailable();
    mapEl.hidden = !mapView;
    if (legendBox) legendBox.hidden = !mapView;
    listToggle.hidden = !mapView;
    listToggle.setAttribute('aria-expanded', String(listOpen));
    listToggle.textContent = listOpen ? 'Hide All Reports' : 'Show All Reports';
    listEl.hidden = mapView ? !listOpen : false;
    if (mapView && map) setTimeout(() => map.invalidateSize(), 30);
  }

  function setPicking(on, purpose) {
    picking = on; pickingFor = purpose || 'report';
    pickBtn.setAttribute('aria-pressed', String(on && pickingFor === 'report')); pickBtn.classList.toggle('primary', on && pickingFor === 'report');
    mapEl.style.cursor = on ? 'crosshair' : '';
    if (on) {
      if (view !== 'map' && mapsAvailable()) { view = 'map'; drawViewBtns(); applyView(); }
      toast(pickingFor === 'near' ? 'Click the map to set where you are.' : 'Click the map where the hazard is.', 'info');
    }
    if (!on && temp && map) { map.removeLayer(temp); temp = null; }
  }

  async function nearMe() {
    nearBtn.disabled = true;
    try {
      const p = await locate();                       // the browser asks for permission only now, because you pressed the button
      setNear({ lat: p.lat, lng: p.lng }, p.accuracy);
    } catch (e) {
      toast(e.message + ' Choose your position on the map instead.', 'error');
      setPicking(true, 'near');
    }
    nearBtn.disabled = false;
  }
  function setNear(p, accuracy) {
    near = p; within.hidden = false;
    if (map) {
      if (nearLayer) map.removeLayer(nearLayer);
      nearLayer = L.circleMarker([p.lat, p.lng], { radius: 8, color: '#fff', weight: 3, fillColor: '#0ea5e9', fillOpacity: 1 }).addTo(map).bindPopup(accuracy ? 'You are here (about ' + Math.round(accuracy) + ' m accuracy)' : 'Your chosen position');
      map.setView([p.lat, p.lng], 14);
    }
    draw();
  }

  // ---- data ----
  async function load() {
    summary.textContent = 'Loading hazards…'; errorBox.hidden = true;
    try {
      if (!state.me) { groups = []; if (layer) layer.clearLayers(); draw(); return; }   // nothing to show without a login
      const params = { category: cat.value, severity: sev.value, status: st.value, q: q.value.trim(), maxAgeDays: age.value, includeSample: sample.checked, includeResolved: resolved.checked };
      if (!admin) params.mine = 'true';   // non-admins: the server returns only their own reports (enforced there too)
      const issues = await api.get('/api/road-issues', params);
      if (!isCurrent()) return;
      groups = groupIssues(issues);
      draw();
      if (firstFit && map && groups.length && !query.hazard) { firstFit = false; fit(map, [L.featureGroup(layer.getLayers())], 50); }
      if (query.hazard && firstFit) { firstFit = false; const g = groups.find((x) => String(x.primary.id) === query.hazard || x.others.some((o) => String(o.id) === query.hazard)); if (g) { if (map) map.setView([g.primary.lat, g.primary.lng], 16); openPanel(g, Number(query.hazard)); } }
    } catch (e) {
      errorBox.textContent = e.message + ' '; errorBox.append(h('button', { class: 'btn small', type: 'button', onclick: load }, 'Try again')); errorBox.hidden = false; summary.textContent = '';
    }
  }

  function visibleGroups() {
    let list = groups;
    if (near) {
      list = list.map((g) => ({ ...g, distance: meters(near, g.primary) }));
      if (within.value) list = list.filter((g) => g.distance <= Number(within.value));
      list.sort((a, b) => a.distance - b.distance);
    }
    return list;
  }

  function draw() {
    if (!state.me) {
      summary.textContent = 'Log in to see the hazards you have reported.';
      mount(listEl, emptyState('Log in to see your reports', 'Only the reports you submitted are shown here.', h('a', { class: 'btn primary', href: '/login' }, 'Log in')));
      applyView();
      return;
    }
    const list = visibleGroups();
    const total = list.reduce((n, g) => n + 1 + g.others.length, 0);
    if (admin) {
      const real = list.filter((g) => !g.primary.sample).length;
      summary.textContent = list.length ? list.length + ' hazard' + (list.length === 1 ? '' : 's') + ' shown (' + total + ' report' + (total === 1 ? '' : 's') + '): ' + real + ' from citizens, ' + (list.length - real) + ' sample.' : 'No hazards match these filters.';
    } else {
      summary.textContent = list.length ? 'Showing ' + total + ' of your report' + (total === 1 ? '' : 's') + '.' : 'No reports of yours match these filters.';
    }
    if (layer) {
      layer.clearLayers();
      list.forEach((g) => {
        const m = issueMarker(meta, g.primary, () => openPanel(g));
        if (g.others.length) m.bindTooltip((g.others.length + 1) + ' reports here', { permanent: false });
        m.addTo(layer);
      });
    }
    mount(listEl, list.length ? list.slice(0, 100).map((g) => h('button', { type: 'button', class: 'complaint-card hazard-row', onclick: () => openPanel(g) },
      h('span', { class: 'bar', style: 'background:' + (g.primary.severity === 'High' ? 'var(--critical-fg)' : g.primary.severity === 'Medium' ? 'var(--warn-fg)' : 'var(--resolved-fg)') }),
      h('div', { class: 'body' }, h('div', null, categoryBadge(meta, g.primary.type, 22), ' ', h('strong', null, g.primary.type), ' ', g.primary.sample ? sampleBadge() : null, g.others.length ? h('span', { class: 'badge' }, (g.others.length + 1) + ' reports') : null),
        h('div', { class: 'meta' }, h('span', null, g.primary.roadName || 'Unnamed road'), h('span', null, 'reported ' + timeAgo(g.primary.createdAt)), h('span', null, g.primary.stillThere + ' confirmed'), g.distance != null ? h('span', { class: 'tech' }, fmtDistance(g.distance) + ' away') : null)),
      h('div', { class: 'side' }, severityBadge(g.primary.severity), statusBadge(g.primary.status)))) : emptyState(admin ? 'No hazards found' : 'No reports found', 'Try removing a filter, widening the report age, or turning on resolved hazards.',
      h('a', { class: 'btn primary', href: '/report' }, 'Report a hazard')));
    applyView();
  }

  // ---- detail panel ----
  async function openPanel(group, focusId) {
    selected = group;
    panel.hidden = false;
    mount(panel, skeleton(6));
    const id = focusId || group.primary.id;
    try {
      const d = await api.get('/api/hazards/' + id);
      renderPanel(d, group);
    } catch (e) { mount(panel, errorState(e, () => openPanel(group, focusId)), h('button', { class: 'btn small', type: 'button', onclick: closePanel }, 'Close')); }
    panel.scrollIntoView && panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }
  function closePanel() { panel.hidden = true; selected = null; }

  function renderPanel(d, group) {
    const others = [group.primary, ...group.others].filter((x) => x.id !== d.id);
    const verify = async (vote) => {
      try { const r = await api.post('/api/hazards/' + d.id + '/verify', { vote }); d.stillThere = r.stillThere; d.gone = r.gone; d.lastVerifiedAt = r.lastVerifiedAt; d.yourVote = r.yourVote; d.flag = r.flag; toast(r.message, 'success'); renderPanel(d, group); }
      catch (e) { toast(e.message, 'error'); }
    };
    mount(panel,
      h('div', { class: 'card-head' }, h('h3', null, categoryBadge(meta, d.type, 24), ' ', d.type), h('button', { class: 'icon-btn small-x', type: 'button', 'aria-label': 'Close details', onclick: closePanel }, icon('x'))),
      h('div', { class: 'row-badges' }, severityBadge(d.severity), statusBadge(d.status), d.sample ? sampleBadge() : null),
      d.sample ? h('p', { class: 'small muted' }, 'This is a demonstration record, not a real report.') : null,
      d.hasPhoto ? h('figure', { class: 'photo-fig' }, h('img', { loading: 'lazy', decoding: 'async',  class: 'detail-photo', src: '/api/hazards/' + d.id + '/photo', alt: 'Photo of the reported ' + d.type.toLowerCase(), loading: 'lazy' }), h('figcaption', null, 'Photo from the report')) : h('p', { class: 'hint' }, 'No photo was attached.'),
      d.hasResolutionPhoto ? h('figure', { class: 'photo-fig' }, h('img', { loading: 'lazy', decoding: 'async',  class: 'detail-photo', src: '/api/hazards/' + d.id + '/resolution-photo', alt: 'Photo of the repair work', loading: 'lazy' }), h('figcaption', null, 'Repair evidence added by staff')) : null,
      d.description ? h('p', null, d.description) : null,
      h('dl', { class: 'kv' },
        h('dt', null, 'Report ID'), h('dd', { class: 'tech' }, d.reportId),
        h('dt', null, 'Location'), h('dd', null, (d.roadName || 'Unnamed road') + ' · ', h('span', { class: 'tech' }, d.approxLat + ', ' + d.approxLng), h('div', { class: 'hint' }, 'Approximate position')),
        h('dt', null, 'Reported'), h('dd', null, fmtDateTime(d.reportedAt) + ' (' + timeAgo(d.reportedAt) + ')'),
        h('dt', null, 'Community'), h('dd', null, d.stillThere + ' say it is still there · ' + d.gone + ' say it is gone', d.lastVerifiedAt ? h('div', { class: 'hint' }, 'Last answer ' + timeAgo(d.lastVerifiedAt)) : null)),
      d.flag === 'POSSIBLY_GONE' ? h('div', { class: 'banner warn small' }, 'Several people say this may be gone. Staff will review it; the official status has not changed.') : null,
      d.flag === 'REPORTED_STILL_PRESENT' ? h('div', { class: 'banner warn small' }, 'Marked resolved, but several people say it is still there. Staff will review it.') : null,
      h('div', { class: 'verify-box' }, h('strong', null, 'Is it still there?'),
        d.canVerify ? h('div', { class: 'btn-row' },
          h('button', { class: 'btn small' + (d.yourVote === 'STILL_THERE' ? ' primary' : ''), type: 'button', 'aria-pressed': String(d.yourVote === 'STILL_THERE'), onclick: () => verify('STILL_THERE') }, 'Still there'),
          h('button', { class: 'btn small' + (d.yourVote === 'GONE' ? ' primary' : ''), type: 'button', 'aria-pressed': String(d.yourVote === 'GONE'), onclick: () => verify('GONE') }, 'No longer there'))
          : h('p', { class: 'small muted' }, d.verifyBlockedReason, ' ', !state.me ? h('a', { href: '/login' }, 'Log in') : null),
        h('p', { class: 'hint' }, 'Your answer is a community signal. Only authorised staff can change the official status.')),
      d.timeline.length ? h('div', null, h('h4', null, 'Progress'), h('ul', { class: 'mini-timeline' }, d.timeline.map((t) => h('li', null, h('span', null, t.stage), h('span', { class: 'when' }, timeAgo(t.at)))))) : null,
      others.length ? h('div', null, h('h4', null, 'Also reported at this spot'), h('ul', { class: 'list-plain' }, others.map((o) => h('li', null, h('span', null, o.complaintId || ('Pin ' + o.id), ' · ', timeAgo(o.createdAt)), h('button', { class: 'btn small', type: 'button', onclick: () => openPanel(group, o.id) }, 'Open'))))) : null,
      h('div', { class: 'btn-row', style: 'margin-top:12px' }, h('a', { class: 'btn small primary', href: '/report?lat=' + d.approxLat + '&lng=' + d.approxLng + '&type=' + encodeURIComponent(d.type) }, 'Report a similar hazard here')));
  }

  // ---- wiring ----
  const debounced = debounce(load, 350);
  q.addEventListener('input', debounced);
  [cat, sev, st, age].forEach((s) => s.addEventListener('change', load));
  within.addEventListener('change', draw);
  resolved.addEventListener('change', load);
  sample.addEventListener('change', async () => { await setIncludeSample(sample.checked); load(); });
  const search = placeSearch({ placeholder: 'Jump to a place…', onPick: (r) => { if (map) map.setView([r.lat, r.lng], 15); } });
  const legendBox = legend(meta);
  legendBox.classList.add('collapsed');
  legendBox.querySelector('strong').addEventListener('click', () => legendBox.classList.toggle('collapsed'));
  legendBox.querySelector('strong').textContent = 'Legend (tap to expand)';

  const filters = h('div', { class: 'card map-panel' }, h('h3', null, 'Find and filter'),
    field('Search places', search.el), field('Search hazards', q), field('Hazard type', cat), field('Severity', sev), field('Status', st), field('Report age', age), field('Distance', within),
    h('label', { class: 'toggle', style: 'display:flex;margin-bottom:8px' }, resolved, 'Show resolved hazards'),
    h('label', { class: 'toggle', style: 'display:flex;margin-bottom:12px' }, sample, 'Include sample data'),
    h('p', { class: 'hint' }, 'Hazards are reported by citizens and may be incomplete or out of date. Records marked \"Sample data\" are demonstrations, not real reports.'));

  mount(container, pageHeader('Explore map', admin ? 'Hazards reported on the road, on an OpenStreetMap base map' : 'Your hazard reports on an OpenStreetMap base map', h('a', { class: 'btn primary', href: '/report' }, icon('plus', 16), 'Report a hazard')), errorBox, tileNote,
    h('div', { class: 'map-layout' }, filters,
      h('div', { class: 'map-main' },
        h('div', { class: 'toolbar' }, viewBtns, nearBtn, pickBtn, h('div', { class: 'grow' }), summary),
        h('div', { class: 'map-stage' }, h('div', { class: 'map-wrap' }, mapEl, mapsAvailable() ? legendBox : null, listToggle, listEl), panel))));
  drawViewBtns(); applyView();

  if (mapsAvailable()) {
    map = createMap(mapEl, meta, { onTileError: () => { tileNote.hidden = false; } });
    layer = layerGroup(map);
    map.on('click', (e) => {
      if (!picking) return;
      if (pickingFor === 'near') { setPicking(false); setNear({ lat: e.latlng.lat, lng: e.latlng.lng }); return; }
      if (temp) map.removeLayer(temp);
      temp = pickMarker(e.latlng, '!').addTo(map);
      temp.bindPopup(h('div', { class: 'popup' }, h('h4', null, 'Report a hazard here?'), h('div', { class: 'muted' }, e.latlng.lat.toFixed(5) + ', ' + e.latlng.lng.toFixed(5)),
        h('div', { style: 'margin-top:8px' }, h('button', { class: 'btn primary small', type: 'button', onclick: () => navigate('/report?lat=' + e.latlng.lat + '&lng=' + e.latlng.lng) }, 'Report here')))).openPopup();
    });
  } else {
    tileNote.hidden = false; tileNote.textContent = 'The map could not be loaded, so hazards are shown as a list.';
  }
  await load();
  return () => { if (map) map.remove(); };
}
