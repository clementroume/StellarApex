# Bellatrix Swagger Gateway

Bellatrix is a unified API documentation gateway that aggregates OpenAPI specifications from all
StellarApex microservices into a single Swagger UI interface.

## Purpose

Provides centralized access to API documentation for:

- **Antares Auth**: Authentication and user management
- **Aldebaran Training**: Workout and performance tracking

## Architecture

```
Browser (HTTPS)
    ↓
Traefik (Forward Auth: ADMIN only)
    ↓
Bellatrix :8888
    ↓
    ├─ /antares-docs → Antares :8080/antares-docs
    └─ /aldebaran-docs → Aldebaran :8081/aldebaran-docs
```

## Access

- **Production**: `https://docs.stellar.apex`
- **Local**: `http://localhost:8888`

Access requires ADMIN role authentication via Antares Auth.

## Security

- **Authentication**: Delegated to Traefik via Forward Auth middleware
- **Authorization**: ADMIN role required (verified by `/antares/auth/verify/admin`)
- **CORS**: Configured for `stellar.apex` domain and subdomains

## Configuration

Key properties in `application.properties`:

```properties
# Internal service URLs
swagger.services.antares.url=http://antares-auth:8080
swagger.services.aldebaran.url=http://aldebaran-training:8081
# API documentation endpoints
springdoc.swagger-ui.urls[0].name=Antares Auth
springdoc.swagger-ui.urls[0].url=/antares-docs
springdoc.swagger-ui.urls[1].name=Aldebaran Training
springdoc.swagger-ui.urls[1].url=/aldebaran-docs
```

## Adding a New Service

1. **Update `application.properties`**:
   ```properties
   swagger.services.newservice.url=http://new-service:8083
   springdoc.swagger-ui.urls[2].name=New Service
   springdoc.swagger-ui.urls[2].url=/newservice-docs
   ```

2. **Add proxy method in `BellatrixController`**:
   ```java
   @GetMapping(value = "/newservice-docs", produces = MediaType.APPLICATION_JSON_VALUE)
   public String getNewServiceApiDocs() {
       return restClient.get()
           .uri(newServiceUrl + "/newservice-docs")
           .retrieve()
           .body(String.class);
   }
   ```

## Build

```bash
./mvnw clean package
docker build -t bellatrix-swagger:latest .
```

## Run

Part of the StellarApex stack via Docker Compose:

```bash
docker compose up bellatrix-swagger
```

## Technical Stack

- **Framework**: Spring Boot 4.0
- **Java**: 25
- **Springdoc**: 3.0.1
- **Security**: Spring Security (permit-all mode, delegated to Traefik)
