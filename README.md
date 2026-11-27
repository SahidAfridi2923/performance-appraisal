# HumAppraisal

Web version of `Performance_Appraisal_All_Teams.xlsx`. The scoring logic of the **CA** sheet is ported 1:1. Admins can now **add, edit, reorder and remove questions, categories, weights and rating rules at any time**, with no spreadsheet editing.

> **Taking over or deploying on your own domain?** See [docs/HANDOVER.md](docs/HANDOVER.md).

## Tech stack

**Frontend** (`frontend/`)

| Part | Technology |
|---|---|
| UI library | React 18.3, plain JavaScript (no TypeScript) |
| Build / dev server | Vite 5.4 |
| Routing | React Router 6.28 |
| Styling | Plain CSS with design tokens (ui-ux-pro-max design system); no UI component library |
| Fonts | Plus Jakarta Sans (interface), Nunito (logo) from Google Fonts |
| Icons and logo | Inline SVG, no icon library |

**Backend** (`backend/`)

| Part | Technology |
|---|---|
| Language / framework | Java 21, Spring Boot 3.4.5 |
| Build | Maven |
| REST API | Spring Web (`/api/...`) |
| Security | Spring Security, JWT login tokens (jjwt 0.12), BCrypt password hashing |
| Validation | Jakarta Bean Validation |
| Data access | Spring Data JPA / Hibernate |
| Database migrations | Flyway (`db/migration`: V1 schema, V2 per-cycle membership) |
| Boilerplate | Lombok (entities) |

**Database:** PostgreSQL (Render managed PostgreSQL in production; PostgreSQL 16 locally or in Docker).

**Packaging and hosting**

| Part | Technology |
|---|---|
| Container | Root `Dockerfile`, 3 stages: Node 22 builds React → Maven + Temurin 21 builds the jar → Temurin 21 JRE runs it. Spring Boot serves the React build, so the app and API share one address. |
| Hosting | Render, via the `render.yaml` Blueprint (one web service + one PostgreSQL database), auto-deploying from GitHub |
| Local containers | `docker-compose.yml`: PostgreSQL, the backend, and nginx serving the frontend |

**Testing and tools**

| Part | Technology |
|---|---|
| Unit tests | JUnit 5: scoring engine, including parity with the CA sheet (7.46, Exceeds Expectations) |
| End-to-end API test | Python, `tools/api_smoke_test.py` |
| Question bank seed | Python, `tools/extract_seed.py` (rebuilds the seed from the Excel workbook) |
| Browser checks | Playwright (used during development; not kept in the repo) |

## What it does

| Area | Who | What |
|---|---|---|
| Question bank | Admin | Common competencies (shared by all teams) and role-specific questions per team. Add, edit, reorder and archive questions, change weights, "Reviewed by" (All / TL-Manager only), the levels each question applies to, and the scoring guide. Add, rename and reorder categories and set their **points per level** (0 = does not apply). |
| Scoring rules | Admin | Common/Role block weights (40/60), reviewer weights (Self 10 / Peer 20 / TL 30 / Manager 40), rating bands, 1–10 scale meanings, Situation and Business-impact pick lists. |
| Teams & levels, Users | Admin | All 9 teams and Junior / Senior / Team Lead are seeded from the workbook. Admins can add more. Users have a home team, a level, roles (Employee / TL / Manager / Admin) and optional extra teams they lead. |
| Cycles | Admin | Draft → **Open** → Closed (can be reopened). Opening **freezes a copy** of the question bank and weights for that cycle. **Sync questions** previews, then pushes later bank edits into an open cycle. Opening a cycle includes everyone active, appraised and in a team at that moment. After that, people join or leave an open cycle **only** through its page (**Add a person**: anyone, any role, with team and level; **Remove**). New users created while a cycle is open stay outside it. Level and team edits on the Users page still update people already in an open cycle. Admins can reopen or delete individual reviews. |
| Reviews | Everyone | Self (the employee); Peer (anyone in the same team); TL / Manager (TL or Manager role who leads the team). Answers are 1–10 or N/A, saved as a draft and then submitted (locked). TL/Manager answers of 9–10 or 1–2 need a comment. Team Leads get no TL review; the Manager answers. |
| Appraisal page | Leads; employee | Scores with a per-question Self/Peer/TL/Manager breakdown, the level picker, lead comments, achievements (lead verifies) and goals. Employees see their own scores only after the cycle closes. |
| Summary | TL / Manager / Admin | Team averages and the per-person table, like the sheet's Summary, with level filter, "check before sharing" outlier flag and self-rating gap hint. |

## Scoring (same as the CA sheet)

1. **Question score:** weighted average of the Self / Peer / TL / Manager numbers, using the configurable 10/20/30/40 weights. Blank or N/A reviews are skipped and the other weights are rebalanced. When several people review in the same role, their numbers are **averaged first**.
2. **Applies to a level:** a question applies when its category has more than 0 points for that level and the question is not switched off for that level.
3. **Category score:** weighted average of its question scores, using the question weights.
4. **Common score:** category scores weighted by the category points for the employee's level. The **Role-specific** score is calculated the same way. With one role category, as in the workbook, this equals the sheet's plain weighted average.
5. **Final score:** `(0.4 × Common + 0.6 × Role) / (0.4 + 0.6)`, rounded to 2 decimals. It is shown only when a level is set and a TL or the Manager has answered every applicable question (N/A counts as answered).
6. **Rating:** the band with the highest "from score" that is ≤ the final score. So 6.9 is Meets Expectations and 7.0 is Exceeds Expectations.

