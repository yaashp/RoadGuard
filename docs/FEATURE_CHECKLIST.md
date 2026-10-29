# Feature checklist (honest status)

Legend: **Done** = implemented and exercised by a check I actually ran (named in brackets) · **Done, not executed** = implemented but not run · **Partial** · **Not done**.

The project ships no automated tests or test scripts. Checks named below were run by me during development, outside the project:
`API` an end-to-end script against the running app · `FE` the real frontend code run in a simulated browser (jsdom) against the running app ·
`UP` upgrade of a database created by the ORIGINAL app · `RS` restart/persistence check · `DEP` start from environment variables the way a server would.

## 1-2 Technical rules, dashboard
| Item | Status |
|---|---|
| Java 17 / Spring Boot 3.3.5 / Maven / JPA / H2 file DB kept; no other backend | Done (`pom.xml` only gained the test starter) |
| Existing functionality preserved (auth, complaints, road issues, legacy endpoints and JSON body) | Done [API, UP] |
| Real backend APIs, no static-only demo; sample data never presented as real | Done [API, FE] - sample records flagged everywhere |
| No hard-coded keys/secrets; env vars; `.env.example` | Done (no key is needed by any integration) |
| Controllers / services / repositories / DTOs / config / central exception handling | Done |
| Dashboard: totals, open, under review, resolved, high severity, recent reports, recent activity, road condition stats, status + category distribution, weekly + monthly trends, interactive charts, quick actions, loading/empty/error states | Done [API, FE] (Chart.js hover tooltips; charts are not drill-down) |
| Statistics computed from database records | Done [API: counts grow by exactly the complaints created] |

## 3 Authentication & profiles
Register, login, logout, current user, profile edit, password change, user/admin roles, session management, protected pages, form validation, clear errors, BCrypt: **Done** [API, FE].
Server-side authorization (a normal user cannot call admin APIs): **Done** [API: 22 admin-route checks, FE]. CSRF protection: **Done** [API]. Google login: **Not done** (optional; would need real OAuth credentials).

