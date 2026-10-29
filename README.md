# RoadGuard (Spring Boot)

A road-safety and complaint-management web app: citizens report road hazards (with a photo), follow each complaint's
timeline, explore a live hazard map, and compare routes by the reported hazards along them; administrators manage
complaints and map data. Backend: **Java 17 · Spring Boot 3.3.5 · Spring Data JPA/Hibernate · H2 (file) · Maven**.
Frontend: plain HTML/CSS/JavaScript (ES modules) served by Spring Boot, with Leaflet (maps) and Chart.js (charts)
bundled locally in `static/vendor/` - no CDN and no build step. Look and feel: dark slate theme with sky-blue accents (Tailwind palette values written as CSS variables in `static/css/app.css`; a light variant is available in Settings).

## Run it

Prerequisites: **JDK 17 or newer** and **Maven 3.9+** (`java -version` and `mvn -v` must both work in a fresh terminal).

```
cd roadguard-springboot          # the folder that contains pom.xml
mvn spring-boot:run
```

Open <http://localhost:8080>. Demo accounts (created on first start, see "Sample data"):

| Role | E-mail | Password |
|---|---|---|
| Citizen | `demo@roadguard.app` | `password123` |
| Administrator | `admin@roadguard.app` | `admin123` |

**In VS Code**: do not use the editor's ▷ "Run" button on `RoadGuardApplication.java` unless the Java extension has imported the
project as a Maven project (it then shows "Run | Debug" above `main`). The plain ▷ button treats the file as a single script and reports
*"Main method not found in the file"*. Use the terminal command above, or open the folder that contains `pom.xml`, install
"Extension Pack for Java" + "Spring Boot Extension Pack", run *Java: Clean Java Language Server Workspace*, and use the Spring Boot Dashboard.

Other command: `mvn package`, then `java -jar target/roadguard-0.0.1-SNAPSHOT.jar`.

## What is in it

| Area | Where to find it |
|---|---|
| Home: background highway traffic video (`static/media/highway-traffic.mp4`, stock footage, muted, with pause control). Dashboard (command center): totals, status/category/severity charts, weekly + monthly trends, recent reports and activity, roads needing attention | `#/` · `GET /api/dashboard` |
| Accounts: register, login/logout, profile, password change, roles, protected pages | `#/login` `#/register` `#/settings` · `/api/auth/*`, `/api/users/me/*` |
| Reporting: 10 categories, severity, description, map pin, lat/lng, road/address, optional photo, reference number `RG-YYYY-NNNNN` | `#/report` · `POST /api/complaints` |
| Complaint tracking: search + filters, progress tracker, persisted status history, photo | `#/complaints` · `/api/complaints/*` |
| Interactive map: category icons, severity rings, status styling, popups, filters, search, locate me, click-to-report, legend | `#/map` · `GET /api/road-issues` |
| Hazard-aware route planner: real route geometry (OSRM), alternatives, distance/time, hazards per route, severity-weighted score, Safe/Moderate/Risky | `#/planner` · `POST /api/routes/analyze` |
| Saved routes: save, list, reopen, rename, delete, re-check with previous-vs-latest comparison | `#/saved` · `/api/saved-routes/*` |
| Road history: road search, hotspots, per-road weekly/monthly/severity charts, repeat problems, resolution stats, period comparison | `#/history` · `/api/roads/*` |
| Administrator: overview, complaint list/detail, status + severity changes with timeline and confirmation dialogs, map-pin management | `#/admin` · `/api/admin/*` |
| In-app notifications (bell, unread count, mark read / all) | `#/notifications` · `/api/notifications/*` |
| Settings: light/dark theme, notification and map preferences, account info, logout | `#/settings` |

## Configuration (all optional; no API key is needed)

