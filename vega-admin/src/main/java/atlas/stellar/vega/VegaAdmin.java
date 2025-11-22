package atlas.stellar.vega;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The main class for the Antares Admin application.
 *
 * <p>This application serves as a spring-boot-admin server, enabling the monitoring and
 * administration of other Spring Boot applications through a centralized interface.
 *
 * <p>The {@code @EnableAdminServer} annotation activates the auto-configuration of the user
 * interface and the API endpoints required to receive client registrations.
 */
@SpringBootApplication
@EnableAdminServer
public class VegaAdmin {

  private VegaAdmin() {}

  static void main(String[] args) {
    SpringApplication.run(VegaAdmin.class, args);
  }
}
