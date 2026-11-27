# HumAppraisal — handover and deployment guide

This guide is for the developer or IT team taking over HumAppraisal and running it on the organisation's own domain (for example `https://appraisal.yourcompany.com`). Read the main [README](../README.md) first for what the app does, the tech stack and the code layout.

## 1. What you are receiving

| Item | Where |
|---|---|
| Source code | GitHub repository, branch **`main`** (production branch) |
| Live instance (current) | Render: one web service `performance-appraisal` + one PostgreSQL database `appraisal-db`, created from `render.yaml` |
| Question bank seed | `backend/src/main/resources/seed/seed-data.json`, generated from `Performance_Appraisal_All_Teams.xlsx` by `tools/extract_seed.py` |
| Tests | `backend/src/test` (JUnit), `tools/api_smoke_test.py` (end-to-end API) |

The app is **one deployable unit**: the root `Dockerfile` builds the React frontend and the Spring Boot backend into a single image. Spring Boot serves the web pages and the API (`/api/...`) from the same address, so there is no separate frontend server and no CORS setup in production.

## 2. Access the current owner should hand over

1. **GitHub** — either
   - transfer the repository to the organisation's GitHub account (repository **Settings → General → Danger Zone → Transfer ownership**), or
   - add the developer as a collaborator (**Settings → Collaborators → Add people**, role *Write* or *Admin*).
2. **Render** (only if you keep hosting there) — invite the developer to the Render workspace (**Workspace settings → Members → Invite**), or move the service to the organisation's own Render workspace.
3. **Secrets** — share these through a password manager, never by email or chat:
   - the `admin` password (`ADMIN_PASSWORD`),
   - the database credentials (only if moving the existing data).
   `JWT_SECRET` does not need to be shared: generate a new one on the new server (users simply sign in again).
4. **DNS** — someone who can add records to the organisation's domain (IT / domain admin).

## 3. Configuration (environment variables)

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `DB_URL` | one of these | — | Full JDBC URL, e.g. `jdbc:postgresql://db-host:5432/appraisal` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | one of these | `localhost` / `5432` / `appraisal` | Used to build the URL when `DB_URL` is not set |
| `DB_USERNAME` / `DB_PASSWORD` | yes | `appraisal` / `appraisal` | Database login |
| `JWT_SECRET` | **yes** | dev-only value | Signing key for login tokens, **at least 32 random characters**. Generate with `openssl rand -base64 48` |
| `JWT_TTL_HOURS` | no | `12` | How long a sign-in lasts |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | first start | `admin` / `admin123` | First admin account, created only when the database has no users |
| `ADMIN_PASSWORD_RESET` | no | `false` | `true` resets the admin password to `ADMIN_PASSWORD` on startup (recovery only; remove afterwards) |
| `SEED_DEMO_USERS` | no | `false` | `true` creates sample users (manager / lead / sara / jay). **Keep `false` in production** |
| `PORT` | no | `8080` | HTTP port |
| `CORS_ORIGINS` | no | `http://localhost:5173` | Only needed if the frontend is served from a different address than the API (not the case with the Dockerfile) |

Health check: `GET /api/health` returns `{"status":"ok"}`.

On first start against an empty database, Flyway creates the tables and the app seeds the question bank (9 teams, 124 questions) and the admin account. Later starts apply only new migrations; data is never reseeded.

## 4. Putting it on the organisation's domain

Pick **one** option.

### Option A — keep Render, add the company domain (simplest)

1. Upgrade the web service and database from the free plan (free services sleep when idle; free databases are deleted after 30 days).
2. Render → service **performance-appraisal → Settings → Custom Domains → Add**, enter e.g. `appraisal.yourcompany.com`.
3. In the company DNS, add the record Render shows (normally a **CNAME** from `appraisal` to `performance-appraisal-xxxx.onrender.com`).
4. Wait for Render to verify the domain; it issues the HTTPS certificate automatically.
5. Render deploys automatically from the `main` branch on every push (service **Settings → Build & Deploy → Branch = `main`**).

### Option B — company server or cloud VM with Docker

```bash
# 1. build the image (from the repository root)
docker build -t humappraisal:1.0 .
```