`application.properties` reads these OS environment variables (see `.env.example`; **Spring Boot does not load a `.env` file** - set them in your shell or IDE run configuration):
`PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `H2_CONSOLE_ENABLED`, `SESSION_COOKIE_SECURE`, `UPLOAD_DIR`, `MAP_TILE_URL`, `MAP_ATTRIBUTION`,
`ROUTING_BASE_URL`, `GEOCODER_BASE_URL`, `GEOCODER_CONTACT_EMAIL`, `HTTP_USER_AGENT`, `ROADGUARD_TIMEZONE`, `SEED_ENABLED`, `SEED_DEMO_PASSWORD`, `SEED_ADMIN_PASSWORD`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `FORWARD_HEADERS_STRATEGY`.
The scoring model has its own properties (`roadguard.scoring.*`, see below).

**External services used (none needs a key):**
* **Map tiles** - OpenStreetMap standard tiles (`https://tile.openstreetmap.org`). Attribution is shown on every map. OSM's tile servers are for light use; for a
  public deployment set `MAP_TILE_URL` to a provider you are allowed to use and keep its attribution. The browser map code is isolated in `static/js/maps.js`.
* **Routing** - an OSRM-compatible service. The default is the public demo server `router.project-osrm.org` (fair-use, no guarantees). Self-host OSRM and set `ROUTING_BASE_URL` for anything serious.
  To use another provider implement `routing/RoutingProvider` (any API key must come from an environment variable, never from source).
* **Place search** - Nominatim. The backend identifies itself with a User-Agent, caches answers, sends at most one request per second and rate-limits each client. Set `GEOCODER_CONTACT_EMAIL` as Nominatim's policy asks.

If routing or place search is unreachable the app says so (HTTP 503 `ROUTING_UNAVAILABLE` / `GEOCODER_UNAVAILABLE`); everything else keeps working and no fake route is ever drawn.

## Security model

* **Sessions**: server-side HTTP session; cookie is `HttpOnly`, `SameSite=Lax`, `Secure` when `SESSION_COOKIE_SECURE=true`; a new session id is issued at login/registration and after a password change.
* **Passwords**: BCrypt; policy 8-64 characters with a letter and a number. 5 wrong passwords per e-mail (or 30 per IP) in 10 minutes -> HTTP 429. Unknown e-mails get the same error and similar timing as wrong passwords.
* **Authorization is enforced on the server**: every `/api` controller or method carries `@Access(PUBLIC | USER | ADMIN)`, checked by `AccessInterceptor` before the controller runs. **An endpoint without an annotation is refused** (fail closed).
  Users only ever see their own complaints / saved routes / notifications: lookups include the owner id, and another user's id answers "not found". There is no endpoint that takes a user id.
* **CSRF**: double-submit cookie. Every response sets `XSRF-TOKEN`; every POST/PUT/PATCH/DELETE under `/api` must send it back as `X-XSRF-TOKEN` (the web app does this automatically).
* **Uploads**: type is decided from the file's bytes (JPEG/PNG/WebP magic numbers), not the name or header; declared type and extension must agree; size <= 5 MB, pixel count limited; stored outside the web root under a random server-generated name;
  served only through an authorised endpoint with `X-Content-Type-Options: nosniff`. The web form also re-encodes photos to <= 1600 px JPEG, which drops hidden metadata such as GPS tags.
