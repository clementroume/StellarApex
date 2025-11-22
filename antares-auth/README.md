# Antares Auth API

This is the core backend service for the Antares platform, responsible for user authentication,
session management, and user profile operations. It is a secure, stateless Spring Boot application
designed to run as a containerized microservice.

## Tech Stack

* **Framework**: Spring Boot 3.5
* **Language**: Java 25
* **Security**: Spring Security 6 (stateless), JWT (via `jjwt`)
* **Authentication**: HttpOnly Cookies (Access + Refresh Tokens) & Double-Submit Cookie CSRF
  Protection
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

### 1. Standalone (for API-only development)

This is the recommended way to develop and debug the API locally.

1. **Start Dependencies:** Start the required PostgreSQL and Redis containers using the main
   `docker-compose.yml` at the project root.
   ```bash
   # From the project root
   docker compose up -d castor-db pollux-cache
   ```
2. **Run the Application:** Run the `AntaresAuth` main class directly from your IDE.

The API will be available at `http://localhost:8080` (main) and `http://localhost:9090` (actuator).

### 2. Full Stack (via Root Docker Compose)

To run the entire platform (Traefik, Frontend, API, Admin, DB, and Cache), use the
`docker-compose.yml` in the project root.

```bash
# From the project root
docker compose up --build -d
```

## API Endpoints

The API will be accessible via the Traefik proxy at `https://antares.local/antares`.

### Authentication (`/antares/auth`)

* `POST /register`: Register a new user.
* `POST /login`: Authenticate and receive HttpOnly session cookies.
* `POST /logout`: Invalidate session and clear cookies.
* `POST /refresh-token`: Use the refresh token (cookie) to get a new access token.

### User (`/antares/users`)

* `GET /me`: Get the profile of the currently authenticated user.
* `PUT /me/profile`: Update the user's first name, last name, or email.
* `PATCH /me/preferences`: Update the user's locale (language) and theme.
* `PUT /me/password`: Change the user's password.

### API Documentation

When the application is running (in any mode), the OpenAPI (Swagger) documentation is available at:
**`https://stellar.atlas/swagger-ui.html`** (if using the full stack) or
`http://localhost:8080/swagger-ui.html` (if running standalone).

Access to the documentation is restricted to users with the `ROLE_ADMIN`.

## Database Migrations

Database schema changes are managed by **Flyway**. SQL migration scripts are located in
`src/main/resources/db/migration`.

Flyway runs automatically on application startup to apply any pending migrations.