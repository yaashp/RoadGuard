// Map provider abstraction. Pages only use the functions exported here, never Leaflet directly, so the
// provider can be replaced by re-implementing this one file. Tiles come from /api/meta (config, not code).
import { h } from './ui.js';

const L = window.L;
const SAFE_COLOR = /^#[0-9a-fA-F]{6}$/;
const SEVERITIES = ['High', 'Medium', 'Low'];

export function mapsAvailable() { return !!L; }

export function createMap(el, meta, { center, zoom, onTileError } = {}) {
  const map = L.map(el, { zoomControl: true }).setView(center || [meta.map.centerLat, meta.map.centerLng], zoom || meta.map.zoom);
  const tiles = L.tileLayer(meta.map.tileUrl, { maxZoom: meta.map.maxZoom, attribution: meta.map.attribution });
  let warned = false;
  tiles.on('tileerror', () => { if (!warned && onTileError) { warned = true; onTileError(); } });
  tiles.addTo(map);
  setTimeout(() => map.invalidateSize(), 50);   // container may still be settling when first created
  return map;
}

// Simple line glyphs per category (static markup only). Used on pins, in the legend and in forms instead of emoji.
const GLYPHS = {
  'Pothole': '<ellipse cx="12" cy="13" rx="7" ry="4"/><path d="M8 11l2 2m4-2-2 2"/>',
  'Road Damage': '<path d="M12 3l-2 6 4 3-3 4 2 5"/>',
  'Accident': '<path d="M12 4 3 20h18L12 4Z"/><path d="M12 10v4m0 3v.01"/>',
  'Waterlogging': '<path d="M3 10c3-3 6 3 9 0s6 3 9 0M3 16c3-3 6 3 9 0s6 3 9 0"/>',
  'Broken Streetlight': '<path d="M8 21h8M12 21V8"/><path d="M12 8a6 6 0 0 1 6-4"/><circle cx="18" cy="4" r="1.5"/>',
  'Damaged Traffic Signal': '<rect x="8" y="3" width="8" height="18" rx="2"/><circle cx="12" cy="8" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="16" r="1"/>',
  'Road Construction': '<path d="M3 20h18M5 20l3-12h8l3 12M9 12h6"/>',
  'Debris on Road': '<path d="M5 19l3-5 3 3 3-6 5 8z"/>',
  'Road Obstruction / Fallen Tree': '<path d="M12 21v-6M12 3 6 12h4l-3 5h10l-3-5h4L12 3Z"/>',
  'Traffic Hazard': '<path d="M12 3l9 9-9 9-9-9 9-9Z"/><path d="M12 8v5m0 3v.01"/>',
  'Other': '<circle cx="12" cy="12" r="3"/>',
};
export function categoryGlyph(label, size = 18) {
  return '<svg viewBox="0 0 24 24" width="' + size + '" height="' + size + '" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + (GLYPHS[label] || GLYPHS.Other) + '</svg>';
}
/** A coloured round badge with the category glyph, as a DOM element. */
export function categoryBadge(meta, label, size = 22) {
  const cat = categoryInfo(meta, label);
  const el = h('span', { class: 'cat-dot', style: 'width:' + size + 'px;height:' + size + 'px;background:' + (SAFE_COLOR.test(cat.color) ? cat.color : '#57534E') });
  el.innerHTML = categoryGlyph(label, Math.round(size * 0.65));   // glyph markup is static, never user text
  return el;
}

export function categoryInfo(meta, label) {
  return (meta.categories || []).find((c) => c.label === label) || { label, color: '#57534E', icon: '📍' };
}

