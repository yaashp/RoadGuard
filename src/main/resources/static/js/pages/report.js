import { navigate } from '../router.js';
// Report a hazard: category, photo (camera or file), location, severity, description. Works with poor connectivity:
// drafts are kept on this device, failed uploads are kept and retried, and a retry can never create a second report.
import { api, ApiError } from '../api.js';
import { state, refreshUnread } from '../state.js';
import { h, icon, mount, field, showFieldErrors, toast, pageHeader, severityBadge, statusBadge, debounce } from '../ui.js';
import { createMap, pickMarker, locate, mapsAvailable, categoryBadge } from '../maps.js';
import { placeSearch } from '../places.js';
import { fmtCoord, timeAgo, fmtDistance } from '../format.js';
import { newRequestId, saveQueued, sendReport, flushQueue, queueSupported, queuedReports } from '../queue.js';

const MAX_SIDE = 1600;
const DRAFT_KEY = 'rg_draft';

/** Re-encodes the photo as a JPEG no larger than 1600 px: smaller upload, and hidden metadata such as GPS tags is dropped. */
async function prepareImage(file) {
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale); canvas.height = Math.round(bitmap.height * scale);
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise((res) => canvas.toBlob(res, 'image/jpeg', 0.85));
    if (blob && blob.size < file.size * 1.2) return new File([blob], 'photo.jpg', { type: 'image/jpeg' });
  } catch (e) { /* fall back to the original file */ }
  return file;
}

