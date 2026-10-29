# Deploying RoadGuard

RoadGuard is one Java process (Spring Boot, port 8080) that serves both the API and the website, and keeps its data in a single **H2 file database plus an uploads folder**. That shapes every deployment:

* run **one instance only** (the file database cannot be shared between processes);
* keep the database and uploads folder on **persistent storage** (they are the whole state of the site);
* put it behind **HTTPS**.

Platforms that only run short-lived serverless functions (for example Vercel or Netlify) cannot host it. Use a server or VPS with Java 17, or any platform that builds and runs Maven/Java projects.

## 1. On a server or VPS (JAR + systemd)

Prerequisites: JDK 17+ and Maven on the build machine, JRE 17+ on the server.

```
mvn -DskipTests package                       # builds target/roadguard-0.0.1-SNAPSHOT.jar
sudo useradd --system --home /opt/roadguard roadguard
sudo mkdir -p /opt/roadguard /var/lib/roadguard && sudo chown roadguard /var/lib/roadguard
sudo cp target/roadguard-0.0.1-SNAPSHOT.jar /opt/roadguard/roadguard.jar
```

Settings go in `/etc/roadguard.env` (plain `KEY=value` lines; `chmod 600` it, it holds the admin password). Start from `.env.example`; a minimal production file:

```
SEED_ENABLED=false
BOOTSTRAP_ADMIN_EMAIL=you@example.org
BOOTSTRAP_ADMIN_PASSWORD=<a strong password>
DB_URL=jdbc:h2:file:/var/lib/roadguard/roadguard
UPLOAD_DIR=/var/lib/roadguard/uploads
SESSION_COOKIE_SECURE=true
FORWARD_HEADERS_STRATEGY=framework
GEOCODER_CONTACT_EMAIL=you@example.org
```

`/etc/systemd/system/roadguard.service`:

```
[Unit]
Description=RoadGuard
After=network.target

[Service]
User=roadguard
WorkingDirectory=/opt/roadguard
EnvironmentFile=/etc/roadguard.env
ExecStart=/usr/bin/java -jar /opt/roadguard/roadguard.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```
sudo systemctl daemon-reload && sudo systemctl enable --now roadguard
journalctl -u roadguard -f          # look for "Created administrator account ..."
curl http://localhost:8080/api/health
```

With `SEED_ENABLED=false` there are **no demo accounts and no sample data**. The first administrator comes from `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`
(8-64 characters with a letter and a number); it is created only if that e-mail does not exist yet and an existing account is never changed, so editing the variable later does not reset a password.
Citizens register themselves on the site. To update: build a new JAR, copy it over `roadguard.jar`, `sudo systemctl restart roadguard`.

## 2. HTTPS (required for a real site)

Put a reverse proxy in front of port 8080 and keep, in the settings above:

```
SESSION_COOKIE_SECURE=true
FORWARD_HEADERS_STRATEGY=framework
```

`SESSION_COOKIE_SECURE=true` stops the login cookie being sent over plain HTTP; `framework` makes the app trust the proxy's `X-Forwarded-Proto/For` headers (so the CSRF cookie is also marked Secure and rate limits see the real client address).
Only enable it when a proxy you control sits in front, otherwise clients could spoof those headers. For plain `http://localhost` testing set `SESSION_COOKIE_SECURE=false`.

Caddy (automatic certificates), `Caddyfile`:

```
roadguard.example.com {
    reverse_proxy localhost:8080
}
```

nginx (with certificates from certbot):

```
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $remote_addr;
    client_max_body_size 6m;          # photos are limited to 5 MB
}
```

## 3. Platforms that build Maven projects (Railway, Azure App Service, Elastic Beanstalk, Heroku-style buildpacks ...)

| Setting | Value |
|---|---|
| Build command | `mvn -DskipTests package` (most platforms detect Maven automatically) |
| Start command | `java -jar target/roadguard-0.0.1-SNAPSHOT.jar` |
| Port | the platform's `PORT` variable is honoured automatically (default 8080) |
| Environment variables | the same as in the settings file above; keep `SESSION_COOKIE_SECURE=true` and `FORWARD_HEADERS_STRATEGY=framework` when the platform's router terminates HTTPS |
| Persistent disk / volume | mount one and point `DB_URL` and `UPLOAD_DIR` at it. Without it, every deploy or restart loses the database and photos |
| Instances | exactly 1 |
| Health check path | `/api/health` |

If a platform offers no persistent disk, it is not suitable for this app as built (H2 file database).

## Checklist before going public

* Strong `BOOTSTRAP_ADMIN_PASSWORD`; `SEED_ENABLED=false`; `H2_CONSOLE_ENABLED` left `false`.
* HTTPS in front, `SESSION_COOKIE_SECURE=true`, `FORWARD_HEADERS_STRATEGY=framework`.
* **Back up the database file(s) and the uploads folder.** Safest: stop the app, copy both, start it again.
* The default map tiles (OpenStreetMap), routing (public OSRM demo server) and place search (Nominatim) are free **fair-use** services meant for light traffic.
  For real traffic use your own OSRM (`ROUTING_BASE_URL`) and a tile provider you are allowed to use (`MAP_TILE_URL`, `MAP_ATTRIBUTION`), and set `GEOCODER_CONTACT_EMAIL`.
* Sessions and rate-limit counters are in memory: a restart logs everyone out. Outgoing calls to the OSM services need internet access from the server.

## What has and has not been verified

Verified in development: starting the app purely from environment variables with a custom port and data directory; no demo data with `SEED_ENABLED=false`; the bootstrap administrator (created once, never overwritten, weak password refused);
Secure cookie flags with `FORWARD_HEADERS_STRATEGY=framework`; the database file and uploads folder being created under the configured directory; `/api/health` answering.
**Not verified:** building the JAR with Maven (the app was run from compiled classes instead), the systemd unit, the proxy snippets, and any hosting platform.
