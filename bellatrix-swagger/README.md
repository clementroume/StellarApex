# Swagger Gateway

The Swagger Gateway is a lightweight Spring Boot microservice that aggregates OpenAPI documentation from all microservices in the StellarApex platform.

## Features

- 🔄 **Dynamic API Discovery**: Automatically fetches OpenAPI specs from backend services
- 🔐 **Unified Security**: Single authentication point (requires ADMIN role)
- 🎨 **Grouped APIs**: Clean dropdown selector in Swagger UI
- 🚀 **Server URL Rewriting**: Ensures all API calls go through the correct gateway URL
- 📊 **Multiple Services**: Currently supports Antares Auth & Orion Training

## Architecture

```
User Browser
    ↓
https://stellar.apex/swagger-ui.html
    ↓
Traefik (forward-auth)
    ↓
Swagger Gateway :8888
    ↓
    ├─ Proxy → Antares Auth :8080/v3/api-docs
    └─ Proxy → Orion Training :8081/v3/api-docs
```

## URLs

- **Home**: `https://stellar.apex/` (redirects to Swagger UI)
- **Swagger UI**: `https://stellar.apex/swagger-ui.html`
- **OpenAPI Docs**:
  - Antares: `https://stellar.apex/v3/api-docs/antares`
  - Orion: `https://stellar.apex/v3/api-docs/orion`

## Configuration

### Environment Variables

```bash
ANTARES_JWT_SECRET=<your-jwt-secret>
```

### Application Properties

Key configurations in `application.properties`:

```properties
# Microservice URLs (internal Docker network)
swagger.services.antares.url=http://antares-auth:8080
swagger.services.orion.url=http://orion-training:8081

# Public gateway URL (used in OpenAPI spec rewriting)
swagger.gateway.public-url=https://stellar.apex
```

## How It Works

### 1. OpenAPI Proxying

The `SwaggerProxyController` fetches OpenAPI JSON from each microservice:

```java
@GetMapping("/v3/api-docs/antares")
public ResponseEntity<String> getAntaresApiDocs() {
    return fetchAndModifyApiDocs(
        "http://antares-auth:8080/v3/api-docs", 
        "/antares"
    );
}
```

### 2. Server URL Rewriting

The gateway modifies the `servers` field in the OpenAPI JSON to ensure all "Try it out" requests use the correct public URL:

```json
{
  "servers": [
    {
      "url": "https://stellar.apex/antares",
      "description": "StellarApex Gateway"
    }
  ]
}
```

### 3. Grouped APIs

Springdoc groups allow a clean dropdown in Swagger UI:

```java
@Bean
public GroupedOpenApi antaresAuthApi() {
    return GroupedOpenApi.builder()
        .group("1-antares-auth")
        .displayName("🔐 Antares Auth API")
        .pathsToMatch("/antares/**")
        .build();
}
```

## Adding a New Service

To add a new microservice to the gateway:

### 1. Update `application.properties`

```properties
swagger.services.newservice.url=http://new-service:8083

springdoc.swagger-ui.urls[2].name=🆕 New Service API
springdoc.swagger-ui.urls[2].url=/v3/api-docs/newservice
```

### 2. Add Proxy Method in Controller

```java
@GetMapping("/v3/api-docs/newservice")
public ResponseEntity<String> getNewServiceApiDocs() {
    return fetchAndModifyApiDocs(
        newServiceUrl + "/v3/api-docs", 
        "/newservice"
    );
}
```

### 3. Add Group Configuration

```java
@Bean
public GroupedOpenApi newServiceApi() {
    return GroupedOpenApi.builder()
        .group("3-new-service")
        .displayName("🆕 New Service API")
        .pathsToMatch("/newservice/**")
        .build();
}
```

## Security

- **Authentication**: JWT from Antares Auth (cookie or Bearer token)
- **Authorization**: Requires `ROLE_ADMIN` for all endpoints
- **CORS**: Configured for `https://stellar.apex` and `https://*.stellar.apex`

## Development

### Local Build

```bash
./mvnw clean package
```

### Docker Build

```bash
docker build -t swagger-gateway:latest .
```

### Run Locally

```bash
./mvnw spring-boot:run
```

Access at: `http://localhost:8888/swagger-ui.html`

## Troubleshooting

### "Service unavailable" errors

- Check that backend services are running
- Verify internal URLs in `application.properties`
- Check Docker network connectivity

### Authentication issues

- Ensure you're logged in as ADMIN in Antares
- Check that JWT secret matches across all services
- Verify cookie domain is correct

### Swagger UI not loading

- Check browser console for errors
- Verify Traefik routing is correct
- Ensure forward-auth middleware is applied

## Monitoring

Health check: `http://swagger-gateway:9093/actuator/health`

Logs:
```bash
docker logs swagger-gateway -f
```

## Future Enhancements

- [ ] Cache OpenAPI specs with TTL
- [ ] API versioning support
- [ ] Search across all APIs
- [ ] Export combined Postman collection
- [ ] Rate limiting per API group
