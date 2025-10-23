package com.antares.api.config;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Singleton class to manage Testcontainers for PostgreSQL and Redis.
 *
 * <p>This class ensures that the containers are started only once per JVM instance, optimizing
 * resource usage during tests.
 */
@SuppressWarnings("resource")
public abstract class SingletonTestContainers {

  public static final PostgreSQLContainer<?> postgres;
  public static final GenericContainer<?> redis;

  static {
    postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    redis =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60));

    postgres.start();
    redis.start();
  }
}
