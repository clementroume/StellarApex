# Aldebaran Training Service

Aldebaran Training Service is a Spring Boot application focused on Workouts and Performance management. It is part of the Stellar Apex ecosystem.

## Tech Stack

*   **Java:** 25
*   **Framework:** Spring Boot 4.0.0
*   **Database:** PostgreSQL (with Flyway for migrations)
*   **Caching:** Redis
*   **API Documentation:** SpringDoc OpenAPI
*   **Observability:** Micrometer, Prometheus
*   **Tools:** Lombok, MapStruct, Commons CSV, POI OOXML

## Prerequisites

*   Java 25
*   Maven
*   PostgreSQL
*   Redis

## Configuration

The application uses the following environment variables for configuration. You should set these before running the application locally.

| Variable | Description |
| :--- | :--- |
| `CASTOR_DB` | PostgreSQL Database Name |
| `CASTOR_USERNAME` | PostgreSQL Username |
| `CASTOR_PASSWORD` | PostgreSQL Password |
| `POLLUX_PASSWORD` | Redis Password |

The application listens on port `8081` by default.

## Running the Application

### Locally with Maven

```bash
./mvnw spring-boot:run
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
