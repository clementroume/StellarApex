package apex.stellar.aldebaran.config;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for all Integration Tests.
 *
 * <p>It spins up specific Docker containers (PostgreSQL, Redis) via Testcontainers to ensure tests
 * run against real infrastructure logic rather than mocks.
 *
 * <p>Annotations explained:
 *
 * <ul>
 *   <li>{@code @SpringBootTest}: Loads the full Application Context.
 *   <li>{@code @AutoConfigureMockMvc}: Injects the MockMvc instance for API testing.
 *   <li>{@code @ActiveProfiles("test")}: Activates 'application-test.properties'.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

  // --- Infrastructure Setup (Docker) ---

  @ServiceConnection // Spring Boot 3.1+ magic: automatically configures spring.datasource.*
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @ServiceConnection(name = "redis") // Automatically configures spring.data.redis.*
  @SuppressWarnings("resource")
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  static {
    // Parallel startup for speed
    postgres.start();
    redis.start();
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected JsonMapper objectMapper;
  @Autowired protected StringRedisTemplate redisTemplate;

  // --- Configuration Injection ---

  @DynamicPropertySource
  static void registerCustomProperties(DynamicPropertyRegistry registry) {
    // JWT Configuration (Must match what SecurityConfig expects to avoid startup failure)
    // We use a dummy secret for tests, but it must be valid Base64 and long enough (256 bits).
    registry.add(
        "application.security.jwt.secret-key",
        () ->
            "YjQ1ZGRjYjU5YjYwNzZkMWY2MzE4YmFiY2Y4ZjgxMGE0YzY4ZmIwYmZkOTRkMjYxYmVjZGU1Y2Y3YWQyYjQzYw==");
    registry.add("application.security.jwt.expiration", () -> 3600000); // 1 hour
    registry.add("application.security.jwt.refresh-token.expiration", () -> 86400000); // 1 day
  }

  @AfterEach
  void cleanUpCache() {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }
}
