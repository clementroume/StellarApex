package atlas.stellar.antares.config;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Manages Testcontainers for PostgreSQL and Redis as singletons.
 *
 * <p>This class ensures that containers are started only once for all integration tests,
 * significantly speeding up the test suite execution.
 */
@SuppressWarnings("resource")
public abstract class SingletonTestContainers {

  // PostgreSQL container instance
  public static final PostgreSQLContainer<?> postgres;
  // Redis container instance
  public static final GenericContainer<?> redis;

  static {
    // Initialize PostgreSQL container
    postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // Initialize Redis container
    redis =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort()) // Wait until Redis is ready
            .withStartupTimeout(Duration.ofSeconds(60));

    // Start containers. This block runs only once when the class is loaded.
    postgres.start();
    redis.start();
  }
}
