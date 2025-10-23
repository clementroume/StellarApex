package com.antares.api.config;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests, providing configuration for application context, Testcontainers
 * for PostgreSQL and Redis, and other commonly used test utilities.
 *
 * <p>This abstract class is intended to be extended by integration test classes. It sets up a test
 * environment where: - The Spring application context is loaded with a random web environment. -
 * Testcontainers are used to provide isolated PostgreSQL and Redis instances. - Default property
 * values are dynamically registered to simulate the application's runtime environment.
 *
 * <p>Class Annotations: - {@link SpringBootTest}: Configures the application context for
 * integration testing. - {@link AutoConfigureMockMvc}: Enables MockMvc for testing web layer
 * functionalities. - {@link ActiveProfiles}: Activates the "test" profile during tests.
 *
 * <p>Primary Responsibilities: - Provides a consistent, isolated environment for integration tests.
 * - Registers dynamic properties such as database connection details and application configuration
 * values via {@link DynamicPropertySource}.
 *
 * <p>Dynamic Property Registration: The method {@code registerDynamicProperties} is responsible for
 * setting dynamic configuration properties for: - PostgreSQL database connection (URL, username,
 * password). - Redis connection details (host and port). - Application-specific properties like
 * security settings and default admin credentials.
 *
 * <p>Example Usage: This class should be extended by integration test classes that require the
 * configured application context and containerized services. Extending classes can leverage the
 * injected properties, autowired beans, and utilities provided by the Spring test context and this
 * base class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest extends SingletonTestContainers {

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    // On référence les conteneurs de la classe parente
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add(
        "JWT_SECRET",
        () ->
            "YjQ1ZGRjYjU5YjYwNzZkMWY2MzE4YmFiY2Y4ZjgxMGE0YzY4ZmIwYmZkOTRkMjYxYmVjZGU1Y2Y3YWQyYjQzYw==");
    registry.add("application.admin.default-firstname", () -> "Test");
    registry.add("application.admin.default-lastname", () -> "Admin");
    registry.add("application.admin.default-email", () -> "admin.test@antares.com");
    registry.add("application.admin.default-password", () -> "testPassword123!");
    registry.add("application.security.jwt.issuer", () -> "antares-test-issuer");
    registry.add("application.security.jwt.audience", () -> "antares-test-audience");
  }
}
