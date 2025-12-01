# StellarApex Project

StellarApex is a modern, full-stack web application featuring Spring Boot microservices, an Angular
frontend, and a comprehensive infrastructure stack with reverse proxy, databases, and monitoring.

## Tech Stack

| Component     | Technology                             |
|---------------|----------------------------------------|
| Backend       | Java 25, Spring Boot 4.0               |
| Frontend      | Angular 20, TypeScript, Tailwind CSS 4 |
| Database      | PostgreSQL 16                          |
| Cache         | Redis 7                                |
| Reverse Proxy | Traefik 3.5                            |
| Monitoring    | Spring Boot Admin 3.5                  |
| API Docs      | Springdoc OpenAPI 3.0                  |

## Architecture Overview

```
(Browser)
    │
    ├─ https://stellar.apex              → Sirius (Angular Frontend)
    ├─ https://stellar.apex/antares      → Antares (Auth API)
    ├─ https://stellar.apex/aldebaran    → Aldebaran (Training API)
    ├─ https://docs.stellar.apex         → Bellatrix (API Docs) [ADMIN]
    ├─ https://admin.stellar.apex        → Vega (Monitoring) [ADMIN]
    └─ https://proxy.stellar.apex        → Altair (Traefik Dashboard) [ADMIN]
```

## Services

### Frontend

- **Sirius** (`sirius-app`): Angular 20 application with Tailwind CSS 4 and DaisyUI 5

### Backend APIs

- **Antares** (`antares-auth`): Authentication, authorization, and user management
    - JWT-based authentication with HttpOnly cookies
    - Redis-backed refresh tokens and rate limiting
    - PostgreSQL for user data
- **Aldebaran** (`aldebaran-training`): Workout management and performance tracking
    - Exercise catalog and workout logging
    - Performance analytics
    - Shared database and cache with Antares

### Infrastructure

- **Bellatrix** (`bellatrix-swagger`): Unified API documentation gateway
- **Vega** (`vega-admin`): Spring Boot Admin monitoring server
- **Altair** (`altair-proxy`): Traefik reverse proxy
    - HTTPS termination
    - Forward authentication
    - Rate limiting
- **Castor** (`castor-db`): PostgreSQL database
- **Pollux** (`pollux-cache`): Redis cache

## Prerequisites

- `JDK 25`
- `Node.js 24`
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
openssl req -x509 -nodes -days 365 -newkey rsa:2048
-keyout certs/local.key -out certs/local.crt
-subj "/CN=*.stellar.apex"
```

### 3. Configure Hosts File

Add to `/etc/hosts` (Linux/macOS) or `C:\Windows\System32\drivers\etc\hosts` (Windows):

```
127.0.0.1 stellar.apex admin.stellar.apex proxy.stellar.apex docs.stellar.apex
::1 stellar.apex admin.stellar.apex proxy.stellar.apex docs.stellar.apex
```

### 4. Start the Stack

```bash
docker compose up --build -d
```

### 5. Access the Application

| Service                | URL                            | Access        |
|------------------------|--------------------------------|---------------|
| Sirius (Frontend)      | https://stellar.apex           | Public        |
| Antares Auth API       | https://stellar.apex/antares   | Public        |
| Aldebaran Training API | https://stellar.apex/aldebaran | Authenticated |
| Bellatrix (API Docs)   | https://docs.stellar.apex      | ADMIN only    |
| Vega (Monitoring)      | https://admin.stellar.apex     | ADMIN only    |
| Altair (Traefik)       | https://proxy.stellar.apex     | ADMIN only    |

**Note**: Your browser will warn about the self-signed certificate. This is expected for local
development.

### Database Access (Local)

Direct access to databases for development:

- **PostgreSQL**: `localhost:5432`
- **Redis**: `localhost:6379`

## Security

### Authentication Flow

1. User authenticates via Antares Auth API
2. Receives JWT tokens in HttpOnly cookies
3. Traefik validates tokens via Forward Auth (`/antares/auth/verify`)
4. Access granted to protected resources

### Protected Resources

- **ADMIN endpoints**: `/admin.*`, `/proxy.*`, `/docs.*`
- **Authenticated endpoints**: All API endpoints except `/antares/auth/**`

### Rate Limiting

- **Admin endpoints**: 200 req/min (burst: 100)
- **API endpoints**: 100 req/min (burst: 50)

## Monitoring

### Actuator Endpoints

Each service exposes health and metrics:

- **Antares**: `http://antares-auth:9090/actuator`
- **Aldebaran**: `http://aldebaran-training:9092/actuator`
- **Bellatrix**: `http://bellatrix-swagger:9093/actuator`

### Logs

View service logs:

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f antares-auth
docker compose logs -f aldebaran-training
```

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
├── vega-admin/            # Monitoring Server
├── dynamic/               # Traefik dynamic configuration
│   ├── routes.yml         # Routing rules
│   └── tls.yaml           # TLS certificates
├── certs/                 # SSL certificates (gitignored)
├── .env                   # Environment variables (gitignored)
├── docker-compose.yml     # Service orchestration
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
- Check `/antares/auth/verify` endpoint
- Verify JWT secret consistency across services
