package apex.stellar.aldebaran;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main entry point for the Orion Training microservice.
 *
 * <p>This service manages exercise catalogs, workout logging, performance tracking, and muscle
 * balance analysis for athletes and coaches.
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableJpaAuditing
@EnableCaching
public class AldebaranTraining {

  public static void main(String[] args) {
    SpringApplication.run(AldebaranTraining.class, args);
  }
}
