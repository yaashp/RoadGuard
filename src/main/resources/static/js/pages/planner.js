// Hazard-aware route planner: real route geometry from the routing service, scored against reported hazards.
import { api } from '../api.js';
import { state, includeSample } from '../state.js';
import { h, icon, mount, toast, pageHeader, classBadge, sampleBadge, field, skeleton } from '../ui.js';
import { createMap, drawRoute, hazardDot, issueMarker, pickMarker, layerGroup, locate, fit, classColor, mapsAvailable } from '../maps.js';
import { placeSearch } from '../places.js';
import { fmtDistance, fmtDuration, fmtCoord } from '../format.js';
import { routeTabs } from './routetabs.js';

export async function plannerPage({ container, query, isCurrent }) {
  const meta = state.meta;
  const L = window.L;
  let origin = null; let dest = null; let pickTarget = null; let analysis = null; let selected = 0;
  let map = null; let pointLayer = null; let routeLayer = null; let hazardLayer = null; let overviewLayer = null; let routeLines = [];

  const originInfo = h('div', { class: 'hint', 'data-field': 'origin' }, 'Not set');
  const destInfo = h('div', { class: 'hint', 'data-field': 'destination' }, 'Not set');
  const status = h('div', { role: 'status' });
  const results = h('div', { class: 'grid', style: 'margin-top:14px' });
  const compare = h('div', { style: 'margin-top:16px' });
  const sampleBox = h('input', { type: 'checkbox', checked: includeSample() });
  const go = h('button', { class: 'btn primary', type: 'button', onclick: analyze }, icon('route', 16), 'Compare routes');
  const mapEl = h('div', { class: 'map-box tall', role: 'application', 'aria-label': 'Route map' });
  const tileNote = h('div', { class: 'banner warn', hidden: true }, 'Map tiles could not be loaded. Routes will still be calculated.');
  const overviewNote = h('p', { class: 'hint', role: 'status', style: 'margin-top:6px' }, 'Loading reported hazards…');

  function describe(p) { return p.label || (fmtCoord(p.lat) + ', ' + fmtCoord(p.lng)); }
  function refreshPoints() {
    originInfo.textContent = origin ? 'Start: ' + describe(origin) : 'Not set';
    destInfo.textContent = dest ? 'Destination: ' + describe(dest) : 'Not set';
    if (!map) return;
    pointLayer.clearLayers();
    if (origin) pickMarker([origin.lat, origin.lng], 'A').addTo(pointLayer);
    if (dest) pickMarker([dest.lat, dest.lng], 'B').addTo(pointLayer);
  }
  function setPoint(which, p) { if (which === 'origin') origin = p; else dest = p; refreshPoints(); if (map && p) map.setView([p.lat, p.lng], Math.max(map.getZoom(), 13)); }
  function pickOnMap(which) { pickTarget = which; mapEl.style.cursor = 'crosshair'; toast('Click the map to set the ' + (which === 'origin' ? 'start' : 'destination') + '.', 'info'); }

  const originSearch = placeSearch({ placeholder: 'Search start…', label: 'Search start location', onPick: (r) => setPoint('origin', { lat: r.lat, lng: r.lng, label: r.label }) });
  const destSearch = placeSearch({ placeholder: 'Search destination…', label: 'Search destination', onPick: (r) => setPoint('dest', { lat: r.lat, lng: r.lng, label: r.label }) });

  const panel = h('div', { class: 'card' }, h('h3', null, 'Plan a route'),
    h('div', { class: 'field' }, h('label', null, 'Start'), originSearch.el, originInfo,
      h('div', { class: 'btn-row', style: 'margin-top:6px' },
        h('button', { class: 'btn small', type: 'button', onclick: () => pickOnMap('origin') }, icon('map', 14), 'Pick on map'),
        h('button', { class: 'btn small', type: 'button', onclick: async () => { try { const p = await locate(); setPoint('origin', { lat: p.lat, lng: p.lng, label: 'My location' }); } catch (e) { toast(e.message, 'error'); } } }, icon('locate', 14), 'My location'))),
    h('div', { class: 'btn-row', style: 'justify-content:center;margin:-4px 0 6px' }, h('button', { class: 'btn ghost small', type: 'button', 'aria-label': 'Swap start and destination', onclick: () => { [origin, dest] = [dest, origin]; refreshPoints(); } }, icon('swap', 14), 'Swap')),
    h('div', { class: 'field' }, h('label', null, 'Destination'), destSearch.el, destInfo,
      h('div', { class: 'btn-row', style: 'margin-top:6px' }, h('button', { class: 'btn small', type: 'button', onclick: () => pickOnMap('dest') }, icon('map', 14), 'Pick on map'))),
    h('label', { class: 'toggle', style: 'display:flex;margin-bottom:12px' }, sampleBox, 'Include sample data in the score'),
    go, status);

  function setStatus(node) { mount(status, node); }

  async function analyze() {
    if (!origin || !dest) { setStatus(h('div', { class: 'banner warn' }, 'Choose both a start and a destination first.')); return; }
    go.disabled = true; go.lastChild.textContent = 'Calculating…';
    setStatus(skeleton(3)); mount(results); mount(compare);
    try {
      const a = await api.post('/api/routes/analyze', { origin, destination: dest, includeSample: sampleBox.checked });
      if (!isCurrent()) return;
      analysis = a; selected = a.recommendedIndex;
      setStatus(null);
      drawAnalysis();
    } catch (e) {
      const unavailable = e.status === 503;
      setStatus(h('div', { class: 'banner ' + (unavailable ? 'warn' : 'error'), role: 'alert' }, h('span', null, e.message), unavailable ? h('button', { class: 'btn small', type: 'button', onclick: analyze }, 'Try again') : null));
    }
    go.disabled = false; go.lastChild.textContent = 'Compare routes';
  }

  function drawAnalysis() {
    const a = analysis;
    if (map) {
      routeLayer.clearLayers(); hazardLayer.clearLayers(); routeLines = [];
      a.routes.forEach((r) => { const line = drawRoute(map, r.geometry, r.safety.classification, r.index === selected); line.on('click', () => select(r.index)); routeLayer.addLayer(line); routeLines.push(line); });
      routeLines[selected].bringToFront();
      a.routes[selected].safety.hazards.forEach((hz) => hazardDot(hz).bindPopup(h('div', { class: 'popup' }, h('h4', null, hz.category), h('div', { class: 'row' }, h('span', { class: 'badge sev-' + hz.severity.toLowerCase() }, hz.severity), h('span', { class: 'badge' }, hz.status), hz.sample ? sampleBadge() : null),
        h('div', { class: 'muted' }, hz.ageDays + ' days old · ' + hz.stillThere + ' confirmed · ' + hz.gone + ' say gone'),
        h('div', null, hz.roadName || ''), h('div', { class: 'muted' }, hz.distanceMeters + ' m from the route · penalty -' + hz.penalty + (hz.reportsMerged > 1 ? ' · ' + hz.reportsMerged + ' reports merged' : '')))).addTo(hazardLayer));
      fit(map, routeLines, 40);
    }
    const best = a.routes[a.recommendedIndex];
    mount(results,
      h('div', { class: 'banner info' }, icon('info', 16), h('span', null, a.routes.length + ' route(s) found. Highest safety score: ',
        h('strong', null, routeName(a.recommendedIndex) + ' (' + best.safety.score + '/100, ' + best.safety.classification + ')'), '. ', a.hazardsConsidered + ' open hazards were considered' + (a.includesSampleData ? ', including ' + a.sampleHazardsConsidered + ' sample.' : '.'))),
      h('p', { class: 'reason' }, h('strong', null, 'Why this suggestion: '), a.recommendationReason),
      a.warnings.map((w) => h('div', { class: 'banner warn small' }, icon('alert', 16), h('span', null, w))),
      a.routes.map(routeCard),
      hazardList(),
      navBox(),
      saveBox(),
      h('details', { class: 'card' }, h('summary', null, h('strong', null, 'How is the score calculated?')), h('p', { class: 'muted small', style: 'margin-top:8px' }, a.model.description)),
      h('div', { class: 'banner warn small' }, icon('alert', 16), h('span', null, a.disclaimer)));
    mount(compare, comparisonTable());
  }

  // Every open reported hazard, so people can see the road conditions before they compare routes.
  // Public data (no personal information); the route score itself is still computed on the server.
  async function loadOverview() {
    if (!map) return;
    try {
      const issues = await api.get('/api/road-issues', { includeSample: includeSample(), includeResolved: false });
      if (!isCurrent()) return;
      overviewLayer.clearLayers();
      issues.forEach((i) => issueMarker(meta, i).bindPopup(h('div', { class: 'popup' },
        h('h4', null, i.type),
        h('div', { class: 'row' }, h('span', { class: 'badge sev-' + String(i.severity).toLowerCase() }, i.severity), h('span', { class: 'badge' }, i.status), i.sample ? sampleBadge() : null),
        h('div', null, i.roadName || ''))).addTo(overviewLayer));
      overviewNote.textContent = issues.length
        ? 'Showing ' + issues.length + ' open reported hazard(s). Click a pin for details. Select a route to see the hazards that affect it.'
        : 'No open hazards are reported in this area.';
    } catch (e) {
      overviewNote.textContent = 'Reported hazards could not be loaded: ' + e.message;
    }
  }

  function hazardList() {
    const hz = analysis.routes[selected].safety.hazards;
    return h('div', { class: 'card' }, h('h3', null, 'Reported hazards near ' + routeName(selected).toLowerCase()),
      hz.length ? h('ul', { class: 'list-plain' }, hz.map((x) => h('li', null,
        h('span', null, h('strong', null, x.category), x.roadName ? ' · ' + x.roadName : '', x.sample ? [' ', sampleBadge()] : null,
          h('div', { class: 'hint tech' }, x.distanceMeters + ' m from the route · ' + x.ageDays + ' days old · ' + x.stillThere + ' confirmed / ' + x.gone + ' gone · ' + x.severity + ' · ' + x.status)),
        h('span', { class: 'tech' }, '-' + x.penalty)))) : h('p', { class: 'muted' }, 'No reported hazards were found near this route. That does not mean the road is free of hazards.'),
      h('p', { class: 'hint' }, 'Penalty = severity x distance x status x recency x community confirmation.'));
  }
  function navBox() {
    const n = analysis.externalNavigation;
    return h('div', { class: 'card' }, h('h3', null, 'Open in a navigation app'),
      h('div', { class: 'btn-row' }, h('a', { class: 'btn small', href: n.googleMapsUrl, target: '_blank', rel: 'noopener noreferrer' }, 'Google Maps'), h('a', { class: 'btn small', href: n.openStreetMapUrl, target: '_blank', rel: 'noopener noreferrer' }, 'OpenStreetMap')),
      h('p', { class: 'hint' }, n.note));
  }
  const routeName = (i) => (i === 0 ? 'Fastest route' : 'Alternative ' + (i + 1));
  function select(i) { selected = i; drawAnalysis(); }

  function routeCard(r) {
    const s = r.safety;
    return h('div', { class: 'route-card' + (r.index === selected ? ' selected' : ''), role: 'button', tabindex: '0', 'aria-pressed': String(r.index === selected), onclick: () => select(r.index), onkeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); select(r.index); } } },
      h('div', { class: 'top' }, h('strong', null, routeName(r.index), r.recommended ? h('span', { class: 'tag' }, 'Safest') : null), classBadge(s.classification)),
      h('div', { class: 'score', style: 'color:' + classColor(s.classification) }, String(s.score), h('small', null, '/ 100 safety score')),
      h('div', { class: 'meter', role: 'img', 'aria-label': 'Safety score ' + s.score + ' out of 100' }, h('span', { style: 'width:' + s.score + '%;background:' + classColor(s.classification) })),
      h('div', { class: 'route-stats' }, h('span', null, icon('route', 14), ' ', fmtDistance(r.distanceMeters)), h('span', null, icon('clock', 14), ' ', fmtDuration(r.durationSeconds)),
        h('span', null, icon('alert', 14), ' ', s.hazardCount + ' hazard' + (s.hazardCount === 1 ? '' : 's'))),
      h('div', { class: 'route-stats', style: 'margin-top:4px' }, h('span', null, 'High ' + s.hazardsBySeverity.High), h('span', null, 'Medium ' + s.hazardsBySeverity.Medium), h('span', null, 'Low ' + s.hazardsBySeverity.Low)),
      h('details', { onclick: (e) => e.stopPropagation() }, h('summary', { class: 'small', style: 'margin-top:8px;cursor:pointer' }, 'Why this score?'), h('ul', { class: 'explain' }, s.explanation.map((t) => h('li', null, t)))));
  }

  function comparisonTable() {
    const a = analysis;
    return h('div', null, h('h2', null, 'Route comparison'), h('div', { class: 'table-wrap' }, h('table', null,
      h('thead', null, h('tr', null, ['Route', 'Distance', 'Time', 'Hazards (H/M/L)', 'Total penalty', 'Per km', 'Score', 'Class'].map((c, i) => h('th', { class: i > 0 && i < 7 ? 'num' : '' }, c)))),
      h('tbody', null, a.routes.map((r) => h('tr', { class: 'clickable', onclick: () => select(r.index) },
        h('td', null, routeName(r.index) + (r.recommended ? ' ★' : '')), h('td', { class: 'num' }, fmtDistance(r.distanceMeters)), h('td', { class: 'num' }, fmtDuration(r.durationSeconds)),
        h('td', { class: 'num' }, r.safety.hazardsBySeverity.High + '/' + r.safety.hazardsBySeverity.Medium + '/' + r.safety.hazardsBySeverity.Low),
        h('td', { class: 'num' }, '-' + r.safety.totalPenalty), h('td', { class: 'num' }, r.safety.exposurePerKm), h('td', { class: 'num' }, h('strong', null, r.safety.score)), h('td', null, classBadge(r.safety.classification))))))));
  }

  function saveBox() {
    const name = h('input', { name: 'name', type: 'text', maxlength: '80', placeholder: 'e.g. Home to college' });
    const box = h('div', { class: 'card' }, h('h3', null, 'Save this route'), h('p', { class: 'muted small' }, 'Saves the start and destination so you can re-check the hazard exposure later.'),
      h('div', { class: 'btn-row' }, h('div', { style: 'flex:1;min-width:180px' }, name),
        h('button', { class: 'btn primary', type: 'button', onclick: async () => {
          if (!name.value.trim()) { toast('Give the route a name first.', 'error'); name.focus(); return; }
          try {
            const r = await api.post('/api/saved-routes', { name: name.value.trim(), origin, destination: dest });
            toast('Route saved' + (r.analysisAvailable ? '.' : ' (without a score: ' + r.analysisMessage + ')'), 'success');
            name.value = '';
          } catch (e) { toast((e.fieldErrors && e.fieldErrors.name) || e.message, 'error'); }
        } }, 'Save'), h('a', { class: 'btn ghost', href: '/saved' }, 'Saved routes')));
    return box;
  }

  mount(container, pageHeader('Safe routes', 'Compare real routes by the hazards reported along them.'), routeTabs('plan'), tileNote,
    h('div', { class: 'map-layout' }, h('div', null, panel, results), h('div', null, h('div', { class: 'map-wrap' }, mapEl, overviewNote), compare)));

  if (mapsAvailable()) {
    map = createMap(mapEl, meta, { onTileError: () => { tileNote.hidden = false; } });
    overviewLayer = layerGroup(map); pointLayer = layerGroup(map); routeLayer = layerGroup(map); hazardLayer = layerGroup(map);
    loadOverview();
    map.on('click', (e) => { if (pickTarget) { setPoint(pickTarget, { lat: e.latlng.lat, lng: e.latlng.lng, label: '' }); pickTarget = null; mapEl.style.cursor = ''; } });
  } else mapEl.replaceChildren('The map library failed to load. You can still search for places and compare routes.');

  if (query.saved) {
    try {
      const s = await api.get('/api/saved-routes/' + encodeURIComponent(query.saved));
      if (!isCurrent()) return;
      origin = { lat: s.originLat, lng: s.originLng, label: s.originLabel }; dest = { lat: s.destinationLat, lng: s.destinationLng, label: s.destinationLabel };
      refreshPoints(); await analyze();
    } catch (e) { toast(e.message, 'error'); }
  }
  return () => { if (map) map.remove(); };
}
