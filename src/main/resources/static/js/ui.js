// DOM helpers. All user-supplied text goes through textContent (never innerHTML), so stored
// descriptions, names or road names cannot inject markup.

export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === false || v === null || v === undefined) continue;
    if (k === 'class') el.className = v;
    else if (k === 'dataset') Object.assign(el.dataset, v);
    else if (k === 'value') el.value = v;
    else if (k === 'checked' || k === 'disabled' || k === 'selected' || k === 'hidden' || k === 'required') { if (v) el[k] = true; }
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const c of children.flat(Infinity)) {
    if (c === null || c === undefined || c === false) continue;
    el.appendChild(c instanceof Node ? c : document.createTextNode(String(c)));
  }
}

export function clear(el) { while (el.firstChild) el.removeChild(el.firstChild); return el; }
export function mount(el, ...children) { clear(el); append(el, children); return el; }

// ---- icons (static markup only) ----
const ICONS = {
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  caret: '<path d="m6 9 6 6 6-6"/>',
  home: '<path d="M3 11 12 3l9 8v10h-6v-6H9v6H3V11Z"/>',
  alert: '<path d="M12 3 2 20h20L12 3Zm0 6v5m0 3v.01"/>',
  map: '<path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Zm0 0v14m6-12v14"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
  route: '<circle cx="6" cy="18" r="2.5"/><circle cx="18" cy="6" r="2.5"/><path d="M8.5 18H15a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h6.5"/>',
  chart: '<path d="M4 20V10m6 10V4m6 16v-8m6 8H2"/>',
  shield: '<path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6l-8-3Zm-3 9 2 2 4-4"/>',
  bell: '<path d="M6 9a6 6 0 1 1 12 0c0 6 2 7 2 7H4s2-1 2-7Zm4 10a2 2 0 0 0 4 0"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M19 5l-2 2M7 17l-2 2"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5M17.5 17.5 19 19M19 5l-1.5 1.5M6.5 17.5 5 19"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4 6.5 6.5 0 0 0 20 14.5Z"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  check: '<path d="m5 12 5 5 9-10"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  locate: '<circle cx="12" cy="12" r="3"/><circle cx="12" cy="12" r="8"/><path d="M12 1v3m0 16v3M1 12h3m16 0h3"/>',
  camera: '<path d="M4 8h3l2-3h6l2 3h3v11H4V8Z"/><circle cx="12" cy="13" r="3.5"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  trash: '<path d="M4 7h16M9 7V4h6v3m-8 0 1 13h8l1-13"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16v4Zm9-13 4 4"/>',
  swap: '<path d="M7 4 3 8l4 4M3 8h14m0 4 4 4-4 4m4-4H7"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-9.5v.01"/>',
  arrowUp: '<path d="M12 19V5m-6 6 6-6 6 6"/>',
  arrowDown: '<path d="M12 5v14m-6-6 6 6 6-6"/>',
  logout: '<path d="M9 4H5v16h4m5-4 4-4-4-4m4 4H9"/>',
};

export function icon(name, size = 18) {
  const span = document.createElement('span');
  span.className = 'icon';
  span.setAttribute('aria-hidden', 'true');
  span.innerHTML = '<svg viewBox="0 0 24 24" width="' + size + '" height="' + size
    + '" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + (ICONS[name] || '') + '</svg>';
  return span;
}

// ---- badges ----
const SEV = { High: 'sev-high', Medium: 'sev-medium', Low: 'sev-low' };
const STATUS = { 'Submitted': 'st-submitted', 'Under Review': 'st-review', 'Action Planned': 'st-assigned', 'Repair in Progress': 'st-progress', 'Resolved': 'st-resolved', 'Rejected': 'st-rejected', 'Duplicate': 'st-rejected' };

export const severityBadge = (s) => h('span', { class: 'badge ' + (SEV[s] || '') }, s);
export const statusBadge = (s) => h('span', { class: 'badge ' + (STATUS[s] || '') }, s);
export const sampleBadge = () => h('span', { class: 'badge sample', 'data-tip': 'Demonstration record created by the seeder. Not a real report.' }, 'Sample data');
export const classBadge = (c) => h('span', { class: 'badge class-' + String(c).toLowerCase() }, c);

// ---- toasts ----
export function toast(message, kind = 'info', ms = 4500) {
  let box = document.getElementById('toasts');
  if (!box) { box = h('div', { id: 'toasts', 'aria-live': 'polite', role: 'status' }); document.body.appendChild(box); }
  const t = h('div', { class: 'toast ' + kind }, icon(kind === 'error' ? 'alert' : kind === 'success' ? 'check' : 'info'), h('span', null, message));
  box.appendChild(t);
  setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 300); }, ms);
}

