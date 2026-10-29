# Interface language (Settings > Language / भाषा)

## What changed
- New "Language / भाषा" card in Settings: a dropdown of 12 languages, each shown in its own script with its English name (for example "मराठी (Marathi)"), plus an "Active language" badge. Choosing a language saves it to the account, redraws the page and shows a confirmation toast written in the new language.
- Supported: English, Hindi, Bengali, Marathi, Telugu, Tamil, Gujarati, Urdu (right to left), Kannada, Odia, Malayalam, Punjabi.
- Translated into all 12 languages: the Settings page; the navigation bar, drawer and account menu; the login and signup pages; the home page hero, guest message and headline figures; and the save and session toasts.
- Still English, to be translated next: the remaining pages (report, complaints, map, planner, dashboard, analytics, community, repairs, impact, notifications, admin, legal, contact), the command palette, and the server's validation messages.
- The language follows the account across devices. Logged-out visitors keep the choice in this browser only.

## Backend
- `user_preferences.language` (nullable; null is read as "en"). Added by Hibernate `ddl-auto=update`; nothing dropped.
- `GET/PUT /api/users/me/preferences` accept and return `language`. Unknown codes are rejected with a field error.

## Frontend
- `static/js/i18n/config.js`: the central language list (code, English name, native name, direction).
- `static/js/i18n/messages.js`: strings per language. English is the source; any missing key falls back to English.
- `static/js/i18n/messages-shell.js`: shared shell strings as one row per key, in the order of `LANG_ORDER`. Add new shell strings here.
- `static/js/i18n/index.js`: `t()`, `applyLanguage()` and `setLanguage()`. The page language and direction are set before first paint.
- Urdu sets `dir="rtl"`. The navigation drawer slides in from the right in RTL, and the skip link hides by moving up, so RTL does not widen the page.
- Per-script font stacks and a taller line height for Indic scripts (Noto families when installed, system fonts otherwise).

## Not verified
- The Java changes were parse-checked only. Maven and the Spring jars were not available, so the backend was not compiled or run against a database.
- The browser tests used a mocked API (see the test run notes). The real server, the H2 migration on an existing database and the Spring validation response were not exercised.
- Translations other than English were written without native-speaker review. Review them before release, especially the longer notification and privacy hints.

# Merged release: role-based layout and user-scoped hazards

Combines the dashboard refactor with the per-user scoping update into one codebase.

## Backend (from the scoping update; unchanged by the merge)
- `/api/dashboard`, `/api/road-issues?mine=true`, `/api/road-history/*` are scoped by the logged-in user's id. Administrators receive system-wide data.
- Admin endpoints (`/api/admin/*`) remain unrestricted. All routes, schemas and services are kept.

## Citizen (non-admin) experience
- Dashboard: headline figures, quick actions, the latest 5 reports and "Roads needing attention". No charts. No "Recent activity" feed. Scoped to the user's own reports.
- Explore map: shows only the user's own hazards (server-enforced). The list is hidden by default; "Show All Reports" sits directly below the map. A Map/List view switch is also available.
- Road history: scoped to the user's own reports.

## Administrator experience
- Console navigation (`/admin`) with a staff-only layout and a link back to the citizen site.
- Overview: summary counts and the latest 5 submissions. No charts.
- Analytics tab: complaint status, issue categories, reports per week (8 weeks), reports per month (6 months), severity by month, and roads needing attention.
- Explore map and road history show all hazards and reports.
- The "Recent activity" feed is removed from both dashboards.

# Changes in this release (community hazard platform upgrade)

## Verified before packaging
- Backend compiles with `javac -Xlint:all` against the Spring Boot 3.3.5 jars.
- End-to-end API checks: 177/177 (original suite, adapted to the renamed statuses) and 98/98 (new suite: duplicates, idempotent submit, verification, flags, lifecycle, repair evidence, duplicate marking, impact score, leaderboard, admin insights and audit, suggestions, route reasons).
- Frontend in a simulated browser: 80/80 (earlier suite, before the new pages), 23/23 (Impact Score page), 27/27 (every page renders as administrator, map detail panel, list view, report-age filter, priority queue, no console errors).

## Not verified
- Existing databases created by the original app were not re-tested after the status rename.
- The 80-check older frontend suite was not re-run against the new Home/Dashboard split.
- Nothing was viewed in a real browser or on a phone. Offline queue behaviour (IndexedDB, retry on reconnect) and camera capture were not exercised.
- Maven build and real routing/geocoding/tile servers were not run here.

