import { h } from '../ui.js';

/** Safe Routes has two views: plan a route, and your saved routes. */
export function routeTabs(active) {
  return h('div', { class: 'tabs', role: 'tablist', 'aria-label': 'Safe routes' },
    h('a', { class: 'tab' + (active === 'plan' ? ' active' : ''), role: 'tab', 'aria-selected': String(active === 'plan'), href: '/planner' }, 'Plan a route'),
    h('a', { class: 'tab' + (active === 'saved' ? ' active' : ''), role: 'tab', 'aria-selected': String(active === 'saved'), href: '/saved' }, 'Saved routes'));
}
