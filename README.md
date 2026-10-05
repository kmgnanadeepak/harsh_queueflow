# QueueFlow

Production-style digital queue and crowd management system. Visitors take a
numbered token for a service, see their live position and ETA, and get pushed
updates when their turn is near or called. Staff work a counter queue, and
admins manage the catalogue and read analytics.

```
React 18 + Vite + Tailwind   ->   Spring Boot 3 / Java 17 / JWT   ->   MySQL 8
        (SockJS + STOMP)  <------------- live push ------------------   (H2 for dev)
```

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Project layout](#project-layout)
- [Quick start](#quick-start)
- [Demo accounts](#demo-accounts)
- [Google sign-in setup](#google-sign-in-setup)
- [How the queue decides who is next](#how-the-queue-decides-who-is-next)
- [Realtime events](#realtime-events)
- [API reference](#api-reference)
- [Security model](#security-model)
- [Tests](#tests)
- [Configuration](#configuration)

---

## Features

**Customers (`USER`)**

- Browse active services and see live queue length, counters in service and
  average wait before joining.
- Take a token with an optional priority category (`NORMAL`, `ELDERLY`,
  `DISABLED`, `EMERGENCY`).
- Live position, people ahead, estimated wait and counter assignment.
- Push notification when the queue is getting close and when a counter calls
  the token.
- Token history and self-service cancellation.

**Staff (`STAFF`)**

- Assigned-counter view with the queue for that service.
- Call next, start, complete and skip tokens.
- Counter status control (`AVAILABLE`, `BUSY`, `OFFLINE`) that feeds ETA
  calculations.

**Admins (`ADMIN`)**

- Analytics: tokens issued, average and peak wait, completion rate, throughput
  and per-service breakdown.
- CRUD for organizations, services, counters and users, including creating
  `STAFF` and `ADMIN` accounts.
- Broadcast a notification to every user.

**Platform**

- Stateless JWT auth plus optional Google OIDC login with account linking.
- Priority ordering with anti-starvation ageing.
- Live updates over STOMP instead of polling.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Frontend | React 18, Vite 5, React Router 6, Tailwind CSS 3, Axios, Recharts 3, `@stomp/stompjs` + `sockjs-client` |
| Backend | Spring Boot 3, Java 17, Spring Security, Spring Data JPA, Spring WebSocket (STOMP) |
| Auth | BCrypt passwords, HMAC-SHA256 JWT, Google OIDC via Spring Security OAuth2 client |
| Database | MySQL 8 in production; H2 in MySQL-compatibility mode for local dev and tests |
| Tests | JUnit 5, Spring Security MockMvc, AssertJ |

---

## Project layout

```
queueflow/
├── backend/                 Spring Boot service
│   ├── src/main/java/com/harsha/queueflow/
│   │   ├── config/          security, async, seed data
│   │   ├── controller/      REST endpoints
│   │   ├── dto/             request/response records
│   │   ├── entity/          JPA entities
│   │   ├── enums/           Role, TokenStatus, CounterStatus, PriorityCategory
│   │   ├── exception/       typed errors + global handler
│   │   ├── repository/      Spring Data repositories
│   │   ├── security/        JWT filter/provider, Google OIDC, success handler
│   │   └── service/         queue engine, token lifecycle, notifications
│   └── src/test/java/...    73 tests
├── frontend/                React SPA
│   ├── src/api/             axios client + endpoint bindings
│   ├── src/components/      layout, notification bell, shared UI
│   ├── src/context/         auth/session
│   ├── src/hooks/           realtime STOMP hook
│   └── src/pages/           login, register, OAuth callback, three dashboards
├── docker-compose.yml       MySQL + backend + nginx-served frontend
├── .env.example             every environment variable documented
└── README.md
```

---

## Quick start

### Option A - zero setup (H2, no MySQL)

Needs only Java 17 and Node 18+.

```bash
# terminal 1 - API on http://localhost:8080
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev,h2

# terminal 2 - UI on http://localhost:5173
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173> and sign in with a demo account.

The `h2` profile uses an in-memory database that is recreated on every restart.
It also exposes an H2 console at <http://localhost:8080/h2-console> (JDBC URL
`jdbc:h2:mem:queueflow`, user `sa`, blank password).

### Option B - Docker Compose

Copies `.env.example` to `.env`, fills in `JWT_SECRET`, then:

```bash
cp .env.example .env
docker compose up --build
```

- Frontend (nginx) on <http://localhost:8081>
- Backend API on <http://localhost:8080>
- MySQL on `localhost:3306` (credentials from `.env`)

Both containers are health-checked and start in dependency order.

### Option C - local MySQL

```sql
CREATE DATABASE queueflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'queueflow'@'localhost' IDENTIFIED BY 'queueflow';
GRANT ALL PRIVILEGES ON queueflow.* TO 'queueflow'@'localhost';
```

```bash
cd backend
mvn spring-boot:run
```

The default `dev` profile connects to `localhost:3306` with `ddl-auto: update`
and seeds the demo organisation, services, counters and accounts.

---

## Demo accounts

Seeded idempotently whenever `SEED_DATA_ENABLED=true`.

| Role | Email | Password |
| --- | --- | --- |
| `ADMIN` | `admin@queueflow.com` | `admin123` |
| `STAFF` | `staff@queueflow.com` | `staff123` |
| `USER` | `user@queueflow.com` | `user123` |

Demo catalogue: **Kuppam General Hospital** with *General Consultation*
(10 min average), *Billing* (5 min) and *Pharmacy* (3 min), served by counters
`C1`, `C2`, `B1` and `P1`. `C1` is assigned to the seeded staff account.

> Self-registration always creates a `USER`. Only an admin can mint a `STAFF`
> or `ADMIN` account, and Google sign-in can never create a privileged role.

---

## Google sign-in setup

The Google button is hidden automatically when no credentials are configured.
Check the current state at `GET /api/auth/google/status`.

1. Create an OAuth 2.0 *Web application* client in the
   [Google Cloud console](https://console.cloud.google.com/apis/credentials).
2. Add the authorised redirect URI:
   `{FRONTEND_URL}/login/oauth2/code/google`
   (for local development that is
   `http://localhost:8080/login/oauth2/code/google`, because the browser starts
   the flow at the API origin).
3. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env` and restart.

On success QueueFlow issues **its own JWT** and redirects to
`{FRONTEND_URL}/auth/callback?token=...`; the Google access token is never
handed to the browser.

Account linking rules:

1. Match on Google's stable `sub` claim, so repeat sign-ins reuse the account.
2. If the subject is unknown but the email already belongs to a QueueFlow
   account, the identity is **linked** to it - the existing password hash, role
   and status are preserved.
3. If that email is already linked to a *different* Google subject the login is
   refused rather than hijacked.
4. Otherwise a new `USER` account is created.

---

## How the queue decides who is next

Ordering is computed in the database, not in memory, so it holds across restarts
and multiple application instances.

1. **Priority rank** - `EMERGENCY` 0, `ELDERLY` 1, `DISABLED` 2, `NORMAL` 3.
2. **Ageing** - every 15 minutes of waiting lowers a token's effective rank by
   one, capped at two boosts. A long-waiting `NORMAL` token eventually outranks
   a freshly taken `ELDERLY` token, so nobody is starved.
3. **Stable tie-breaks** - then `createdAt`, then `id`, so the order is
   deterministic.

Effective rank is persisted and refreshed by a scheduled job, which keeps the
per-request cost to a single indexed query.

**Estimated wait** is `ceil(peopleAhead x averageServiceTime / activeCounters)`.
When no counter is active the queue would stall, so the estimate falls back to
the serial time, `peopleAhead x averageServiceTime`.

**Token numbers** are `SERVICE-042` style: service initials plus a per-service
sequence, allocated under a pessimistic row lock and protected by a unique
`(queue_service_id, token_number)` constraint so two instances cannot collide.

**Lifecycle:** `WAITING -> CALLED -> SERVING -> COMPLETED`, plus `SKIPPED` and
`CANCELLED`. Completing a token releases its counter back to `AVAILABLE` but
does **not** auto-call the next one - a staff member presses *Call next*, which
keeps an idle counter from silently pulling the queue forward.

---

## Realtime events

Authenticated over SockJS, endpoint `/ws`, STOMP destinations:

| Destination | Published when | Consumed by |
| --- | --- | --- |
| `/topic/queue/{serviceId}` | any token change for a service | staff dashboard, customer dashboard |
| `/topic/token/{tokenId}` | that specific token changes | the customer holding it |
| `/topic/notifications/{userId}` | a notification is created | the notification bell |

---

## API reference

All endpoints are prefixed with `/api`. Everything except the marked endpoints
requires `Authorization: Bearer <jwt>`.

**Public**

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/auth/register` | creates a `USER` |
| `POST` | `/api/auth/login` | returns a JWT |
| `GET` | `/api/auth/google/status` | `{"enabled": boolean}` |
| `GET` | `/api/organizations/active` | public catalogue |
| `GET` | `/api/services/active` | public catalogue |

**Session and catalogue - any authenticated role**

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/auth/me` | current principal |
| `POST` | `/api/auth/logout` | audit event; JWTs are stateless |
| `POST` | `/api/profile` | update own profile |
| `GET` | `/api/organizations`, `/api/services`, `/api/services/{id}` | catalogue |
| `GET` | `/api/services/{id}/counters` | counters for a service |
| `GET` | `/api/queue/{serviceId}` | queue for a service |
| `GET` | `/api/queue/{serviceId}/status` | live counts and wait |
| `GET` | `/api/notifications` | own notifications |
| `GET` | `/api/notifications/unread-count` | badge count |
| `PUT` | `/api/notifications/{id}/read` | mark one read |
| `PUT` | `/api/notifications/read-all` | mark all read |

**Tokens - `USER`**

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/tokens` | take a token for a service |
| `GET` | `/api/tokens/my` | own active tokens |
| `GET` | `/api/tokens/my/current` | current token, `204` when none |
| `GET` | `/api/tokens/my/{id}` | own token by id |
| `POST` | `/api/tokens/my/{id}/cancel` | cancel own token |
| `GET` | `/api/tokens/history` | own token history |

**Staff operations - `STAFF` or `ADMIN`**

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/staff/dashboard` | staff overview |
| `GET` | `/api/staff/queue` | queue for a service |
| `GET` | `/api/staff/counters` | counters |
| `POST` | `/api/staff/tokens/call-next` | call the next waiting token |
| `POST` | `/api/staff/tokens/{id}/start` | begin service |
| `POST` | `/api/staff/tokens/{id}/complete` | complete service |
| `POST` | `/api/staff/tokens/{id}/skip` | skip a token |
| `POST` | `/api/staff/counters/{id}/status` | set counter status |

**Administration - `ADMIN` only**

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/admin/dashboard` | headline metrics |
| `GET` | `/api/admin/analytics` | charts and aggregates |
| `GET`/`POST` | `/api/admin/users` | list / create users |
| `PUT`/`DELETE` | `/api/admin/users/{id}` | update / deactivate |
| `GET`/`POST` | `/api/admin/organizations` | CRUD |
| `PUT`/`DELETE` | `/api/admin/organizations/{id}` | |
| `GET`/`POST` | `/api/admin/services` | CRUD |
| `PUT`/`DELETE` | `/api/admin/services/{id}` | |
| `GET`/`POST` | `/api/admin/counters` | CRUD |
| `PUT`/`DELETE` | `/api/admin/counters/{id}` | |
| `POST` | `/api/admin/broadcast` | notify every user |

Errors share one shape:

```json
{ "status": 409, "error": "Conflict", "message": "...", "path": "/api/tokens" }
```

---

## Security model

- **Stateless JWT.** HMAC-SHA256, `sub`, `role`, `iat`, `exp`, signed by a secret
  of at least 32 bytes. There is no server-side session to steal.
- **Fail-closed startup.** The app refuses to boot without a real JWT secret.
  The `dev` and `h2` profiles get a local default; `prod` requires the variable.
- **Two filter chains.** `/api/**` is stateless, CSRF-free and JSON-oriented;
  a separate chain owns the browser OAuth2 endpoints and SockJS.
- **Ownership is server-side.** Token reads, cancels and transitions resolve the
  caller from the JWT and check it against the token, so a crafted request body
  cannot reach another customer's token.
- **Role boundaries are enforced in the URL space** (`/api/admin/**`,
  `/api/staff/**`), with additional ownership checks in the service layer.
- **Passwords** are BCrypt hashed. Google accounts hold no local password.
- **Deactivation is immediate**, for both password and Google sign-in.
- Stack traces are never returned; `spring.jpa.open-in-view` is off.

---

## Tests

```bash
cd backend
mvn test
```

73 tests, all passing:

| Suite | Tests | Covers |
| --- | --- | --- |
| `AuthControllerTest` | 15 | register, login, validation, deactivated and Google-only accounts, `/me`, `/logout` |
| `AuthorizationSecurityTest` | 19 | admin/staff/user boundaries, token ownership, counter operations |
| `QueueEngineTest` | 16 | priority ordering, ageing, ETA, token numbering, lifecycle |
| `TokenLifecycleTest` | 12 | allocation, transitions, ownership, notifications, concurrent allocation |
| `OAuthAccountLinkServiceTest` | 11 | linking rules and the privilege-escalation guard |

Tests run against H2 with an isolated, purged database per test; BCrypt cost is
lowered to 4 so the suite stays fast.

Frontend:

```bash
cd frontend
npm run lint    # ESLint (react, react-hooks, unused imports), zero warnings tolerated
npm run build   # production bundle
```

### Dependency advisories

`npm audit` reports four advisories against the pinned dependency lines: a
high-severity issue in `vite@5` (build-time only - it never ships in the
bundle) and moderate issues in `react-router-dom@6` plus its transitive `esbuild`
and `react-router` entries. Clearing them means moving to Vite 8 and
react-router 7, which is a deliberate migration rather than a patch, so it was
left out of scope here and is worth scheduling separately.

---

## Configuration

Everything is documented in `.env.example`. The settings that matter most:

| Variable | Default | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev`, `dev,h2` or `prod` |
| `SERVER_PORT` | `8080` | |
| `DATABASE_URL` | local MySQL | JDBC URL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `queueflow` | |
| `DDL_AUTO` | `update` | `prod` forces `validate` |
| `JWT_SECRET` | dev only | **required** in `prod`, min 32 chars |
| `JWT_EXPIRATION` | `8h` | |
| `FRONTEND_URL` | `http://localhost:5173` | OAuth redirect target |
| `FRONTEND_ALLOWED_ORIGINS` | empty | browser origins allowed to call the API |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | empty | disables Google sign-in when blank |
| `QUEUE_AGEING_INTERVAL_MINUTES` | `15` | |
| `QUEUE_AGEING_MAX_BOOST` | `2` | anti-starvation cap |
| `QUEUE_APPROACHING_THRESHOLD` | `2` | "you're next" threshold |
| `SEED_DATA_ENABLED` | `true` | always `false` in `prod` |
| `VITE_API_BASE_URL` | empty | empty means same-origin via proxy |

### A note on `/login`

The SPA route `/login` and Spring Security's OAuth2 callback prefix
`/login/oauth2/**` share a prefix. The dev proxy in `frontend/vite.config.js`
and the nginx config in `frontend/nginx.conf` both scope the proxy to
`/login/oauth2`, so `/login` is still served as the SPA page while the callback
reaches the backend. Proxying a bare `/login` would break the sign-in page.

---

## License

MIT