## Backend
- Statuses: Submitted → Under Review → Action Planned → Repair in Progress → Resolved, plus Rejected and Duplicate. Old labels "Assigned" and "In Progress" are renamed once on startup.
- New category: Debris on Road. "Fallen Tree / Obstruction" renamed to "Road Obstruction / Fallen Tree" (old name still accepted).
- Duplicate detection: a new report of the same category within 40 m and 60 days returns 409 POSSIBLE_DUPLICATE with the matches; the user can choose "different hazard" to submit anyway. Nothing is merged automatically.
- Idempotent submission: each report carries a client key; a retried upload returns the existing report.
- Community verification (`HazardVerification`): one "still there" or "no longer there" per user per hazard, changeable. Own reports, sample data and anonymous users cannot vote. Votes never change an official status.
- Review flags: three "no longer there" votes on an open hazard, or three "still there" votes on a resolved one, notify administrators. Admins can dismiss a flag with an explanation (recorded in the audit trail).
- Admin actions: mark duplicate (reversible, report kept, hidden from map), add repair evidence photo (after-photo kept separately from the original).
- Route scoring: penalties also scale by report age (full weight to 30 days, 0.6 at 180 days) and community confirmations (+10% per "still there", −15% per "gone", capped). Route analysis returns a reason for the suggestion, warnings, and links to Google Maps and OpenStreetMap (which use their own routes).
- Impact score (server-computed): +10 accepted report, +5 resolved, +2 per rewarded confirmation (max 5/day). Badges and an opt-in leaderboard showing first name and initial only.
- Admin insights: counters, priority queue with visible factors, hotspots (about 500 m cells with 2+ open hazards), audit trail.
- Rule-based suggestions (category from keywords, description template). No AI model is used, and the API says so.
- New tables/columns: `hazard_verifications`, complaint `clientRequestId`, `duplicateOfId`, repair photo columns, pin `flagClearedAt`, preference `showOnLeaderboard`. Added by Hibernate `ddl-auto=update`; nothing dropped.
- New settings: `roadguard.verification.*` (radius, flag threshold, rate limits).

## Endpoints added
GET /api/hazards/nearby · GET /api/hazards/{id} · POST /api/hazards/{id}/verify · GET /api/hazards/{id}/photo · GET /api/hazards/{id}/resolution-photo
GET /api/impact/rules · GET /api/impact/leaderboard · GET /api/impact/me
POST /api/suggest/category · POST /api/suggest/description
GET /api/admin/insights · GET /api/admin/audit · POST /api/admin/hazards/{id}/dismiss-flag
POST /api/admin/complaints/{id}/duplicate · POST /api/admin/complaints/{id}/resolution-photo · GET /api/complaints/{id}/resolution-photo
GET /api/road-issues accepts maxAgeDays. Complaint creation accepts clientRequestId and allowDuplicate.

## Frontend
- Navigation: Home, Explore Map, Report Hazard, My Reports, Safe Routes, Dashboard (+ Admin). "Report a Hazard" in the top bar.
- Home: explanation, three steps, headline numbers, latest reports. Dashboard: charts and full statistics.
- Explore Map: map/list toggle, filters (type, status, severity, report age, distance), Near me (asks for location only when pressed), click-to-report, detail panel with photo, counts, community answers, progress.
- Report Hazard: camera or file photo, location with map preview, suggestions (optional), duplicate check before submitting, draft kept on the device, "Saved on this device" when offline.
- My Reports: pending uploads listed with retry and discard.
- Safe Routes: plan a route and saved routes, with reasons, warnings and external navigation links.
- Impact score page and opt-in leaderboard. Admin: priority queue, hotspots, audit trail, duplicate and repair-evidence controls.
- Emoji replaced by SVG glyphs. Palette unchanged.

## Environment variables
None added to the deployment settings; verification limits use defaults in `application.properties`.

## Community (Reddit-style road-safety discussions)

Verified: 61/61 community API checks; 152/152 existing API checks; 36/36 community browser checks; 27/27 every-page browser check; 23/23 Impact Score browser check.

Backend
- Entities: `CommunityPost`, `CommunityComment` (nested via parent), `CommunityVote` (one row per user per post or comment, unique). Tables are created by `ddl-auto=update`; nothing existing is changed.
- Topics are fixed: Road safety, Accidents, Potholes, Traffic, Construction and diversions, Lights and signals, Flooding, Other.
- Rules enforced on the server: only the author can edit or delete their posts and comments (others get 403); one vote per user per item, changeable or removable; scores are recomputed from the vote rows; deleted posts keep their comments as "[deleted]"; deleted comments keep their place so replies stay attached; replies must belong to the same post; rate limits on posting and voting.
- Sorting: New (newest), Hot (Reddit-style: vote magnitude with time decay), Top (highest score, optionally within a day, week, month or year). Search matches title and body; `%` and `_` are treated as plain text.
- Author names are shown as first name and initial only.
- Endpoints (all under `/api/community`): `GET /categories`, `GET|POST /posts`, `GET|PUT|DELETE /posts/{id}`, `POST /posts/{id}/vote`, `POST /posts/{id}/comments`, `PUT|DELETE /comments/{id}`, `POST /comments/{id}/vote`, `GET /users/{id}`.

