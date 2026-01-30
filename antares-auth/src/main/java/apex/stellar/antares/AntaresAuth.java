package apex.stellar.antares;

import apex.stellar.antares.config.JwtProperties;
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