* **XSS**: the UI builds DOM with `textContent` (never `innerHTML` for stored data). **Headers**: `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `Cache-Control: no-store` on `/api`.
* **Changed defaults vs the original project** (for safety): the H2 web console is **off** (enable with `H2_CONSOLE_ENABLED=true`), and `AUTO_SERVER=TRUE` was removed from the JDBC URL because it opened the (empty-password) database to the network.
  Error responses never include stack traces.

## Route safety score

`score = 100 - sum(penalty)` over **distinct open reported hazards** within 60 m of the real route line, clamped to 0-100.
`penalty = severity x proximity x status`: severity Low/Medium/High = 2/5/10; proximity = 1.0 within 20 m, falling linearly to 0.5 at 60 m; status = 0.75 if "In Progress" else 1.0.
Resolved and rejected reports are ignored. Each hazard gets one distance (its shortest distance to any route segment), so overlapping segments never count it twice; reports of the same category within 15 m of each other are merged into one hazard.
Safe >= 80, Moderate >= 50, otherwise Risky. All numbers are configurable under `roadguard.scoring.*` and every response includes a plain-language explanation of how its score arose.
**It is an estimate built only from reports in this system (including labelled sample data if included). It does not measure road condition, traffic or personal safety and guarantees nothing.**

## Data, migration and sample data

* H2 file database at `./data/roadguard` (override with `DB_URL`); `ddl-auto=update` only **adds** tables/columns/indexes - nothing is dropped or rewritten. New columns on existing tables are nullable.
* New tables: `complaint_status_history`, `saved_routes`, `notifications`, `user_preferences`. New columns on `users`, `complaints`, `road_issues`.
* On start `DataSeeder` (idempotent) upgrades old rows: demo complaints are labelled `SAMPLE`, real ones `USER`; every complaint without history gets one "Imported" entry (history before this version genuinely was not recorded);
  real complaints get map pins; legacy pin types are normalised (`Construction` -> `Road Construction`, `Hazard` -> `Traffic Hazard`, `Resolved` -> `Other`); old base64 photos stay readable.
* **Sample data is always labelled** ("Sample data" badge, `sample: true` in the API, hatched pins) and every chart/map/planner has an "include sample data" switch. Sample complaint references look like `RG-S-0001`.
  Set `SEED_ENABLED=false` for a clean database with no demo accounts. To start over, stop the app and delete the `data/` folder.
* A *RoadHistory* table was deliberately not created: road history is derived from the complaints and their status history, which already hold the facts.
  Roads are grouped by road name (the first comma-separated part of the road/address), a practical approximation.

## Deploy

**`docs/DEPLOYMENT.md`** explains how to run it on a server (JAR + systemd), behind HTTPS with Caddy or nginx, or on a platform that builds Maven projects. In short: one instance only,
persistent storage for the database and uploads, HTTPS in front. For a real server set `SEED_ENABLED=false` and create the first administrator with `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`.

## Project layout

```
src/main/java/com/roadguard
  config/      properties, WebConfig, BCrypt bean, DataSeeder
  controller/  thin HTTP layer (one class per area)
  dto/         request/response records (entities are never returned directly)
  exception/   ApiException, validation helper, one JSON error format
  geo/         distance maths, road-name keys
  model/       JPA entities + enums (status transitions live in ComplaintStatus)
  repository/  Spring Data repositories (derived queries + one Specification)
  routing/     RoutingProvider interface + OSRM implementation
  security/    @Access, AccessInterceptor, SessionAuth, CsrfFilter, SecurityHeadersFilter, rate-limit counter
  service/     business logic (complaints, route safety/planner, dashboard, history, notifications, uploads ...)
src/main/resources/static     index.html, css/app.css, js/ (api, router, ui, maps, charts, pages/*), media/ (home-page video), vendor/ (Leaflet, Chart.js)
docs/                         FEATURE_CHECKLIST.md, CHANGES.md, DEPLOYMENT.md
```

## Known limitations

See `docs/FEATURE_CHECKLIST.md` (end) for the full, honest list. The important ones: no Content-Security-Policy; rate limits and the geocode cache live in memory (single instance); the map loads all pins and analytics are computed in Java over all
complaints (fine for thousands of rows, not millions); notifications are in-app only (no e-mail/SMS/push); no Google/OAuth login; layout was never checked in a real browser.

## Search engines, analytics and contact form (deployment)

| Environment variable | Purpose |
| --- | --- |
| `SITE_URL` | Public address of the site, e.g. `https://roadguard.example.com`. Used for canonical links, the sitemap and social previews. |
| `GA_MEASUREMENT_ID` | Google Analytics 4 ID (`G-…`). Leave empty to disable tracking. |
| `SEARCH_CONSOLE_VERIFICATION` | Content of the Google Search Console HTML-tag verification meta tag. |
| `SEO_REDIRECTS` | Legacy URLs that moved, e.g. `/old-page=/map;/report-hazard=/report` (301 redirects). |

Routes are real URLs (`/map`, `/complaints/12`). `robots.txt` and `sitemap.xml` are generated by the server. Unknown top-level paths return 404.
Contact-form messages are stored in the `contact_messages` table; at most 5 per network address per hour are accepted.