Frontend
- Pages: `#/community` (feed with Hot / New / Top, topic list, search, post composer), `#/community/posts/{id}` (post with nested comment tree, reply boxes, edit and delete for your own content), `#/community/users/{id}` (profile with counts, score and posts).
- Navigation: a "Community" entry between Safe Routes and Dashboard. Existing pages are unchanged.

Bug fixed during the work
- The vote column was named `value`, which is reserved in H2; it is now `vote_value` (no API change).

## Civic features: authority loop, repair-proof pages, linked threads, your street

Verified: 37/37 civic API checks; 61/61 community API checks; 152/152 existing API checks; 24/24 civic browser checks; 36/36 community browser checks; 23/23 Impact browser checks; 27/27 every-page browser checks.

Authority loop (honest version)
- A report owner can record the ward or department they believe is responsible (`PUT /api/complaints/{id}/authority`).
- `GET /api/complaints/{id}/letter` produces a plain-text draft from the real report (reference, location, description, date, placeholders for the citizen's own name and contact). It is never sent by RoadGuard. Preparing it is recorded.
- Only administrators can record an authority's reply (`PUT /api/admin/complaints/{id}/authority-response`), with a time stamp and an entry in the status history. Citizens see it; it is never invented.
- The UI and API state that RoadGuard is not connected to any government system.

Repair-proof pages
- Public page per resolved report: `GET /api/repairs/{reportId}`. Shows before and after photos, submitted and resolved dates, days taken, a timeline of status changes, any recorded authority response, and a disclaimer. Internal staff notes, e-mail addresses, names and exact coordinates are not published. Rejected, duplicate and demonstration reports have no page.
- `GET /api/repairs/fixed` lists the most recent resolved real reports ("Fixed recently" page, `#/repairs`).

Community threads linked to hazards
- A post can reference a hazard report by its public reference. The reference is validated, and the thread shows the report's live status with a link to it. Unknown references are refused.

Your street
- `GET /api/impact/street` on the Impact page: hazards you confirmed, what has happened to them since (resolved, planned or open), and your own resolved and open reports. Computed from real records; the page notes that confirmations do not change the official status.

Schema
- New nullable columns on complaints (authority name, authority response and its time, letter time) and on community posts (linked hazard). Added by `ddl-auto=update`; nothing dropped.

## Home page video
- The home-page 3D model was replaced by the supplied traffic video (`media/highway-traffic.mp4`, 1280x720 H.264, 60 s, stock footage). It plays muted and loops, has a pause/play button, and does not autoplay when the visitor asks for reduced motion. The caption says it is stock footage, not live traffic.
- Removed the 3D scene page and the bundled Three.js library, which only it used. `X-Frame-Options` is back to `DENY`.

## Palette and navbar (Road Hazard & Field Safety)
- Colours are now CSS variables in `css/theme.css`: Safety Orange #E65100 (CTAs, active states, alerts), Dark Slate #2E403B (headings, dark buttons, avatar), Muted Slate #4A5D57 (secondary text, icons), App background #F0F4F1, card white with #D1DDD6 borders, body text #1C2824. Light is the default; dark is available in Settings.
- Orange text on light backgrounds uses #B53F00 (5.7:1). Plain #E65100 is 3.8:1 and only used for large UI elements and indicators.
- `css/navbar.css` and `js/navbar.js`: sticky, blurred (`backdrop-filter: blur(10px)`), bottom border `1px solid rgba(74, 93, 87, 0.15)`. Left: brand and a live status tag from a real `/api/health` check. Centre: Dashboard, Hazards, Reports, Analytics, and a More menu for the other pages (nothing was removed). Right: Ctrl/Cmd+K search dialog, Report a Hazard button, notification bell with an orange dot when unread, account menu. Below 1040px: hamburger panel.
- Fixes found while testing: the account menu closed itself because a page-change check ran during the re-render; the router now waits for startup before rendering, so pages cannot read reference data that has not loaded yet.
- Verified: 56/56 navbar browser checks; 81/81 smoke; 27/27 every-page; 36/36 community; 24/24 civic; 23/23 Impact.
