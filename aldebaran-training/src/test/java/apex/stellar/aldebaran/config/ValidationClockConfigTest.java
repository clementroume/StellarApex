package apex.stellar.aldebaran.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.PastOrPresent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidationClockConfigTest {

  record Dated(@PastOrPresent LocalDate date) {}

  private static Validator validatorAt(String instant) {
    Clock utc = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    var configuration = Validation.byDefaultProvider().configure();
    new ValidationClockConfig().validationClock(utc).customize(configuration);
    return configuration.buildValidatorFactory().getValidator();
  }

  @Test
  @DisplayName("A Paris athlete logging just after midnight local time keeps their day")
  void localTodayAccepted() {
    // 23:36 UTC on Oct 29 = 00:36 on Oct 30 in Paris
    Validator validator = validatorAt("2026-10-29T23:36:00Z");

    assertEquals(0, validator.validate(new Dated(LocalDate.parse("2026-10-30"))).size());
    assertEquals(0, validator.validate(new Dated(LocalDate.parse("2026-10-29"))).size());
  }

  @Test
  @DisplayName("A day after today in every time zone is still refused")
  void futureRefused() {
    Validator validator = validatorAt("2026-10-29T23:36:00Z");

    assertEquals(1, validator.validate(new Dated(LocalDate.parse("2026-10-31"))).size());
  }
}
