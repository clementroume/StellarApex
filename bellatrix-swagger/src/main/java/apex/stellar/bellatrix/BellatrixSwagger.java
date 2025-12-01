package apex.stellar.bellatrix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Entry point for the Bellatrix Swagger microservice.
 *
 * <p>This service acts as a documentation gateway. It aggregates and exposes the OpenAPI (v3)
 * specifications of other internal microservices to make them accessible via a centralized Swagger
 * UI interface.
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@SuppressWarnings("java:S1118")
public class BellatrixSwagger {
  static void main(String[] args) {
    SpringApplication.run(BellatrixSwagger.class, args);
  }
}