const loadDraft = () => { try { return JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null'); } catch (e) { return null; } };
const saveDraft = (d) => { try { localStorage.setItem(DRAFT_KEY, JSON.stringify(d)); } catch (e) { /* storage may be unavailable */ } };
const clearDraft = () => { try { localStorage.removeItem(DRAFT_KEY); } catch (e) { /* ignore */ } };

export async function reportPage({ container, query }) {
  const meta = state.meta;
  let point = null; let photo = null; let map = null; let marker = null;
  let requestId = newRequestId();
  let busy = false;
  let matches = [];

  const draft = loadDraft();
  const restored = !!(draft && (draft.description || draft.issueType || draft.lat));
  if (restored && draft.clientRequestId) requestId = draft.clientRequestId;

  // ---- form controls ----
  const category = h('div', { class: 'choice-grid', 'data-field': 'issueType' }, meta.categories.map((c, i) => h('label', { class: 'choice' },
    h('input', { type: 'radio', name: 'issueType', value: c.label, checked: (query.type || (restored && draft.issueType)) === c.label, required: i === 0 }),
    h('span', null, categoryBadge(meta, c.label, 22), c.label))));
  const severity = h('div', { class: 'choice-grid', 'data-field': 'severity', style: 'grid-template-columns:repeat(3,1fr)' }, meta.severities.map((s) => h('label', { class: 'choice sev-choice ' + s.toLowerCase() },
    h('input', { type: 'radio', name: 'severity', value: s, checked: (restored && draft.severity ? draft.severity : 'Medium') === s }), h('span', null, s))));
  const description = h('textarea', { name: 'description', maxlength: '2000', placeholder: 'What is wrong, and where exactly? e.g. "Deep pothole in the left lane just after the signal."' });
  if (restored && draft.description) description.value = draft.description;
  const counter = h('span', { class: 'hint' }, description.value.length + ' / 2000');
  const roadName = h('input', { name: 'roadName', type: 'text', maxlength: '120', placeholder: 'e.g. Linking Road', value: restored ? draft.roadName || '' : '' });
  const address = h('input', { name: 'address', type: 'text', maxlength: '200', placeholder: 'Nearby landmark (optional)', value: restored ? draft.address || '' : '' });
  const coords = h('div', { class: 'hint' }, 'No location chosen yet. Click the map, search for a place, or use your location.');
  const coordError = h('div', { class: 'error-text', role: 'alert' });
  const locField = h('div', { class: 'field', 'data-field': 'location' });
  const mapEl = h('div', { class: 'map-box small', id: 'report-map', role: 'application', 'aria-label': 'Map: click to choose the location of the hazard' });
  const mapNote = h('div', { class: 'banner warn', hidden: true }, 'Map tiles could not be loaded (are you offline?). You can still use "Use my location" or the search box.');
  const banner = h('div', { class: 'banner error', hidden: true, role: 'alert' });
  const statusLine = h('div', { class: 'upload-status', role: 'status', 'aria-live': 'polite' });
  const dupBox = h('div', { hidden: true });
  const suggestBox = h('div', { class: 'suggest-box', hidden: true });
  const allowDup = h('input', { type: 'checkbox', name: 'allowDuplicate' });

  const selected = (name) => (container.querySelector('input[name=' + name + ']:checked') || {}).value || '';
  const persist = debounce(() => saveDraft({ issueType: selected('issueType'), severity: selected('severity'), description: description.value, roadName: roadName.value, address: address.value, lat: point && point.lat, lng: point && point.lng, clientRequestId: requestId }), 400);

  function setStatus(kind, text) {
    mount(statusLine, kind ? h('span', { class: 'status-pill ' + kind }, text) : null);
  }

  // ---- location ----
  function setPoint(lat, lng, zoomTo) {
    point = { lat, lng };
    coords.textContent = 'Selected: ' + fmtCoord(lat) + ', ' + fmtCoord(lng);
    locField.classList.remove('has-error'); coordError.textContent = '';
    if (map) {
      if (!marker) {
        marker = pickMarker([lat, lng], '!');
        marker.options.draggable = true;
        marker.addTo(map);
        marker.on('dragend', () => { const p = marker.getLatLng(); setPoint(p.lat, p.lng); });
      } else marker.setLatLng([lat, lng]);
      if (zoomTo) map.setView([lat, lng], Math.max(map.getZoom(), 16));
    }
    persist(); checkNearby();
  }
  const search = placeSearch({ placeholder: 'Search for a place or road to jump there…', onPick: (r) => setPoint(r.lat, r.lng, true) });
  const locateBtn = h('button', { class: 'btn', type: 'button', onclick: async () => {
    locateBtn.disabled = true;
    try { const p = await locate(); setPoint(p.lat, p.lng, true); toast('Location found (about ' + Math.round(p.accuracy) + ' m). Drag the pin to adjust.', 'success'); }
    catch (e) { toast(e.message + ' Click the map to choose the spot instead.', 'error'); }
    locateBtn.disabled = false;
  } }, icon('locate', 16), 'Use my location');

  // ---- photo (camera or file) ----
  const photoPreview = h('div', { class: 'hint' }, 'Optional. JPEG, PNG or WebP up to ' + Math.round(meta.image.maxBytes / 1048576) + ' MB. Large photos are resized automatically.');
  async function takePhoto(input) {
    photo = null; photoPreview.replaceChildren('Checking photo…');
    const f = input.files[0];
    if (!f) { photoPreview.textContent = 'Optional.'; return; }
    if (!meta.image.allowedTypes.includes(f.type)) { input.value = ''; showFieldErrors(form, { image: 'Only JPEG, PNG or WebP photos are allowed.' }); photoPreview.textContent = ''; return; }
    showFieldErrors(form, {});
    photo = await prepareImage(f);
    if (photo.size > meta.image.maxBytes) { photo = null; input.value = ''; showFieldErrors(form, { image: 'This photo is too large even after resizing. Choose a smaller one.' }); photoPreview.textContent = ''; return; }
    photoPreview.replaceChildren(h('img', { loading: 'lazy', decoding: 'async',  src: URL.createObjectURL(photo), alt: 'Preview of the selected photo', class: 'photo-preview' }),
      h('div', { class: 'hint' }, Math.round(photo.size / 1024) + ' KB · ', h('button', { class: 'link-btn', type: 'button', onclick: () => { photo = null; camera.value = ''; gallery.value = ''; photoPreview.textContent = 'Optional.'; } }, 'Remove')));
  }
  const accept = 'image/jpeg,image/png,image/webp';
  const camera = h('input', { type: 'file', accept, capture: 'environment', hidden: true, 'aria-label': 'Take a photo with the camera', onchange: () => takePhoto(camera) });
  const gallery = h('input', { type: 'file', name: 'image', accept, hidden: true, 'aria-label': 'Choose a photo from this device', onchange: () => takePhoto(gallery) });
  const photoButtons = h('div', { class: 'btn-row' },
    h('button', { class: 'btn', type: 'button', onclick: () => camera.click() }, icon('camera', 16), 'Take photo'),
    h('button', { class: 'btn', type: 'button', onclick: () => gallery.click() }, 'Choose from device'), camera, gallery);

  // ---- possible duplicates (shown BEFORE a second report is created) ----
  const checkNearby = debounce(async () => {
    const cat = selected('issueType');
    if (!point || !cat) { matches = []; renderMatches(); return; }
    try { matches = await api.get('/api/hazards/nearby', { lat: point.lat, lng: point.lng, category: cat }); } catch (e) { matches = []; }
    renderMatches();
  }, 500);

  function renderMatches(fromServer) {
    dupBox.hidden = matches.length === 0;
    if (!matches.length) { mount(dupBox); return; }
    mount(dupBox, h('div', { class: 'banner warn dup-box' },
      h('div', null, h('strong', null, 'A similar hazard is already reported here.'), h('p', { class: 'small' }, fromServer
        ? 'Please check whether it is the same one. Confirming it helps more than a second report.'
        : 'Check whether it is the same one. You can confirm it instead of reporting it again.')),
      matches.slice(0, 3).map((m) => h('div', { class: 'dup-item' },
        h('div', null, categoryBadge(meta, m.type, 18), ' ', h('strong', null, m.roadName || m.type), ' · ', fmtDistance(m.distanceMeters), ' away'),
        h('div', { class: 'muted small' }, severityBadge(m.severity), ' ', statusBadge(m.status), ' ', m.reportId + ' · ' + timeAgo(m.reportedAt) + ' · ' + m.stillThere + ' confirmed, ' + m.gone + ' say gone'),
        h('div', { class: 'btn-row' },
          h('button', { class: 'btn small primary', type: 'button', onclick: async (e) => {
            e.currentTarget.disabled = true;
            try { await api.post('/api/hazards/' + m.id + '/verify', { vote: 'STILL_THERE' }); toast('Thanks, your confirmation was recorded for ' + m.reportId + '.', 'success'); matches = []; renderMatches(); }
            catch (err) { toast(err.message, 'error'); e.currentTarget.disabled = false; }
          } }, 'It is still there (same hazard)'),
          h('a', { class: 'btn small', href: '/map?hazard=' + m.id, target: '_blank', rel: 'noopener' }, 'View')))),
      h('label', { class: 'toggle small' }, allowDup, 'This is a different hazard: let me submit a new report')));
  }

  // ---- rule-based suggestions (no AI model; always optional and editable) ----
  const suggestCat = h('button', { class: 'btn small', type: 'button', onclick: async () => {
    if (description.value.trim().length < 8) { toast('Write a few words about the problem first.', 'info'); return; }
    try {
      const s = await api.post('/api/suggest/category', { text: description.value });
      mount(suggestBox, h('div', null, h('strong', null, s.category ? 'Suggested category: ' + s.category : 'No suggestion'), h('p', { class: 'small muted' }, s.message + ' (' + s.method + ')'),
        h('div', { class: 'btn-row' }, s.category ? h('button', { class: 'btn small primary', type: 'button', onclick: () => { const r = container.querySelector('input[name=issueType][value="' + s.category + '"]'); if (r) { r.checked = true; r.dispatchEvent(new Event('change', { bubbles: true })); } suggestBox.hidden = true; } }, 'Use this category') : null,
          h('button', { class: 'btn small ghost', type: 'button', onclick: () => { suggestBox.hidden = true; } }, 'Dismiss'))));
      suggestBox.hidden = false;
    } catch (e) { toast(e.message, 'error'); }
  } }, 'Suggest a category');
  const draftBtn = h('button', { class: 'btn small', type: 'button', onclick: async () => {
    if (!selected('issueType')) { toast('Choose a category first.', 'info'); return; }
    try {
      const d = await api.post('/api/suggest/description', { category: selected('issueType'), severity: selected('severity'), roadName: roadName.value, landmark: address.value });
      mount(suggestBox, h('div', null, h('strong', null, 'Draft description'), h('p', { class: 'draft-text' }, d.text), h('p', { class: 'small muted' }, d.method + '. Nothing changes until you accept it, and you can edit it afterwards.'),
        h('div', { class: 'btn-row' }, h('button', { class: 'btn small primary', type: 'button', onclick: () => { description.value = description.value.trim() ? description.value.trim() + '\n' + d.text : d.text; description.dispatchEvent(new Event('input')); suggestBox.hidden = true; } }, 'Use this text'),
          h('button', { class: 'btn small ghost', type: 'button', onclick: () => { suggestBox.hidden = true; } }, 'Dismiss'))));
      suggestBox.hidden = false;
    } catch (e) { toast(e.message, 'error'); }
  } }, 'Draft a description');

  // ---- submit ----
  const submit = h('button', { class: 'btn primary', type: 'submit' }, icon('check', 16), 'Submit report');
  function setBusy(on) { busy = on; submit.disabled = on; submit.lastChild.textContent = on ? 'Submitting…' : 'Submit report'; }

  const form = h('form', { novalidate: true, onsubmit: async (e) => {
    e.preventDefault();
    if (busy) return;                                                  // a second click while uploading does nothing
    banner.hidden = true;
    const errors = {};
    if (!selected('issueType')) errors.issueType = 'Choose what kind of hazard this is.';
    if (description.value.trim().length < 10) errors.description = 'Please describe the hazard in at least 10 characters.';
    if (!point) errors.location = 'Choose the location of the hazard on the map.';
    form.querySelectorAll('.field').forEach((f) => f.classList.remove('has-error'));
    if (Object.keys(errors).length) {
      showFieldErrors(form, errors);
      if (errors.location) { locField.classList.add('has-error'); coordError.textContent = errors.location; }
      return;
    }
    const item = { id: requestId, createdAt: Date.now(), state: 'uploading', allowDuplicate: allowDup.checked, photo,
      fields: { issueType: selected('issueType'), severity: selected('severity'), description: description.value.trim(), roadName: roadName.value.trim(), address: address.value.trim(), lat: point.lat, lng: point.lng } };
    setBusy(true); setStatus('uploading', 'Uploading…');
    try {
      const c = await sendReport(item);
      clearDraft(); setStatus('done', 'Submitted');
      done(c);
    } catch (err) {
      setBusy(false);
      if (err instanceof ApiError && err.status === 409 && err.code === 'POSSIBLE_DUPLICATE') {
        matches = err.details && err.details.length ? err.details : matches; renderMatches(true);
        setStatus('', ''); banner.textContent = err.message; banner.hidden = false; dupBox.scrollIntoView && dupBox.scrollIntoView({ behavior: 'smooth', block: 'center' });
        return;
      }
      if (err instanceof ApiError && err.status === 0 || (err instanceof ApiError && err.status >= 500)) {
        if (queueSupported()) {
          try { await saveQueued({ ...item, state: 'saved', message: 'Waiting for a connection.' }); clearDraft(); setStatus('saved', 'Saved on this device'); savedOnDevice(); return; }
          catch (qe) { /* fall through to the normal error message */ }
        }
        setStatus('', ''); banner.textContent = 'Could not reach the server. Your form is kept: check your connection and press Submit again.'; banner.hidden = false; return;
      }
      setStatus('', ''); banner.textContent = err.message; banner.hidden = false;
      showFieldErrors(form, err.fieldErrors);
      if (err.fieldErrors && err.fieldErrors.location) { locField.classList.add('has-error'); coordError.textContent = err.fieldErrors.location; }
    }
  } },
  restored ? h('div', { class: 'banner info' }, h('span', null, 'Your unsent draft was restored from this device (the photo is not kept in drafts).'),
    h('button', { class: 'btn small', type: 'button', onclick: () => { clearDraft(); location.reload(); } }, 'Discard draft')) : null,
  banner,
  h('div', { class: 'card' }, h('h2', null, '1. What is the hazard?'),
    h('div', { class: 'field', 'data-field': 'issueType' }, category, h('div', { class: 'error-text', role: 'alert' })),
    h('div', { class: 'field', 'data-field': 'severity' }, h('span', { class: 'label-text' }, 'How serious is it?'), severity, h('div', { class: 'hint' }, 'High = immediate danger to road users; Low = an inconvenience.'), h('div', { class: 'error-text', role: 'alert' })),
    field('Short description', description, { hint: counter }),
    h('div', { class: 'btn-row' }, suggestCat, draftBtn), suggestBox),
  h('div', { class: 'card', style: 'margin-top:16px' }, h('h2', null, '2. Where is it?'),
    h('div', { class: 'btn-row', style: 'margin-bottom:10px' }, h('div', { style: 'flex:1;min-width:220px' }, search.el), locateBtn),
    mapNote, mapEl, locField,
    h('div', { class: 'form-grid', style: 'margin-top:12px' }, field('Road name or landmark (optional)', roadName), field('Address / nearby landmark', address))),
  dupBox,
  h('div', { class: 'card', style: 'margin-top:16px' }, h('h2', null, '3. Photo'),
    h('div', { class: 'field', 'data-field': 'image' }, photoButtons, photoPreview, h('div', { class: 'error-text', role: 'alert' }))),
  h('div', { class: 'btn-row', style: 'margin-top:16px' }, submit, h('a', { class: 'btn ghost', href: '/' }, 'Cancel'), statusLine),
  h('p', { class: 'hint', style: 'margin-top:10px' }, 'Reports are public hazard information on this site. Submitting here does not send anything to a government authority.'));
  locField.append(coords, coordError);
  form.addEventListener('input', persist); form.addEventListener('change', () => { persist(); checkNearby(); });
  description.addEventListener('input', () => { counter.textContent = description.value.length + ' / 2000'; });

  mount(container, pageHeader('Report a hazard', 'It takes about a minute. Your report appears on the map.'), form);

  if (mapsAvailable()) {
    map = createMap(mapEl, meta, { onTileError: () => { mapNote.hidden = false; } });
    map.on('click', (e) => setPoint(e.latlng.lat, e.latlng.lng));
    const lat = parseFloat(query.lat) || (restored ? draft.lat : NaN), lng = parseFloat(query.lng) || (restored ? draft.lng : NaN);
    if (Number.isFinite(lat) && Number.isFinite(lng)) setPoint(lat, lng, true);
  } else {
    mapNote.hidden = false; mapNote.textContent = 'The map library did not load. Use "Use my location" or the search box to set the location.';
  }

  function done(c) {
    mount(container, h('div', { class: 'card form-narrow', style: 'text-align:center' },
      h('div', { style: 'color:var(--resolved-fg)' }, icon('check', 48)),
      h('h1', null, 'Report submitted'),
      h('p', null, 'Your report ID is'),
      h('p', null, h('span', { class: 'ref', style: 'font-size:1.4rem' }, c.complaintId)),
      h('p', { class: 'muted' }, 'Status: ' + c.status + '. This is a community report on RoadGuard; it has not been sent to any authority. You will be notified when its status changes.'),
      h('div', { class: 'btn-row', style: 'justify-content:center' },
        h('a', { class: 'btn primary', href: '/complaints/' + c.id }, 'Track this report'),
        h('a', { class: 'btn', href: '/map?hazard=' + (c.id ? '' : '') }, 'See the map'),
        h('a', { class: 'btn ghost', href: '/report', onclick: () => setTimeout(() => location.reload(), 0) }, 'Report another'))));
    toast('Report ' + c.complaintId + ' submitted.', 'success');
    refreshUnread();
  }

  function savedOnDevice() {
    mount(container, h('div', { class: 'card form-narrow', style: 'text-align:center' },
      h('div', { style: 'color:var(--link)' }, icon('clock', 44)),
      h('h1', null, 'Saved on this device'),
      h('p', null, 'The server could not be reached, so your report and photo are stored on this device. They upload automatically when you are back online.'),
      h('div', { class: 'btn-row', style: 'justify-content:center' },
        h('button', { class: 'btn primary', type: 'button', onclick: async (e) => { e.currentTarget.disabled = true; const sent = await flushQueue(); if (sent.length) { toast('Submitted ' + sent[0].complaintId + '.', 'success'); navigate('/complaints'); } else { toast('Still offline. It will keep trying.', 'info'); e.currentTarget.disabled = false; } } }, 'Retry now'),
        h('a', { class: 'btn', href: '/complaints' }, 'My reports'))));
  }
  return () => { if (map) map.remove(); };
}
