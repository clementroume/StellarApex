# StellarApex Project

StellarApex is a web application for CrossFit athletes and gyms: movement and muscle catalog, WOD
programming, scores and gym memberships. It is built from Spring Boot microservices, an Angular
frontend, and an infrastructure stack with reverse proxy, databases, and monitoring.

## Tech Stack

| Component     | Technology                             |
|---------------|----------------------------------------|
| Backend       | Java 25, Spring Boot 4.1                               |
| Frontend      | Angular 22, TypeScript, Tailwind CSS 4, DaisyUI 5      |
| Database      | PostgreSQL 18 (Flyway migrations)                      |
| Cache         | Redis 8                                                |
| Reverse Proxy | Traefik 3.7                                            |
| Monitoring    | Grafana, Prometheus, Loki, Tempo, Alloy, cAdvisor      |
| API Docs      | Springdoc OpenAPI 3.1                                  |
| Tests         | JUnit 5, Mockito, Testcontainers, Vitest               |

## Architecture Overview

```
(Browser)
    │
    ├─ https://stellar.apex              → Sirius (Angular Frontend)
    ├─ https://stellar.apex/antares      → Antares (Auth API)
    ├─ https://stellar.apex/aldebaran    → Aldebaran (Training API)
    ├─ https://docs.stellar.apex         → Bellatrix (API Docs)      [ADMIN]
    ├─ https://monitor.stellar.apex      → Vega (Grafana)            [ADMIN]
    └─ https://proxy.stellar.apex        → Altair (Traefik Dashboard) [ADMIN]
```

## Services

### Frontend

- **Sirius** (`sirius-app`): Angular 22 application with Tailwind CSS 4 and DaisyUI 5 (FR/EN)
    - Movements, muscles and WODs (list, detail, form) with UI adapted to the user's rights
    - Admin support page (`/admin/support`) to act as a user

### Backend APIs

- **Antares** (`antares-auth`): Authentication, users, gyms and memberships
    - JWT-based authentication with HttpOnly cookies, XSRF protection
    - Redis-backed refresh tokens and rate limiting
    - Forward Auth for Traefik: identity, platform role and gym memberships sent to Aldebaran
- **Aldebaran** (`aldebaran-training`): Training domain
    - Muscle and movement catalog, WODs, scores and leaderboards
    - Per-gym authorization evaluated on each resource, WOD capabilities for the UI
    - Shared PostgreSQL instance (own schema) and Redis cache with Antares

### Infrastructure

- **Bellatrix** (`bellatrix-swagger`): Unified API documentation gateway
- **Altair** (`altair-proxy`): Traefik reverse proxy
    - HTTPS termination
    - Forward authentication
    - Rate limiting
- **Castor** (`castor-db`): PostgreSQL database
- **Pollux** (`pollux-cache`): Redis cache

### Observability (The "Vega" Suite)

- **Vega-Grafana**: Visualization dashboards for metrics, logs, and traces.
- **Vega-Prometheus**: Time-series database for metrics.
- **Vega-Loki**: Log aggregation system.
- **Vega-Tempo**: Distributed tracing backend.
- **Vega-Alloy**: OpenTelemetry collector for telemetry data.
- **Vega-cAdvisor / exporters**: Container, PostgreSQL and Redis metrics.

The observability stack is defined in `docker-compose.observability.yml`.

## Prerequisites

- `JDK 25`
- `Node.js 26`
- `Docker`
- `openssl`(usually pre-installed on macOS/Linux)

## Quick Start

### 1. Environment Configuration

Create a `.env` file in the project root:

```txt
# === CASTOR (PostgreSQL) ===
CASTOR_DB=
CASTOR_USERNAME=
CASTOR_PASSWORD=

# === POLLUX (Redis) ===
POLLUX_PASSWORD=

# === ANTARES (Auth Api) ===
# Generate with 'openssl rand -base64 64'
ANTARES_JWT_SECRET=
COOKIE_DOMAIN=
# Token required to create a gym
SECRET_ADMIN_TOKEN=

# === ANTARES <-> ALDEBARAN ===
# Shared secret proving that identity headers come from Antares (same value for both services)
INTERNAL_SECRET=

# === ADMIN USER ===
ADMIN_FIRSTNAME=
ADMIN_LASTNAME=
ADMIN_EMAIL=
ADMIN_PASSWORD=
```

### 2. Generate Local Certificates

We need self-signed certificates for Traefik to serve HTTPS locally.

