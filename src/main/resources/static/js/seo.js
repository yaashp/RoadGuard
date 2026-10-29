// Per-page SEO: document title, description, robots, canonical link, Open Graph and Twitter tags, breadcrumb
// structured data, and a Google Analytics page view for every client-side route change. The server supplies the site
// URL and the default head tags in index.html (see SpaFilter on the Java side).

const LABELS = {
  map: 'Hazard map', report: 'Report a hazard', complaints: 'My reports', dashboard: 'Dashboard', impact: 'Impact score',
  community: 'Community', posts: 'Post', users: 'Profile', repairs: 'Fixed recently', repair: 'Repair',
  planner: 'Safe routes', saved: 'Saved routes', history: 'Road history', notifications: 'Notifications',
  settings: 'Settings', admin: 'Admin', login: 'Log in', register: 'Create an account', privacy: 'Privacy policy',
  terms: 'Terms of service', contact: 'Contact',
};

// Order matters: the first match wins. Private or personal pages are marked noindex.
const PAGES = [
  { match: (p) => p === '/', title: 'RoadGuard · Report road hazards and check route safety',
    description: 'Report potholes and other road hazards, track their repair, explore a live hazard map and compare route safety.' },
  { match: (p) => p === '/report', title: 'Report a road hazard · RoadGuard',
    description: 'Report a pothole, flooding, a broken signal or another road hazard, with its location and a photo.' },
  { match: (p) => p === '/map', title: 'Hazard map · RoadGuard',
    description: 'Explore reported road hazards on an interactive map, filtered by type, severity and status.' },
  { match: (p) => p === '/planner', title: 'Safe routes · RoadGuard', noindex: true,
    description: 'Compare driving routes by the road hazards reported along each one and an estimated safety score.' },
  { match: (p) => p === '/repairs', title: 'Fixed recently · RoadGuard',
    description: 'Road hazards that were recently repaired, with before and after evidence.' },
  { match: (p) => /^\/repair\/[^/]+$/.test(p), title: 'Repaired road hazard · RoadGuard',
    description: 'Before and after evidence for a road hazard that has been repaired.' },
  { match: (p) => p === '/community', title: 'Community · RoadGuard',
    description: 'Discuss potholes, accidents and traffic with neighbours and share road safety updates.' },
  { match: (p) => /^\/community\/posts\/[^/]+$/.test(p), title: 'Community post · RoadGuard',
    description: 'A road safety discussion from the RoadGuard community.' },
  { match: (p) => /^\/community\/users\/[^/]+$/.test(p), title: 'Community profile · RoadGuard', noindex: true,
    description: 'Community activity from a RoadGuard member.' },
  { match: (p) => p === '/impact', title: 'Impact score · RoadGuard',
    description: 'See how reporting road hazards helps make your roads safer.' },
  { match: (p) => p === '/history', title: 'Road history · RoadGuard',
    description: 'Past road hazard reports, trends and repeat problems for each road.' },
  { match: (p) => p === '/contact', title: 'Contact us · RoadGuard',
    description: 'Send a question, suggestion or problem report to the RoadGuard team.' },
  { match: (p) => p === '/privacy', title: 'Privacy policy · RoadGuard',
    description: 'How RoadGuard collects, uses and protects the information you share with us.' },
  { match: (p) => p === '/terms', title: 'Terms of service · RoadGuard',
    description: 'The rules for using RoadGuard, including reports, community posts and route estimates.' },
  { match: (p) => p === '/login', title: 'Log in · RoadGuard', noindex: true,
    description: 'Log in to report road hazards and follow their repair.' },
  { match: (p) => p === '/register', title: 'Create an account · RoadGuard', noindex: true,
    description: 'Create a free RoadGuard account to report road hazards and track repairs.' },
  { match: (p) => p === '/dashboard', title: 'Dashboard · RoadGuard', noindex: true,
    description: 'Your personal road safety dashboard.' },
  { match: (p) => p.startsWith('/complaints'), title: 'My reports · RoadGuard', noindex: true,
    description: 'Track the status of the road hazard reports you have submitted.' },
  { match: (p) => p === '/saved', title: 'Saved routes · RoadGuard', noindex: true,
    description: 'Your saved routes and their latest safety estimates.' },
  { match: (p) => p === '/notifications', title: 'Notifications · RoadGuard', noindex: true,
    description: 'Updates on your road hazard reports.' },
  { match: (p) => p === '/settings', title: 'Settings · RoadGuard', noindex: true,
    description: 'Your RoadGuard account and preferences.' },
  { match: (p) => p === '/admin', title: 'Admin console · RoadGuard', noindex: true,
    description: 'RoadGuard administration.' },
];
const FALLBACK = { title: 'Page not found · RoadGuard', description: 'This page does not exist on RoadGuard.', noindex: true };

/** Breadcrumb trail for a path: Home, then one item per segment. */
export function crumbsFor(path) {
  const segments = path.split('/').filter(Boolean);
  const items = [{ name: 'Home', path: '/' }];
  segments.forEach((seg, i) => {
    items.push({ name: LABELS[seg] || 'Details', path: '/' + segments.slice(0, i + 1).join('/') });
  });
  return items;
}

function siteUrl() {
  const m = document.querySelector('meta[name="rg-site-url"]');
  return ((m && m.content) || location.origin).replace(/\/$/, '');
}

function meta(attr, key, value) {
  let el = document.head.querySelector('meta[' + attr + '="' + key + '"]');
  if (!el) { el = document.createElement('meta'); el.setAttribute(attr, key); document.head.appendChild(el); }
  el.setAttribute('content', value);
}

function canonical(href) {
  let el = document.head.querySelector('link[rel="canonical"]');
  if (!el) { el = document.createElement('link'); el.rel = 'canonical'; document.head.appendChild(el); }
  el.href = href;
}

function jsonLd(id, data) {
  let el = document.getElementById(id);
  if (!data) { if (el) el.remove(); return; }
  if (!el) { el = document.createElement('script'); el.type = 'application/ld+json'; el.id = id; document.head.appendChild(el); }
  el.textContent = JSON.stringify(data);
}

/** Updates the document head for the page at `path`. Called by the router before each page renders. */
export function applySeo(path) {
  const page = PAGES.find((p) => p.match(path)) || FALLBACK;
  const base = siteUrl();
  const url = base + (path === '/' ? '/' : path);
  document.title = page.title;
  meta('name', 'description', page.description);
  meta('name', 'robots', page.noindex ? 'noindex,nofollow' : 'index,follow');
  canonical(url);
  meta('property', 'og:title', page.title);
  meta('property', 'og:description', page.description);
  meta('property', 'og:url', url);
  meta('name', 'twitter:title', page.title);
  meta('name', 'twitter:description', page.description);

  const crumbs = crumbsFor(path);
  jsonLd('rg-breadcrumbs', crumbs.length > 1 ? {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: crumbs.map((c, i) => ({ '@type': 'ListItem', position: i + 1, name: c.name, item: base + (c.path === '/' ? '/' : c.path) })),
  } : null);

  if (typeof window.gtag === 'function') window.gtag('event', 'page_view', { page_title: page.title, page_location: url, page_path: path });
}
