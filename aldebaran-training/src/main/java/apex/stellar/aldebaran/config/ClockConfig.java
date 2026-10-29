package apex.stellar.aldebaran.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Clock of the time-based rules (session visibility, journal dates), replaceable in tests. */
@Configuration
public class ClockConfig {

  /**
   * System clock in UTC: instants are stored as such, local days are placed with each gym's time
   * zone.
   *
   * @return The clock.
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
