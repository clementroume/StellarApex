package apex.stellar.antares.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Sliding window of wrong enrollment codes, against a real Redis. */
class EnrollmentAttemptServiceIT extends BaseIntegrationTest {

  @Autowired private EnrollmentAttemptService service;

  private Instant now;

  @BeforeEach
  void fixClock() {
    now = Instant.parse("2026-10-28T10:00:00Z");
    service.clock = Clock.fixed(now, ZoneOffset.UTC);
  }

  @AfterEach
  void restoreClock() {
    service.clock = Clock.systemUTC();
  }

  private void at(Duration offset) {
    service.clock = Clock.fixed(now.plus(offset), ZoneOffset.UTC);
  }

  @Test
  @DisplayName("five wrong codes in 15 minutes block the user until the oldest one leaves the window")
  void slidingWindow() {
    for (int minute = 0; minute < 5; minute++) {
      at(Duration.ofMinutes(minute));
      service.checkAllowed(1L);
      service.recordFailure(1L);
    }

    at(Duration.ofMinutes(5));
    assertThatThrownBy(() -> service.checkAllowed(1L))
        .isInstanceOf(TooManyAttemptsException.class)
        .extracting(e -> ((TooManyAttemptsException) e).getRetryAfterSeconds())
        .isEqualTo(600L);
    // Another user is not affected
    service.checkAllowed(2L);

    // The first failure (minute 0) leaves the window at minute 15: one more try
    at(Duration.ofMinutes(15));
    service.checkAllowed(1L);
    service.recordFailure(1L);
    assertThat(service.retryAfterSeconds(1L)).isEqualTo(60L);
  }

  @Test
  @DisplayName("the counter key expires with the window")
  void keyExpires() {
    service.recordFailure(3L);
    Long ttl = redisTemplate.getExpire(EnrollmentAttemptService.KEY_PREFIX + 3);
    assertThat(ttl).isBetween(1L, 900L);
  }
}
