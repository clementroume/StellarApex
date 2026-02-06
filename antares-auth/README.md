# Antares Auth API

This is the authentication backend service for the StellarApex platform, responsible for user
authentication, session management, and user profile operations. It is a secure, stateless Spring
Boot application designed to run as a containerized microservice.

## Tech Stack

* **Framework**: Spring Boot 4.0
* **Language**: Java 25
* **Security**: Spring Security 6 (stateless), OAuth2 Resource Server (JWT)
* **Authentication**: HttpOnly Cookies (Access + Refresh Tokens)
* **CSRF Protection**: Double-Submit Cookie
* **Database**: PostgreSQL (managed by Flyway migrations)
* **Cache**: Redis (for secure refresh token storage)
* **Tooling**: MapStruct (DTO mapping), Lombok (boilerplate reduction)
* **API Docs**: Springdoc (OpenAPI / Swagger)

## Prerequisites

* JDK 25
* Docker & Docker Compose
* A configured `.env` file in the project root (see the root `README.md`).

## How to Build

This project uses the Maven Wrapper (`mvnw`), which will download the correct Maven version
automatically.

### Build the JAR

To build the executable JAR and run all tests:

```bash
./mvnw verify
````

*This command runs all unit tests (`*Test.java`) and integration tests (`*IT.java`).*

To build the JAR *without* running tests:

```bash
./mvnw clean package -DskipTests
```

### Build the Docker Image

This project uses a multi-stage `Dockerfile` to create a minimal, non-root, Alpine-based JRE image.

The image is built as part of the root `docker-compose.yml`.

```bash
# From the project root
docker compose build antares-auth
```

## How to Run

This service is designed to be run as part of the full StellarAtlas stack using Docker Compose. This
ensures all dependencies (Database, Redis, Traefik) are correctly networked and configured.

### Run with Full Stack (via Root Docker Compose)

To run the entire platform (Traefik, Frontend, API, Admin, DB, and Cache), use the
`docker-compose.yml` in the project root.

1. **Build and start the services:**
   ```bash
   docker compose up --build -d
   ```

2. **Access the API:** The API will be available through the Traefik proxy at:

- https://stellar.apex/antares (Public API)
- https://stellar.apex/swagger-ui.html (Documentation - Requires Admin Access)

3. **Logs:** To follow the logs for this specific service:

```Bash
docker compose logs -f antares-auth
```

## API Endpoints

### Authentication (`/antares/auth`)

* `POST /register`: Register a new user.
* `POST /login`: Authenticate and receive HttpOnly session cookies.
* `POST /logout`: Invalidate session and clear cookies.
* `POST /refresh-token`: Use the refresh token (cookie) to get a new access token.
* `GET /verify/admin`: Forward Auth for admin-only services (e.g., Grafana, Traefik).
* `GET /verify/api`: Forward Auth for backend API services (e.g., Aldebaran).
* `POST /impersonate/{userId}`: [ADMIN] Assume the identity of another user.

### User (`/antares/users`)

* `GET /me`: Get the profile of the currently authenticated user.
* `PUT /me/profile`: Update the user's first name, last name, or email.
* `PATCH /me/preferences`: Update the user's locale (language) and theme.
* `PUT /me/password`: Change the user's password.
* `DELETE /me`: Delete the current user's account.

### Gym Management (`/antares/gyms`)

* `POST /`: Create a new Gym (tenant).
* `POST /join`: Join a gym using an enrollment code.
* `GET /{gymId}/settings`: [OWNER/ADMIN] Get gym settings.
* `PUT /{gymId}/settings`: [OWNER/ADMIN] Update gym settings.
* `GET /`: [ADMIN] List all gyms with status filter.

### Membership Management (`/antares/memberships`)

* `GET /`: [OWNER/ADMIN] List members of a gym.
* `PUT /{id}`: [OWNER/ADMIN] Update a member's role, status, or permissions.
* `DELETE /{id}`: [OWNER/ADMIN] Remove a member from a gym.

## Database Migrations

Flyway manages database schema changes****. SQL migration scripts are located in
`src/main/resources/db/migration`.

Flyway runs automatically on application startup to apply any pending migrations.