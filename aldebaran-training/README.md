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
```

Ensure that you have a running PostgreSQL and Redis instance and the environment variables are set.

### Docker

The project includes a `Dockerfile` for building a container image.

```bash
docker build -t aldebaran-training .
```

The application is designed to be run as part of the Stellar Apex ecosystem using Docker Compose. The `docker-compose.yml` file in the root of the repository defines the services, including database (`castor-db`) and cache (`pollux-cache`).

To run the full stack (from the root directory):
```bash
docker-compose up -d
```

## API Documentation

OpenAPI documentation is enabled and available at:

*   **API Docs Path:** `/aldebaran-docs`

Note: Swagger UI is currently disabled in the configuration (`springdoc.swagger-ui.enabled=false`). The `bellatrix-swagger` service provides a centralized Swagger UI for all services in the ecosystem.

## Observability

The application exposes management endpoints for monitoring and health checks.

*   **Management Port:** `9092`
*   **Endpoints:** All endpoints are exposed (`management.endpoints.web.exposure.include=*`).
*   **Health Check:** `/actuator/health`

## Testing

To run integration tests:

```bash
./mvnw verify
```
