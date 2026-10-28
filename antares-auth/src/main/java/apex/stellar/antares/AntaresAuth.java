package apex.stellar.antares;

import apex.stellar.antares.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the Antares API Spring Boot application.
 *
 * <p>This class bootstraps the Spring context and starts the embedded server.
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
@EnableAsync
@SuppressWarnings("java:S1118")
public class AntaresAuth {

  /**
   * Starts the Antares API application.
   *
   * @param args command-line arguments passed to the application
   */
  static void main(String[] args) {
    SpringApplication.run(AntaresAuth.class, args);
  }
}
