package apex.stellar.aldebaran.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import apex.stellar.aldebaran.repository.SessionBlockRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.service.SessionSettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests of the session read rules (frame, content per role and policy, logging day). */
class SessionSecurityTest {

  private static final long GYM = 100L;
  private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

  private final SessionSettingsService settingsService = mock(SessionSettingsService.class);
  private SessionSecurity security;

  @BeforeEach
  void setUp() {
    when(settingsService.zoneOf(GYM)).thenReturn(ZoneId.of("Europe/Paris"));
    security =
        new SessionSecurity(
            new SecurityService(),
            mock(SessionRepository.class),
            mock(SessionLogRepository.class),
            mock(SessionBlockRepository.class),
            settingsService,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Session session(VisibilityPolicy policy, Instant contentVisibleFrom) {
    Session session =
        Session.builder()
            .id(1L)
            .gymId(GYM)
            .authorId(9L)
            .visibilityPolicy(policy)
            .contentVisibleFrom(contentVisibleFrom)
            .build();
    session.schedule(SessionType.WOD, LocalDate.of(2026, 11, 3));
    return session;
  }

  private static AldebaranUserPrincipal member(String role) {
    return TestPrincipals.withMemberships(
        7L, Map.of(GYM, new GymAccess(role, Set.of())));
  }

  @Test
  @DisplayName("Content: open once contentVisibleFrom is reached, for every member")
  void opensAtInstant() {
    AldebaranUserPrincipal athlete = member("ATHLETE");

    assertFalse(
        security.isContentVisible(
            session(VisibilityPolicy.DAY_OF, NOW.plusSeconds(60)), athlete, false));
    assertTrue(security.isContentVisible(session(VisibilityPolicy.DAY_OF, NOW), athlete, false));
    assertFalse(
        security.isContentVisible(
            session(VisibilityPolicy.AFTER, NOW.plusSeconds(3600)), athlete, true),
        "AFTER: a participant waits like everyone");
  }

  @Test
  @DisplayName("Content: participant policies open to participants at once")
  void participants() {
    AldebaranUserPrincipal athlete = member("ATHLETE");

    assertTrue(
        security.isContentVisible(
            session(VisibilityPolicy.AFTER_PARTICIPANTS, NOW.plusSeconds(3600)), athlete, true));
    assertTrue(
        security.isContentVisible(session(VisibilityPolicy.PARTICIPANTS_ONLY, null), athlete, true));
    assertFalse(
        security.isContentVisible(
            session(VisibilityPolicy.PARTICIPANTS_ONLY, null), athlete, false));
  }

  @Test
  @DisplayName("Content: any staff role reads it; outsiders not even the frame")
  void staffAndOutsiders() {
    Session hidden = session(VisibilityPolicy.PARTICIPANTS_ONLY, null);

    assertTrue(security.isContentVisible(hidden, member("COACH"), false));
    assertTrue(security.isContentVisible(hidden, member("OWNER"), false));
    AldebaranUserPrincipal outsider = TestPrincipals.of(8L, null, "USER", List.of());
    assertFalse(security.canReadFrame(hidden, outsider));
    assertFalse(security.isContentVisible(session(VisibilityPolicy.ADVANCE, NOW), outsider, false));
  }

  @Test
  @DisplayName("Logging: from the session's first day in the gym's time zone")
  void logDay() {
    AldebaranUserPrincipal athlete = member("ATHLETE");
    Session today = session(VisibilityPolicy.AFTER, null);
    Session tomorrow = session(VisibilityPolicy.AFTER, null);
    tomorrow.schedule(SessionType.WOD, LocalDate.of(2026, 11, 4));

    assertTrue(security.canLogNow(today, athlete));
    assertFalse(security.canLogNow(tomorrow, athlete));
  }
}