/** A round pin: fill = category colour, ring = severity, faded = no longer open. Only whitelisted values reach the markup. */
export function issueMarker(meta, issue, onClick) {
  const cat = categoryInfo(meta, issue.type);
  const color = SAFE_COLOR.test(cat.color) ? cat.color : '#57534E';
  const sev = SEVERITIES.includes(issue.severity) ? issue.severity.toLowerCase() : 'medium';
  const closed = issue.status === 'Resolved' || issue.status === 'Rejected' || issue.status === 'Duplicate';
  const progress = issue.status === 'Repair in Progress' || issue.status === 'Action Planned';
  const html = '<div class="pin sev-' + sev + (closed ? ' closed' : '') + (progress ? ' working' : '') + (issue.sample ? ' sample' : '') + '" style="--pin:' + color + '"><span>' + (closed ? '✓' : categoryGlyph(issue.type, 18)) + '</span></div>';
  const icon = L.divIcon({ className: 'rg-pin-wrap', html, iconSize: [34, 34], iconAnchor: [17, 17], popupAnchor: [0, -16] });
  const marker = L.marker([issue.lat, issue.lng], { icon, title: issue.type + ' - ' + issue.severity + ' - ' + issue.status });
  if (onClick) marker.on('click', () => onClick(issue));
  return marker;
}

export function pickMarker(latlng, label) {
  return L.marker(latlng, { icon: L.divIcon({ className: 'rg-pin-wrap', html: '<div class="pin pick"><span>' + label + '</span></div>', iconSize: [34, 34], iconAnchor: [17, 34] }) });
}

export function layerGroup(map) { return L.layerGroup().addTo(map); }

const CLASS_COLORS = { Safe: '#0d9488', Moderate: '#d97706', Risky: '#e11d48' };
export const classColor = (c) => CLASS_COLORS[c] || '#57534E';

export function drawRoute(map, geometry, classification, selected) {
  const line = L.polyline(geometry, { color: classColor(classification), weight: selected ? 7 : 5, opacity: selected ? 0.95 : 0.55, dashArray: selected ? null : '8 8' });
  return line.addTo(map);
}

export function hazardDot(hz) {
  const color = hz.severity === 'High' ? '#e11d48' : hz.severity === 'Medium' ? '#d97706' : '#0d9488';
  return L.circleMarker([hz.lat, hz.lng], { radius: 7, color: '#fff', weight: 2, fillColor: color, fillOpacity: 0.95 });
}

export function fit(map, layers, padding = 40) {
  const pts = [];
  layers.forEach((l) => { if (l.getBounds) { const b = l.getBounds(); if (b.isValid()) { pts.push(b.getSouthWest(), b.getNorthEast()); } } else if (l.getLatLng) pts.push(l.getLatLng()); });
  if (pts.length) map.fitBounds(L.latLngBounds(pts), { padding: [padding, padding], maxZoom: 16 });
}

export function locate() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) { reject(new Error('Your browser does not support location access.')); return; }
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: p.coords.accuracy }),
      (e) => reject(new Error(e.code === 1 ? 'Location permission was denied. You can still click the map to choose a point.' : 'Could not determine your location.')),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 });
  });
}

export function legend(meta, { showCategories = true } = {}) {
  const box = h('div', { class: 'map-legend' }, h('strong', null, 'Legend'));
  if (showCategories) {
    (meta.categories || []).forEach((c) => box.appendChild(h('div', { class: 'legend-row' }, categoryBadge(meta, c.label, 20), c.label)));
  }
  box.appendChild(h('div', { class: 'legend-sep' }));
  box.appendChild(h('div', { class: 'legend-row' }, h('span', { class: 'ring sev-high' }), 'High severity (thick red ring)'));
  box.appendChild(h('div', { class: 'legend-row' }, h('span', { class: 'ring sev-medium' }), 'Medium severity (amber ring)'));
  box.appendChild(h('div', { class: 'legend-row' }, h('span', { class: 'ring sev-low' }), 'Low severity (green ring)'));
  box.appendChild(h('div', { class: 'legend-row' }, h('span', { class: 'ring closed' }, '✓'), 'Resolved / rejected (faded)'));
  box.appendChild(h('div', { class: 'legend-row' }, h('span', { class: 'ring working' }), 'Assigned / in progress (dashed)'));
  box.appendChild(h('div', { class: 'legend-row' }, h('span', { class: 'ring sample' }), 'Sample data (hatched)'));
  return box;
}

export const raw = () => L;
