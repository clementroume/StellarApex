package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.ScoreSecurity;
import apex.stellar.aldebaran.security.SecurityService;
import apex.stellar.aldebaran.security.TestPrincipals;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ScoreCapabilityServiceTest {

  private final ScoreSecurity scoreSecurity =
      new ScoreSecurity(
          Mockito.mock(ScoreRepository.class),
          Mockito.mock(WodRepository.class),
          new SecurityService());
  private final ScoreCapabilityService service = new ScoreCapabilityService(scoreSecurity);

  private static ScoreResponse score(Long athleteId, Long gymId) {
    WodSummaryResponse wod =
        new WodSummaryResponse(1L, "Fran", null, null, null, null, null, null, null, 5L, gymId, true);
    return new ScoreResponse(
        10L, athleteId, LocalDate.now(), wod, null, 300, 5, 0, null, null, null, null, null, null, null,
        null, null, ScalingLevel.RX, false, null, true, "felt good", "band", null, null);
  }

  @Test
  @DisplayName("forViewer: the athlete can modify and sees their notes")
  void athlete() {
    AldebaranUserPrincipal athlete = TestPrincipals.of(2L, null, "USER", List.of());

    ScoreResponse result = service.forViewer(score(2L, 100L), athlete);

    assertTrue(result.capabilities().canModify());
    assertEquals("felt good", result.userComment());
    assertEquals("band", result.scalingNotes());
  }

  @Test
  @DisplayName("forViewer: SCORE_VERIFY staff of the WOD's gym can modify and see the notes")
  void staff() {
    AldebaranUserPrincipal coach =
        TestPrincipals.of(3L, 100L, "COACH", List.of("SCORE_VERIFY"));

    ScoreResponse result = service.forViewer(score(2L, 100L), coach);

    assertTrue(result.capabilities().canModify());
    assertEquals("felt good", result.userComment());
  }

  @Test
  @DisplayName("forViewer: another athlete cannot modify and does not see the notes")
  void otherAthlete() {
    AldebaranUserPrincipal other = TestPrincipals.of(4L, 100L, "ATHLETE", List.of());

    ScoreResponse result = service.forViewer(score(2L, 100L), other);

    assertFalse(result.capabilities().canModify());
    assertNull(result.userComment());
    assertNull(result.scalingNotes());
    assertEquals(300, result.timeSeconds());
  }
}