Only **submitted** reviews count; drafts are ignored. `ScoringEngineTest` reproduces the CA sheet sample (Resource 1, Senior): Common **7.7**, Role **7.3**, Final **7.46**, **Exceeds Expectations**.

Differences from the sheet, all deliberate:
- **Several reviewers in one role:** averaged automatically. The sheet asked the reviewers to agree a single number instead.
- **A block with no answerable questions:** that block is left out and the other block takes the full weight, instead of showing "Check answers". An example is a team with no role questions yet.

## Run locally (development)

You need Java 21, Maven, Node 20+ and PostgreSQL 14+.

Create the database:

```bash
createuser -P appraisal
```

```bash
createdb -O appraisal appraisal
```

Start the backend (API on http://localhost:8080). Flyway creates the tables, and on first start the question bank is seeded from `backend/src/main/resources/seed/seed-data.json`:

```bash
cd backend
SEED_DEMO_USERS=true mvn spring-boot:run
```

In another terminal, start the frontend (http://localhost:5173, proxies /api to :8080):

```bash
cd frontend
npm install
npm run dev
```

Sign in as `admin` / `admin123` and change the password on the account page. `SEED_DEMO_USERS=true` also creates the CA users `manager`, `lead`, `sara` and `jay` (password `password123`). It only runs on an empty database.

### Configuration (environment variables)

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/appraisal` / `appraisal` / `appraisal` | |
| `JWT_SECRET` | dev-only value | **Set in production**, at least 32 characters |
| `JWT_TTL_HOURS` | 12 | |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | Only used when the database has no users |
| `SEED_DEMO_USERS` | false | |
| `CORS_ORIGINS` | `http://localhost:5173` | Comma-separated |

## Run with Docker

```bash
cp .env.example .env
```

Edit `.env`, then:

```bash
docker compose up --build
```

The app is then at http://localhost:3000. nginx serves the React build and proxies `/api` to the backend.

> The Docker files were written but not built in the environment this was developed in (no Docker daemon there). The backend and frontend themselves were built and tested directly.

## Deploy on Render

`render.yaml` is a Render Blueprint. It creates a PostgreSQL database and one web service built from the root `Dockerfile`, which serves both the React app and the API.

1. Sign in at https://dashboard.render.com, then click **New → Blueprint**.
2. Connect GitHub and pick this repository and the branch to deploy.
3. Enter a value for **ADMIN_PASSWORD** when asked, then click **Apply**.
4. Wait for the first build (about 5–10 minutes), then open the `https://performance-appraisal-xxxx.onrender.com` address shown on the service.

Notes:
- `SEED_DEMO_USERS` is `"true"` in `render.yaml`, so the sample users `manager`, `lead`, `sara` and `jay` (password `password123`) exist for trying the app. Set it to `"false"` before the first deploy for real use, or deactivate them on the Users page later.
- **Admin can't sign in?** The admin account is created only on the first start, so changing `ADMIN_PASSWORD` later has no effect on its own. To reset it, set `ADMIN_PASSWORD` to the new password and add `ADMIN_PASSWORD_RESET` = `true` under the service's **Environment** tab, then save. Render redeploys. Sign in, then delete `ADMIN_PASSWORD_RESET`. An empty `ADMIN_PASSWORD` falls back to `admin123`.
- On Render's free plan the service sleeps after about 15 minutes idle, so the first visit afterwards takes about a minute. The free database expires after 30 days. Choose paid plans for real use.

## Tests

Scoring engine unit tests:

```bash
cd backend
mvn test
```

End-to-end API check against a running backend started with `SEED_DEMO_USERS=true` on an empty database:

```bash
python3 tools/api_smoke_test.py http://localhost:8080
```

## Re-seeding from a new workbook

`tools/extract_seed.py` rebuilds `seed-data.json` from the workbook: questions, weights, category points, rating bands, scale and pick lists, and guidance text from `Metric_Library`.

```bash
python3 tools/extract_seed.py Performance_Appraisal_All_Teams.xlsx backend/src/main/resources/seed/seed-data.json
```

The seed is applied only to an empty database. After that, the admin screens are the source of truth.

## Project layout

```
backend/src/main/java/com/appraisal/
  domain/      JPA entities: live bank (Team, Level, Category, Question, ScoringSettings, ...)
               + frozen per-cycle copies (Cycle*, Appraisal, Review, Answer, Achievement, Goal)
  service/     ScoringEngine (pure scoring logic), ScoreService, CycleService (open / sync / close),
               ReviewService, AppraisalService (results, summary), QuestionBankService, AccessService (permissions)
  web/         REST controllers (/api/auth, /api/admin/**, /api/cycles, /api/appraisals, ...)
  security/    JWT filter + Spring Security config
backend/src/main/resources/db/migration   Flyway schema
frontend/src/pages                        My work, review form, appraisal page, summary
frontend/src/pages/admin                  Questions, Scoring rules, Cycles, Users, Teams & levels
```