// ---- dialogs ----
function dialogShell(title, body, actions) {
  const overlay = h('div', { class: 'overlay', role: 'dialog', 'aria-modal': 'true', 'aria-label': title });
  const card = h('div', { class: 'dialog' }, h('h3', null, title), body, h('div', { class: 'dialog-actions' }, actions));
  overlay.appendChild(card);
  document.body.appendChild(overlay);
  const close = () => { overlay.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
  return { overlay, card, close };
}

/** Resolves true when the user confirms. */
export function confirmDialog({ title, message, confirmText = 'Confirm', danger = false }) {
  return new Promise((resolve) => {
    let done = false;
    const finish = (v) => { if (!done) { done = true; dlg.close(); resolve(v); } };
    const ok = h('button', { class: 'btn ' + (danger ? 'danger' : 'primary'), type: 'button', onclick: () => finish(true) }, confirmText);
    const cancel = h('button', { class: 'btn ghost', type: 'button', onclick: () => finish(false) }, 'Cancel');
    const dlg = dialogShell(title, h('p', null, message), [cancel, ok]);
    dlg.overlay.addEventListener('mousedown', (e) => { if (e.target === dlg.overlay) finish(false); });
    ok.focus();
  });
}

/** Generic modal with arbitrary content; returns {close}. */
export function modal(title, content, { wide = false } = {}) {
  const closeBtn = h('button', { class: 'btn ghost', type: 'button' }, 'Close');
  const dlg = dialogShell(title, content, [closeBtn]);
  if (wide) dlg.card.classList.add('wide');
  closeBtn.addEventListener('click', dlg.close);
  return dlg;
}

// ---- states ----
export function skeleton(lines = 4) {
  const box = h('div', { class: 'skeleton-box', 'aria-busy': 'true', 'aria-label': 'Loading' });
  for (let i = 0; i < lines; i++) box.appendChild(h('div', { class: 'skeleton', style: 'width:' + (60 + ((i * 17) % 40)) + '%' }));
  return box;
}

export function skeletonCards(n = 4) {
  return h('div', { class: 'stat-grid' }, Array.from({ length: n }, () => h('div', { class: 'card stat' }, h('div', { class: 'skeleton', style: 'width:50%;height:12px' }), h('div', { class: 'skeleton', style: 'width:35%;height:28px;margin-top:10px' }))));
}

export function emptyState(title, text, action) {
  return h('div', { class: 'empty' }, icon('info', 28), h('h4', null, title), text ? h('p', null, text) : null, action || null);
}

export function errorState(err, retry) {
  return h('div', { class: 'empty error' }, icon('alert', 28), h('h4', null, 'Something went wrong'),
    h('p', null, err && err.message ? err.message : 'Please try again.'),
    retry ? h('button', { class: 'btn', type: 'button', onclick: retry }, 'Try again') : null);
}

// ---- forms ----
export function field(label, input, { error, hint, id } = {}) {
  const wrap = h('div', { class: 'field' + (error ? ' has-error' : '') });
  if (id) input.id = id;
  wrap.append(h('label', id ? { for: id } : null, label), input);
  if (hint) wrap.append(h('div', { class: 'hint' }, hint));
  wrap.append(h('div', { class: 'error-text', role: 'alert' }, error || ''));
  return wrap;
}

/** Show server-side field errors under the matching inputs (inputs carry name="..."). */
export function showFieldErrors(form, errors) {
  form.querySelectorAll('.field').forEach((f) => { f.classList.remove('has-error'); const e = f.querySelector('.error-text'); if (e) e.textContent = ''; });
  let first = null;
  Object.entries(errors || {}).forEach(([name, msg]) => {
    const input = form.querySelector('[name="' + name + '"]');
    const wrap = input ? input.closest('.field') : form.querySelector('[data-field="' + name + '"]');
    if (wrap) {
      wrap.classList.add('has-error');
      const e = wrap.querySelector('.error-text');
      if (e) e.textContent = msg;
      if (!first) first = input || wrap;
    }
  });
  if (first && first.focus) first.focus();
  return Object.keys(errors || {}).length > 0;
}

export function debounce(fn, ms = 300) {
  let t;
  return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
}

export function pageHeader(title, subtitle, ...actions) {
  return h('div', { class: 'page-head' }, h('div', null, h('h1', null, title), subtitle ? h('p', { class: 'muted' }, subtitle) : null), h('div', { class: 'page-actions' }, actions));
}
