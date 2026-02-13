package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WodPrServiceTest {

  @Mock private WodScoreRepository scoreRepository;
  @InjectMocks private WodPrService wodPrService;

  private final Long userId = 1L;
  private final Long wodId = 100L;

  @Test
  @DisplayName("updatePrStatus: Time (Lower is Better)")
  void testUpdatePrStatus_Time() {
    Wod wod = Wod.builder().id(wodId).scoreType(ScoreType.TIME).build();

    WodScore s1 = WodScore.builder().id(10L).timeSeconds(100).personalRecord(true).build();
    WodScore s2 = WodScore.builder().id(11L).timeSeconds(90).personalRecord(false).build(); // Better
    WodScore s3 = WodScore.builder().id(12L).timeSeconds(110).personalRecord(false).build();

    when(scoreRepository.findByWodIdAndUserId(wodId, userId)).thenReturn(List.of(s1, s2, s3));

    // Check if s2 (ID 11) is the new PR
    boolean result = wodPrService.updatePrStatus(wod, userId, 11L);

    assertTrue(result, "Score 11 should be the new PR");
    assertFalse(s1.isPersonalRecord(), "Score 10 should lose PR status");
    assertTrue(s2.isPersonalRecord(), "Score 11 should gain PR status");
    verify(scoreRepository).saveAll(List.of(s1, s2, s3));
  }

  @Test
  @DisplayName("updatePrStatus: Rounds+Reps (Higher is Better)")
  void testUpdatePrStatus_RoundsReps() {
    Wod wod = Wod.builder().id(wodId).scoreType(ScoreType.ROUNDS_REPS).build();

    WodScore s1 = WodScore.builder().id(10L).rounds(5).reps(10).build();
    WodScore s2 = WodScore.builder().id(11L).rounds(5).reps(20).build(); // Better
    WodScore s3 = WodScore.builder().id(12L).rounds(4).reps(50).build();

    when(scoreRepository.findByWodIdAndUserId(wodId, userId)).thenReturn(List.of(s1, s2, s3));

    boolean result = wodPrService.updatePrStatus(wod, userId, 11L);

    assertTrue(result);
    assertTrue(s2.isPersonalRecord());
    verify(scoreRepository).saveAll(List.of(s1, s2, s3));
  }

  @Test
  @DisplayName("updatePrStatus: Weight (Higher is Better)")
  void testUpdatePrStatus_Weight() {
    Wod wod = Wod.builder().id(wodId).scoreType(ScoreType.WEIGHT).build();

    WodScore s1 = WodScore.builder().id(10L).maxWeightKg(100.0).build();
    WodScore s2 = WodScore.builder().id(11L).maxWeightKg(105.5).build(); // Better

    when(scoreRepository.findByWodIdAndUserId(wodId, userId)).thenReturn(List.of(s1, s2));

    boolean result = wodPrService.updatePrStatus(wod, userId, 11L);

    assertTrue(result);
    assertTrue(s2.isPersonalRecord());
  }

  @Test
  @DisplayName("updatePrStatus: Empty list returns false")
  void testUpdatePrStatus_Empty() {
    Wod wod = Wod.builder().id(wodId).scoreType(ScoreType.TIME).build();
    when(scoreRepository.findByWodIdAndUserId(wodId, userId)).thenReturn(List.of());

    boolean result = wodPrService.updatePrStatus(wod, userId, 1L);

    assertFalse(result);
  }

  @Test
  @DisplayName("updatePrStatus: Deletion scenario (currentScoreId is null)")
  void testUpdatePrStatus_Deletion() {
    Wod wod = Wod.builder().id(wodId).scoreType(ScoreType.TIME).build();

    // s1 was deleted, so only s2 remains in DB
    WodScore s2 = WodScore.builder().id(11L).timeSeconds(90).personalRecord(false).build();

    when(scoreRepository.findByWodIdAndUserId(wodId, userId)).thenReturn(List.of(s2));

    // We pass null because the "current" score was deleted
    boolean result = wodPrService.updatePrStatus(wod, userId, null);

    // Result is false because "null" (the deleted score) cannot be the PR
    assertFalse(result);

    // But s2 should have been promoted to PR
    assertTrue(s2.isPersonalRecord());
    verify(scoreRepository).saveAll(List.of(s2));
  }
}