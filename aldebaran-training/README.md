# Aldebaran Training API

This is the training and performance backend service for the StellarApex platform, responsible for
workout management (WODs), exercise catalog (Movements/Muscles), and athlete performance tracking
(Scores/PRs). It is a secure, stateless Spring Boot application designed to run as a containerized
microservice.

## Tech Stack

* **Framework**: Spring Boot 4.0
* **Language**: Java 25
* **Security**: Spring Security 6 (stateless), JWT (via Forward Auth)
* **Database**: PostgreSQL (managed by Flyway migrations)
* **Cache**: Redis (for caching WODs and Master Data)
* **Tooling**: MapStruct (DTO mapping), Lombok (boilerplate reduction)
* **API Docs**: Springdoc OpenAPI 3.0.1

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
```

This command runs all unit tests (*Test.java) and integration tests (*IT.java). Ensure that you have
a running PostgreSQL and Redis instance and the environment variables are set.

To build the JAR without running tests:

```bash 
./mvnw clean package -DskipTests 
```

### Docker

This project uses a multi-stage Dockerfile to create a minimal, non-root, Distroless-based JRE
image.
The image is built as part of the root docker-compose.yml.

```bash
docker build -t aldebaran-training .
```

## How to Run

This service is designed to be run as part of the full StellarApex stack using Docker Compose. This
ensures all dependencies (Database, Redis, Traefik) are correctly networked and configured.

### Run with Full Stack (via Root Docker Compose)

To run the entire platform (Traefik, Frontend, API, Admin, DB, and Cache), use the
`docker-compose.yml` in the project root.

```bash
docker-compose up -d
```

## API Documentation

OpenAPI documentation is enabled and available at:

* **API Docs Path:** `/aldebaran-docs`

Note: Swagger UI is currently disabled in the configuration (`springdoc.swagger-ui.enabled=false`).
The `bellatrix-swagger` service provides a centralized Swagger UI for all services in the ecosystem.

## Testing

To run integration tests:

```bash
./mvnw verify
```
