// Place search box (backed by /api/geocode, which proxies Nominatim with caching and rate limits).
import { api } from './api.js';
import { h, debounce, clear, icon } from './ui.js';

export function placeSearch({ placeholder = 'Search for a place or road…', onPick, label = 'Search places' }) {
  const input = h('input', { type: 'search', placeholder, 'aria-label': label, autocomplete: 'off' });
  const results = h('div', { class: 'search-results', hidden: true, role: 'listbox' });
  const wrap = h('div', { class: 'relative' }, input, results);
  let seq = 0;

  const show = (nodes) => { clear(results); nodes.forEach((n) => results.appendChild(n)); results.hidden = nodes.length === 0; };
  const note = (text) => h('div', { class: 'muted small', style: 'padding:9px 11px' }, text);

  const run = debounce(async () => {
    const q = input.value.trim();
    if (q.length < 3) { show([]); return; }
    const mine = ++seq;
    show([note('Searching…')]);
    try {
      const list = await api.get('/api/geocode', { q });
      if (mine !== seq) return;
      if (!list.length) { show([note('No places found.')]); return; }
      show(list.map((r) => h('button', { type: 'button', role: 'option', onclick: () => { input.value = r.label; show([]); onPick(r); } }, icon('map', 14), ' ', r.label)));
    } catch (e) {
      if (mine === seq) show([note(e.message || 'Place search is unavailable right now.')]);
    }
  }, 600);

  input.addEventListener('input', run);
  input.addEventListener('keydown', (e) => { if (e.key === 'Escape') show([]); });
  document.addEventListener('click', (e) => { if (!wrap.contains(e.target)) results.hidden = true; });
  return { el: wrap, input, clear: () => { input.value = ''; show([]); } };
}
