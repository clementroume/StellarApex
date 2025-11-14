package com.antares.api.config;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base abstract class for all integration tests.
 *
 * <p>This class sets up the Spring Boot test environment, activates the "test" profile, and
 * inherits the singleton Testcontainers. It uses {@link DynamicPropertySource} to override
 * application properties at runtime with the dynamic ports and credentials from the containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc // Configures MockMvc for testing web controllers
@ActiveProfiles("test") // Activates the 'application-test.properties'
public abstract class BaseIntegrationTest extends SingletonTestContainers {

  /**
   * Dynamically registers properties from the Testcontainers into the Spring ApplicationContext
   * before it starts.
   *
   * @param registry The property registry to add properties to.
   */
  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    // Inject PostgreSQL properties
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);

    // Inject Redis properties
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

    // Inject dummy security and admin properties for the test environment
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
