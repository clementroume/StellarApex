# StellarAtlas Project

StellarAtlas is a modern, full-stack web application featuring a Spring Boot backend (
`antares-auth`), an Angular frontend (`sirius-app`), and a Spring Boot Admin server (`vega-admin`).
The entire stack is containerized with Docker and served securely via a Traefik reverse proxy.

## Tech Stack

- **Backend (Auth)**: Java 25, Spring Boot 3.5, Spring Security, JWT (`antares-auth`)
- **Backend (Admin)**: Spring Boot Admin (`vega-admin`)
- **Frontend**: Angular 20, TypeScript, Tailwind CSS 4, DaisyUI 5 (`sirius-app`)
- **Database**: PostgreSQL (`castor-db`)
- **Cache & Sessions**: Redis (`pollux-cache`)
- **Reverse Proxy**: Traefik (handles HTTPS, local domains, rate limiting)
- **Deployment**: Docker & Docker Compose

## Architecture Overview

All local traffic is managed by Traefik, which provides a single secure entry point (`https://...`)
and routes requests to the appropriate service.

``` 
(Your Machine)
        │
        ├─ Public Access ([https://stellar.atlas](https://stellar.atlas))
        │  │
        │  ├─ / (Routes Angular)      → [Traefik] → [sirius-app (Nginx)]
        │  └─ /antares/* (API)        → [Traefik] → [antares-auth (Spring)]
        │
        ├─ Admin Access
        │  ├─ [https://admin.stellar.atlas](https://admin.stellar.atlas)     → [Traefik] → [vega-admin (Spring)]
        │  └─ [https://proxy.stellar.atlas](https://proxy.stellar.atlas)     → [Traefik] (Internal Dashboard)
        │
        └─ Direct access (localhost only)
           ├─ localhost:5432  → [castor-db]
           └─ localhost:6379  → [pollux-cache]
```

## Prerequisites

- `JDK 25`
- `Node.js 24`
- `Docker`
- `openssl`(usually pre-installed on macOS/Linux)
- `htpasswd`(comes with`apache2-utils`on Linux, or use an online generator)

## Local Setup

### 1. Environment Configuration

Create an`.env`file in the project's root directory. This file contains all the sensitive variables
required to run the application.

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

# === ADMIN - ANTARES & VEGA ===
ADMIN_USERNAME=
ADMIN_FIRSTNAME=
ADMIN_LASTNAME=
ADMIN_EMAIL=
ADMIN_PASSWORD=

# === ALTAIR (Traefik) ===
# Generate with 'htpasswd -nb user password', escaping '$' as '$$'
ALTAIR_DASHBOARD_AUTH=
```

### 2. Generate Local Certificates

We need self-signed certificates for Traefik to serve HTTPS locally.

```bash
# Create the certs directory
mkdir -p certs

# Generate the certificate for *.stellar.atlas
openssl req -x509 -nodes -days 365 -newkey rsa:2048
-keyout certs/local.key -out certs/local.crt
-subj "/CN=*.stellar.atlas"
```

### 3. Update Your Host File

Your computer needs to know that these domains point to your local machine.

- **macOS/Linux**: Edit`/etc/hosts`
- **Windows**: Edit`C:\Windows\System32\drivers\etc\hosts`

Add the following lines:

``` 
127.0.0.1 stellar.atlas auth.stellar.atlas admin.stellar.atlas proxy.stellar.atlas 
::1 stellar.atlas auth.stellar.atlas admin.stellar.atlas proxy.stellar.atlas 
```

### 4. Build and Run Containers

This command will build the service images and start all services.

``` bash
docker compose up --build -d
```

## Accessing the Application

Your stack is now running. Your browser will show a security warning, which is normal (self-signed
certificate). You can safely "proceed" or "accept the risk".

- **Sirius - Angular frontend served by Nginx**:`https://stellar.atlas`
- **Antares - Auth and Users SpringBoot Api**:`https://auth.stellar.atlas`
    - **Antares (Sirius)**:`https://stellar.atlas/antares`
    - **Antares (Swagger UI)**:`https://stellar.atlas/swagger-ui.html`
- **Vega - Admin SpringBoot Server**:`https://admin.stellar.atlas`
- **Altair - Traefik Dashboard**:`https://proxy.stellar.atlas`

**Database & Cache Access (Local Development)**

The databases are securely exposed_only_to`localhost`on your host machine.

- **Castor - PostgreSQL Database (`castor-db`)**:
    - **Host**:`127.0.0.1`
    - **Port**:`5432`
    - **User/Password**: (from your`.env`)

- **Pollux - Redis Cache (`pollux-cache`)**:
    - **Host**:`127.0.0.1`
    - **Port**:`6379`
    - **Password**: (from your`.env`)