```bash
# Create the certs directory
mkdir -p certs

# Generate the certificate for *.stellar.apex
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout certs/local.key -out certs/local.crt \
  -subj "/CN=*.stellar.apex"
```

### 3. Configure Hosts File

Add to `/etc/hosts` (Linux/macOS) or `C:\Windows\System32\drivers\etc\hosts` (Windows):

```
127.0.0.1 stellar.apex monitor.stellar.apex proxy.stellar.apex docs.stellar.apex
::1 stellar.apex monitor.stellar.apex proxy.stellar.apex docs.stellar.apex
 
```

### 4. Start the Stack

```bash
docker compose up --build -d
# Optional: observability stack
docker compose -f docker-compose.observability.yml up -d
```

At startup, Antares creates the platform admin defined by the `ADMIN_*` variables. While the
software is in development, the `dev` Spring profile (set in `docker-compose.yml`) also seeds test
gyms, test accounts, gym WODs and their scores; this is temporary (see the Antares and Aldebaran READMEs).

### 5. Access the Application

| Service                | URL                            | Access        |
|------------------------|--------------------------------|---------------|
| Sirius (Frontend)      | https://stellar.apex           | Public        |
| Antares Auth API       | https://stellar.apex/antares   | Public        |
| Aldebaran Training API | https://stellar.apex/aldebaran | Authenticated |
| Bellatrix (API Docs)   | https://docs.stellar.apex      | ADMIN only    |
| Vega (Grafana)         | https://monitor.stellar.apex   | ADMIN only    |
| Altair (Traefik)       | https://proxy.stellar.apex     | ADMIN only    |

**Note**: Your browser will warn about the self-signed certificate. This is expected for local
development.

### Database Access (Local)

Direct access to databases for development:

- **PostgreSQL**: `localhost:5432`
- **Redis**: `localhost:6379`

## Development

| Service   | Tests (unit + integration)        | Build / Run                              |
|-----------|-----------------------------------|------------------------------------------|
| Sirius    | `cd sirius-app && npm test`       | `npm run build`, `npm start` (port 4200) |
| Antares   | `cd antares-auth && ./mvnw verify` | `docker compose build antares-auth`     |
| Aldebaran | `cd aldebaran-training && ./mvnw verify` | `docker compose build aldebaran-training` |

Backend integration tests (`*IT`) start PostgreSQL and Redis with Testcontainers, so Docker must be
running. `npm start` proxies `/antares` and `/aldebaran` to `https://stellar.apex`
(`sirius-app/proxy.conf.json`), so the Docker stack must be up. CI (`.github/workflows/ci.yml`) runs
the same commands for each service whose files changed.

## Security

### Authentication Flow

1. User authenticates via Antares Auth API
2. Receives JWT tokens in HttpOnly cookies
3. Traefik validates each request via Forward Auth:
    - `/antares/auth/verify/api` for Aldebaran
    - `/antares/auth/verify/admin` for the admin hosts
4. Antares returns identity headers that Traefik copies onto the request to Aldebaran:
    - `X-Auth-User-Id`, `X-Auth-User-Locale`
    - `X-Auth-User-Role`: platform role (`ADMIN` or `USER`)
    - `X-Auth-Memberships`: every usable gym membership with its effective permissions
      (`gymId:ROLE:PERM+PERM,...`)
    - `X-Auth-Actor-Id`: the admin's id during a support session
    - `X-Internal-Secret`: proves the headers come from Antares

Traefik only forwards the headers listed in `dynamic/routes.yml`, which also overwrites any value
forged by a client.

### Protected Resources

- **ADMIN only**: `docs.stellar.apex`, `monitor.stellar.apex`, `proxy.stellar.apex`
- **Authenticated**: every Aldebaran endpoint and every Antares endpoint except `/antares/auth/**`

### Authorization Model

Rules are enforced by the backends; Sirius only adapts its UI to what they return.

- **Platform roles**: `ADMIN` (every right) and `USER`.
- **Gym roles and effective permissions** (`WOD_WRITE`, `SCORE_VERIFY`, `MANAGE_MEMBERSHIPS`,
  `MANAGE_SETTINGS`): `OWNER` and `PROGRAMMER` hold all of them, a `COACH` holds the ones granted to
  them, an `ATHLETE` holds none.
- **Several gyms**: a user can belong to several gyms (e.g. a physical box and a virtual programming).
  Rights are evaluated on the gym of each resource.
