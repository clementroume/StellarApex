# Antares Project

Antares is a modern, full-stack web application featuring a Spring Boot backend (`antares-api`), an
Angular frontend (`antares-app`), and a Spring Boot Admin server (`antares-admin`). The entire stack
is containerized with Docker and served securely via a Traefik reverse proxy.

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.5, Spring Security, JWT, Flyway, MapStruct

- **Frontend**: Angular 20, TypeScript, Tailwind CSS 4, DaisyUI 5

- **Database**: PostgreSQL

- **Cache & Sessions**: Redis

- **Reverse Proxy**: Traefik (handles HTTPS, local domains, rate limiting)

- **Monitoring**: Spring Boot Admin Server

- **Deployment**: Docker & Docker Compose

## Architecture Overview

All local traffic is managed by Traefik, which provides a single secure entry point (`https://...`)
and routes requests to the appropriate service.

```
(Your Mac)
    |
    |  Accesses:
    |  - [https://antares.local](https://antares.local)      -> [Traefik] -> [antares-app (Nginx)]
    |  - [https://antares.local/api](https://antares.local/api)  -> [Traefik] -> [antares-api (Spring)]
    |  - [https://api.antares.local](https://api.antares.local)  -> [Traefik] -> [antares-api (Spring)]
    |  - [https://admin.antares.local](https://admin.antares.local) -> [Traefik] -> [antares-admin (Spring)]
    |  - [https://dashboard.antares.local](https://dashboard.antares.local) -> [Traefik] (Internal Dashboard)
    |
    |  Direct Access via localhost (for Dev):
    |  - localhost:5432             -> [antares-postgres]
    |  - localhost:6379             -> [antares-redis]
```

## Prerequisites

- [JDK 21](https://adoptium.net/ "null")or newer

- [Node.js 22](https://nodejs.org/ "null")or newer

- [Docker](https://www.docker.com/products/docker-desktop/ "null")& Docker Compose

- `openssl`(usually pre-installed on macOS/Linux)

- `htpasswd`(comes with`apache2-utils`on Linux, or use an online generator)

## Local Setup

### 1. Environment Configuration

Create an`.env`file in the project's root directory. This file contains all the sensitive variables
required to run the application.

```
# === Database Credentials ===
POSTGRES_DB=antares
POSTGRES_USER=antares
POSTGRES_PASSWORD=your_strong_postgres_password

# === Cache Credentials ===
REDIS_PASSWORD=your_strong_redis_password

# === JWT Security Configuration ===
# Generate with 'openssl rand -base64 64'
JWT_SECRET=your_long_and_random_jwt_secret_key

# === Default Admin User Configuration ===
# Used by both 'antares-api' and 'antares-admin'
ADMIN_FIRSTNAME=Admin
ADMIN_LASTNAME=User
ADMIN_EMAIL=admin@antares.local
ADMIN_PASSWORD=your_strong_admin_password

# === Traefik Dashboard Authentication ===
# This is the HASH, not the plain text password.
# 1. Generate it by running: htpasswd -nb "admin@antares.local" "your_strong_admin_password"
# 2. Take the output (e.g., admin@antares.local:$apr1$...)
# 3. Add '$$' to escape each '$' (e.g., admin@antares.local:$$apr1$$...)
TRAEFIK_DASHBOARD_AUTH=your_htpasswd_hash_with_escaped_dollars
```

### 2. Generate Local Certificates

We need self-signed certificates for Traefik to serve HTTPS locally.

```
# Create the certs directory
mkdir -p certs

# Generate the certificate for *.antares.local
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout certs/local.key -out certs/local.crt \
  -subj "/CN=*.antares.local"
```

### 3. Update Your Host File

Your computer needs to know that these domains point to your local machine.

- **macOS/Linux**: Edit`/etc/hosts`

- **Windows**: Edit`C:\Windows\System32\drivers\etc\hosts`

Add the following lines:

```
127.0.0.1       antares.local api.antares.local admin.antares.local dashboard.antares.local
::1             antares.local api.antares.local admin.antares.local dashboard.antares.local
```

### 4. Build and Run Containers

This command will build the API and app images, then start all services (api, app, admin, traefik,
db, redis).

```
docker compose up --build -d
```

## Accessing the Application

Your stack is now running. Your browser will show a security warning, which is normal (self-signed
certificate). You can safely "proceed" or "accept the risk".

- **Main Application**:`https://antares.local`

- **Spring Boot Admin**:`https://admin.antares.local`

- **Traefik Dashboard**:`https://dashboard.antares.local`

- **API (Swagger UI)**:`https://antares.local/swagger-ui.html`

- **API (Dedicated)**:`https://api.antares.local`

**Database & Cache Access (Local Development)**

The databases are securely exposed_only_to`localhost`on your host machine.

- **PostgreSQL**:

    - **Host**:`127.0.0.1`

    - **Port**:`5432`

    - **User/Password**: (from your`.env`)

- **Redis**:

    - **Host**:`127.0.0.1`

    - **Port**:`6379`

    - **Password**: (from your`.env`)