## 4 Reporting
All 10 categories, description, severity, map location + lat/lng, address/road name, optional photo, confirmation, unique reference number, client + server validation, stored per authenticated user: **Done** [API, FE].
Photo: type by content, size, pixels, file-name rules, random stored name, configurable directory: **Done** [API: 7 rejection cases]. "Current location" button: **Done, not executed** (needs a real browser's geolocation).

## 5 Complaint tracking
List, details, search, filters (status/severity/category/date), dates, locations, photo, progress tracker, persisted status history, resolved vs unresolved view, statuses Submitted / Under Review / Assigned / In Progress / Resolved / Rejected, readable references, ownership enforced: **Done** [API, FE].

## 6 Interactive map
Real pins from the database, category icons, severity rings, status styling, popups, filters, search by road/type and by place, locate-me, click-to-select location, legend, loading/error states, responsive layout, Leaflet + OpenStreetMap tiles with attribution, provider isolated in `js/maps.js`: **Done** [FE with real Leaflet in jsdom: pins, filters, legend, attribution]. Real tile loading and visual layout: **not seen** (no browser, no internet in my sandbox).

## 7 Hazard-aware route planner
Start/destination (search or map click), real route geometry from OSRM with alternatives, distance, provider-supplied duration, hazards per route, severity-weighted exposure, 0-100 score, Safe/Moderate/Risky, visual comparison (coloured routes, score bars, table), explanation of the score, no double counting, graceful outage handling, clear disclaimer: **Done** [API, FE].
**Caveat:** the routing and place-search calls were verified against local stand-ins that return the documented OSRM / Nominatim response formats - **not against the real public servers**, which my sandbox could not reach.

## 8 Saved routes
Save with a name, origin/destination, list, reopen in the planner, rename, delete, latest vs previous exposure, per-user isolation, stored in the database: **Done** [API: 404 for every cross-user attempt, FE].

## 9 Road history & analytics
Road search, per-road details and complaint history, counts, severity trends, weekly + monthly charts, resolution statistics (from recorded status changes only), period comparison, hotspots, repeat problems, recent change in volume, real vs sample clearly separated, not presented as official records: **Done** [API, FE]. Separate `RoadHistory` entity: **intentionally not created** (derived from complaints + history).

## 10 Administrator
Overview, statistics + charts, complaint list with search/filters/paging, detail with location/photo/timeline, status + severity management with confirmation dialogs, validated transitions, who/when recorded, road-issue management, all enforced on the server: **Done** [API, FE].

## 11 Notifications
List, unread count, mark read / all, submission + status + severity notifications, admin notification of new complaints, per-user preferences honoured, persisted: **Done** [API, FE]. E-mail/SMS/push: **Not done** (in-app only; nothing claims otherwise).

## 12 Settings
Light/dark/system theme (stored per user, plus a local cache for first paint), profile, notification and map preferences, account info, logout: **Done** [API, FE].

## 13 UI / UX
Slate + sky palette from your specification (dark by default; page `slate-950`, cards `slate-900`, borders `slate-800`, headings `slate-100`, muted text `slate-400`, accent `sky-500` with `slate-950` text and `sky-600` hover, technical data `cyan-400` in monospace,
Pending = sky, Critical = rose, Resolved = teal badges; a light variant exists in Settings). Consistent components, cards, toasts, confirmation dialogs, skeletons, empty states, tooltips, responsive CSS, skip link, focus outlines, reduced-motion support: **Done, not executed visually**.
Notes: Tailwind itself is not used (no build step) - its colour values are plain CSS variables. A few functional colours outside your list are used where three levels must be told apart (amber for Medium severity / Moderate routes; map pins keep category colours).
Home page background video (`media/highway-traffic.mp4`, muted, loops, pause control, respects reduced motion): **Done** [FE: video element present, muted, looping, pause control; video served with byte-range support].
**I never looked at any page, or the video, in a real browser on a phone**, so how it actually looks, performs and fits on phones is untested by eye.

## 14-16 API, database, keys, deployment
Consistent `/api` REST endpoints, DTOs, validation, JSON errors, pagination where it matters (admin complaints, notifications): **Done** [API].
Additive schema, existing records preserved, restart-safe, idempotent seeder: **Done** [UP, RS].
Deployment: optional bootstrap administrator, HTTPS-proxy support, `docs/DEPLOYMENT.md` (JAR + systemd, reverse proxy, build-from-source platforms): **Done** [DEP: env-var start, no demo data, admin bootstrap, Secure cookies]. The JAR build, the systemd unit and any hosting platform: **not executed**.

## 17-18 Quality & verification
Layered architecture, documented scoring, externalised config: **Done**.
Not possible here: running **Maven** itself (Maven Central is unreachable from my sandbox). I compiled with `javac -Xlint:all` against the exact Spring Boot 3.3.5 jars (taken from another application's jar), which gives real compile and runtime checks, but with **Jetty instead of Tomcat** and **H2 2.1.214 instead of the 2.2.224 that Boot 3.3.5 manages**. `mvn package` and dependency resolution on your machine are therefore unverified by me.
No automated tests or test scripts are shipped.

## Known limitations / unfinished
- No Content-Security-Policy (tile host is configurable; UI uses inline style attributes).
- Rate limiting and the place-search cache are in-memory (reset on restart, per instance).
- The map loads every pin (no bounding-box query / clustering); analytics are computed in Java over all complaints.
- Road grouping is by road-name text, not by geometry; two different roads with the same name are merged.
- Duplicate reports of the same hazard are merged only for route scoring (15 m, same category), not on the map.
- Roads/hazards outside the area you report in simply have no data; the score says nothing about them.
- Accessibility was designed in (labels, focus, ARIA) but not audited with a screen reader.
- Demo credentials are documented in the README and shown on the login page when served from localhost; set `SEED_ENABLED=false` for any real deployment.
