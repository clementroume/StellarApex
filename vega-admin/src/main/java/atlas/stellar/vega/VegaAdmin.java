package atlas.stellar.vega;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The main class for the Antares Admin application.
 *
 * <p>This application serves as a spring-boot-admin server, enabling the monitoring and
 * administration of other Spring Boot applications through a centralized interface. It utilizes
 * Spring Boot's auto-configuration and Codecentric's Spring Boot Admin server functionality.
 */
@SpringBootApplication
@EnableAdminServer
public class VegaAdmin {

  /**
   * The main method serving as the entry point for the Antares Admin application.
   *
   * @param args command-line arguments passed to the application on startup
   */
  public static void main(String[] args) {
    SpringApplication.run(VegaAdmin.class, args);
  }
}