- **WODs**: readable when public, from one of the user's gyms, or authored by the user. Gym WODs are
  written with `WOD_WRITE` on that gym; personal WODs only by their author. A WOD with logged scores
  is locked. The API returns `capabilities` (`canModify`, `locked`) for each WOD.
- **Scores**: athletes log and manage their own scores on the WODs they can read, in one of their
  gyms; `SCORE_VERIFY` on that gym lets staff correct or delete their athletes' scores.
- **Competitions**: a gym WOD can be flagged "competition" (never public), for instance a public Open
  WOD copied into the gym. Its judges (`SCORE_VERIFY` in the gym) log scores for the gym's members and
  verify scores; a verified score is locked for its athlete. The competition leaderboard shows
  verified scores by default. Outside competitions scores are never verified.
- **Service-to-service**: Aldebaran asks Antares whether an athlete belongs to a gym through an
  internal API (`/antares/internal`, shared secret, not routed by Traefik).
- **Leaderboards**: per WOD and level, worldwide or for the user's gyms only ("My gym(s)"); a score
  on a public WOD is attached to the gym where it was performed (automatic for a single gym). Names are shown to
  members of the same gym; elsewhere only if the athlete opted in (My account), else "anonymous". One personal record per athlete
  and WOD (Rx+ > Rx > Scaled > Custom, then performance; a finished time beats a time-capped score).
- **Gym roles by type**: a physical gym has exactly one `OWNER`, any number of `COACH` and
  `ATHLETE`; a virtual (programming) gym has exactly one `PROGRAMMER` and any number of `ATHLETE`.
  The owner/programmer role changes only through an explicit transfer.
- **Admin support session**: from Sirius `/admin/support`, an admin acts as a real user (a gym member
  who reported a bug, a test account) for 30 minutes, with a mandatory reason. Writes are allowed;
  Antares records every session and Aldebaran logs every write with the admin's id. The admin's own
  session is kept, and an expired session never falls back silently to admin rights.

### Rate Limiting

- **Admin endpoints**: 200 req/min (burst: 100)
- **API endpoints**: 100 req/min (burst: 50)

## Monitoring

The "Vega" suite provides a complete observability stack:

- Grafana: https://monitor.stellar.apex – Central dashboard for logs, metrics, and traces.

## Production Considerations

1. **Replace self-signed certificates** with valid SSL certificates
2. **Configure secure JWT secrets** (min 256 bits)
3. **Use strong database passwords**
4. **Enable HTTPS-only cookies** in production
5. **Configure appropriate rate limits**
6. **Set up log aggregation** (ELK, Loki, etc.)
7. **Implement backup strategy** for databases
8. **Use secrets management** (Vault, AWS Secrets Manager)

## Project Structure

```
stellarapex/
├── antares-auth/          # Authentication API
├── aldebaran-training/    # Training API
├── bellatrix-swagger/     # API Documentation Gateway
├── sirius-app/            # Angular Frontend
├── monitoring/            # Observability stack configuration (Grafana, Prometheus, etc.)
├── dynamic/               # Traefik dynamic configuration
│   ├── routes.yml         # Routing rules, middlewares, Forward Auth headers
│   └── tls.yaml           # TLS certificates
├── .github/workflows/     # CI (tests and builds per service) and CodeQL
├── .claude/               # Claude Code project settings
├── certs/                 # SSL certificates (gitignored)
├── .env                   # Environment variables (gitignored)
├── docker-compose.yml     # Service orchestration
├── docker-compose.observability.yml  # Vega observability stack
├── CLAUDE.md              # Context for Claude Code (one per service as well)
└── README.md
```

## Troubleshooting

### Services won't start

- Check Docker logs: `docker compose logs <service-name>`
- Verify `.env` file configuration
- Ensure ports 80, 443, 5432, 6379 are available

### Database connection errors

- Wait for health checks: `docker compose ps`
- Verify credentials in `.env`
- Check network connectivity: `docker network inspect stellar-net`

### SSL certificate warnings

- Normal for self-signed certificates
- Proceed past the warning or import `certs/local.crt` as trusted

### Forward Auth fails

- Ensure Antares Auth is running
- Check the `/antares/auth/verify/api` and `/antares/auth/verify/admin` endpoints
- Verify that `INTERNAL_SECRET` is the same for Antares and Aldebaran
- A header added to the Forward Auth response must also be listed in `dynamic/routes.yml`
