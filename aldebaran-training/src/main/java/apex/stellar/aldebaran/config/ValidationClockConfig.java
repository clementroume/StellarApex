package apex.stellar.aldebaran.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clock of the Bean Validation time constraints ({@code @PastOrPresent} on score and journal
 * dates). Clients send the athlete's local day while the server runs in UTC: the clock is shifted
 * to the most advanced time zone (UTC+14), so "today" is accepted wherever the gym is and the next
 * day is still refused.
 */
@Configuration
public class ValidationClockConfig {

  /** Offset of the most advanced time zone (Line Islands). */
  static final Duration LATEST_ZONE_OFFSET = Duration.ofHours(14);

  /**
   * Gives the validator the application clock, shifted to UTC+14.
   *
   * @param clock The application clock (UTC).
   * @return The customizer of the validator configuration.
   */
  @Bean
  public ValidationConfigurationCustomizer validationClock(Clock clock) {
    return configuration -> configuration.clockProvider(() -> latestZoneClock(clock));
  }

  /**
   * The given clock moved to the most advanced time zone.
   *
   * @param clock A clock.
   * @return The same clock, 14 hours ahead.
   */
  static Clock latestZoneClock(Clock clock) {
    return Clock.offset(clock, LATEST_ZONE_OFFSET);
  }
}
