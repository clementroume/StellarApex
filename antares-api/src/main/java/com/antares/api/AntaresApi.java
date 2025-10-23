package com.antares.api;

import com.antares.api.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main entry point for the Antares API Spring Boot application.
 *
 * <p>This class bootstraps the Spring context and starts the embedded server.
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class AntaresApi {

  /**
   * Starts the Antares API application.
   *
   * @param args command-line arguments passed to the application
   */
  public static void main(String[] args) {
    SpringApplication.run(AntaresApi.class, args);
  }
}
