# Vega Admin Server

Vega is the monitoring and administration server for the StellarAtlas platform. It utilizes Spring
Boot Admin to provide a centralized interface for managing registered microservices (such as
`antares-auth`), viewing logs, tracking metrics, and monitoring health status.

## Tech Stack

- **Framework**: Spring Boot 3.5
- **Core**: Spring Boot Admin Server 3.5
- **Language**: Java 25
- **Security**: Spring Security (Session-based, CSRF Protection, Remember-Me)

## Security Configuration

Vega uses a stateful, session-based security model secured by Spring Security.

- **Authentication**: In-Memory Authentication using credentials injected via environment variables.
- **CSRF**: Enabled using`CookieCsrfTokenRepository`with`HttpOnly`set to false to allow client-side
  interaction if necessary.
- **Session Management**: Configured with a generated UUID key for Remember-Me tokens (valid for 2
  weeks per session lifespan).
- **Proxy Support**: Configured to trust`X-Forwarded-Proto`headers, allowing correct HTTPS
  identification behind the Traefik proxy.

## Prerequisites

- Docker & Docker Compose
- A configured`.env`file in the project root.

## Environment Configuration

The following variables must be defined in the root`.env`file for the application to start:

| Variable         | Description                    |
|------------------|--------------------------------|
| `AMIN_USERNAME`  | Login username (email format). |
| `ADMIN_PASSWORD` | Login password.                |

## How to Run

This service is designed to be run as part of the full StellarAtlas stack via the root Docker
Compose.

## Accessing the Application

- **URL (Traefik)**:`https://admin.stellar.atlas`
- **Local Port**:`9091`(Exposed internally within the Docker network)