```bash
# 2. run it (PostgreSQL must be reachable; use your own values)
docker run -d --name humappraisal --restart unless-stopped -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://DB_HOST:5432/appraisal \
  -e DB_USERNAME=appraisal -e DB_PASSWORD='***' \
  -e JWT_SECRET='***at-least-32-random-characters***' \
  -e ADMIN_PASSWORD='***' \
  humappraisal:1.0
```

3. Put a reverse proxy with HTTPS in front of port 8080 for the domain. Example with Caddy (gets certificates automatically):

```
appraisal.yourcompany.com {
    reverse_proxy localhost:8080
}
```

   With nginx, proxy `location /` to `http://localhost:8080` and add a TLS certificate (e.g. Let's Encrypt / certbot).
4. Point the DNS **A record** for `appraisal.yourcompany.com` to the server's public IP.

`docker-compose.yml` is a ready-made local setup (PostgreSQL + backend + nginx frontend) and can be adapted for a single-server install.

### Option C — company Java hosting without Docker

```bash
cd frontend && npm ci && npm run build && cd ..
```

```bash
rm -rf backend/src/main/resources/static && cp -r frontend/dist backend/src/main/resources/static
```

```bash
cd backend && mvn -B package -DskipTests
```

Run `backend/target/appraisal-backend-1.0.0.jar` with Java 21 (`java -jar app.jar`) and the environment variables from section 3, behind the organisation's HTTPS proxy / load balancer.

## 5. Moving the existing data (only if real appraisals were entered on Render)

```bash
pg_dump --no-owner --no-privileges -Fc "RENDER_EXTERNAL_DATABASE_URL" -f humappraisal.dump
```

```bash
pg_restore --no-owner --no-privileges -d "postgresql://USER:PASSWORD@NEW_HOST:5432/appraisal" humappraisal.dump
```

The external database URL is on Render → **appraisal-db → Connect**. Restore into an **empty** database, then start the app; Flyway sees the existing schema version and continues from there. If only sample data exists, skip this and start with an empty database.

## 6. Go-live checklist

- [ ] HTTPS works on the company domain; plain HTTP redirects to HTTPS
- [ ] `JWT_SECRET` is a new long random value; `ADMIN_PASSWORD` is strong; `SEED_DEMO_USERS=false`; `ADMIN_PASSWORD_RESET` removed
- [ ] Admin signs in and changes the password (Account page)
- [ ] Sample users (manager, lead, sara, jay) deactivated or replaced with real people (Users page)
- [ ] Teams, levels, users (team, level, roles) set up; question bank reviewed
- [ ] Automated daily **database backups** enabled and a restore tested
- [ ] `/api/health` is monitored
- [ ] Run the tests once against the new environment (section 7)

## 7. Working on the code

Run locally (needs Java 21, Maven, Node 20+, PostgreSQL) — see README **Run locally**. Then:

```bash
cd backend && mvn test
```

```bash
python3 tools/api_smoke_test.py http://localhost:8080
```

(the smoke test needs a backend started with `SEED_DEMO_USERS=true` on an **empty** database — never run it against production).

Suggested workflow: create a branch per change → open a pull request to `main` → review → merge. Each merge to `main` deploys automatically on Render (Option A); for Options B/C, rebuild and restart.

**Database changes** must be new Flyway files (`backend/src/main/resources/db/migration/V3__description.sql`, `V4__…`); never edit `V1`/`V2`, they have already run in production. Hibernate runs with `ddl-auto: validate`, so the app refuses to start if entities and tables disagree.

## 8. Things worth knowing / possible next steps

- **Sign-in** is username + password with roles (Admin, Manager, TL, Employee). There is no self-service password reset or email; admins reset passwords on the Users page. Company single sign-on (Google Workspace / Microsoft 365) could be added later with Spring Security OAuth2.
- **Login tokens** are kept in the browser's local storage and last `JWT_TTL_HOURS`. There is no login rate limiting yet; consider adding it (or a proxy rule) before exposing the app publicly.
- **Business rules** that matter when changing code: scoring is in `ScoringEngine.java` (unit-tested against the CA sheet); permissions in `AccessService.java`; cycle opening, snapshot and sync in `CycleService.java`. Open cycles keep their own copy of the questions; people join an open cycle only through the cycle page.
- **Question bank** is maintained in the app by admins. `tools/extract_seed.py` only matters for a brand-new empty installation.
