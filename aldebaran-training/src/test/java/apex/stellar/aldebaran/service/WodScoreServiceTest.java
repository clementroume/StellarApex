package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.WodScoreMapper;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import apex.stellar.aldebaran.security.SecurityService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

@ExtendWith(MockitoExtension.class)
class WodScoreServiceTest {

  private final Long userId = 123L;
  @Mock private WodScoreRepository scoreRepository;
  @Mock private WodRepository wodRepository;
  @Mock private WodService wodService;
  @Mock private WodScoreMapper scoreMapper;
  @Mock private SecurityService securityService;
  @InjectMocks private WodScoreService scoreService;

  private Wod wodProxy;
  private WodScoreRequest requestTime;
  private WodScore scoreEntityTime;

  @BeforeEach
  void setUp() {
    wodProxy = Wod.builder().id(1L).build();

    requestTime =
        new WodScoreRequest(
            null,
            1L,
            LocalDate.now(),
            5,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            ScalingLevel.RX,
            false,
            null,
            null);

    scoreEntityTime =
        WodScore.builder()
            .id(50L)
            .wod(wodProxy)
            .userId(userId)
            .timeSeconds(300)
            .scaling(ScalingLevel.RX)
            .personalRecord(false)
            .build();
  }

  // =========================================================================
  // HELPER MOCK
  // =========================================================================

  private void mockWodDetailHit(ScoreType type) {
    WodResponse dto = mock(WodResponse.class);
    when(dto.scoreType()).thenReturn(type);
    when(wodService.getWodDetail(1L)).thenReturn(dto);
  }

  // =========================================================================
  // READ OPERATIONS
  // =========================================================================

  @Test
  @DisplayName("getMyScores: should return user scores filtered by WOD")
  void testGetMyScores_WithWodId() {
    when(securityService.getCurrentUserId()).thenReturn(userId);
    Pageable pageable = PageRequest.of(0, 20);
    when(scoreRepository.findByUserIdAndWodId(eq(userId), eq(1L), any(Pageable.class)))
        .thenReturn(Page.empty());

    Slice<WodScoreResponse> result = scoreService.getMyScores(1L, pageable);

    assertNotNull(result);
    verify(scoreRepository).findByUserIdAndWodId(eq(userId), eq(1L), any(Pageable.class));
  }

  @Test
  @DisplayName("getMyScores: should return all user scores when no WOD filter")
  void testGetMyScores_NoFilter() {
    when(securityService.getCurrentUserId()).thenReturn(userId);
    Pageable pageable = PageRequest.of(0, 20);
    when(scoreRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(Page.empty());

    Slice<WodScoreResponse> result = scoreService.getMyScores(null, pageable);

    assertNotNull(result);
    verify(scoreRepository).findByUserId(eq(userId), any(Pageable.class));
  }

  @Test
  @DisplayName("getLeaderboard: should return PRs for WOD")
  void testGetLeaderboard_Success() {
    Pageable pageable = PageRequest.of(0, 20);
    mockWodDetailHit(ScoreType.TIME);
    when(scoreRepository.findByWodIdAndScalingAndPersonalRecordTrue(
            eq(1L), eq(ScalingLevel.RX), any(Pageable.class)))
        .thenReturn(Page.empty());

    Slice<WodScoreResponse> result = scoreService.getLeaderboard(1L, ScalingLevel.RX, pageable);

    assertNotNull(result);
  }

  // =========================================================================
  // WRITE OPERATIONS (logScore, updateScore, deleteScore)
  // =========================================================================

  @Test
  @DisplayName("logScore: should save new score and trigger PR check")
  void testLogScore_Success() {
    when(securityService.getCurrentUserId()).thenReturn(userId);
    when(wodRepository.getReferenceById(1L)).thenReturn(wodProxy);
    when(scoreMapper.toEntity(requestTime)).thenReturn(scoreEntityTime);
    when(scoreRepository.save(scoreEntityTime)).thenReturn(scoreEntityTime);
    mockWodDetailHit(ScoreType.TIME);

    // Simule qu'il n'y a que ce score en base pour la réévaluation
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(scoreEntityTime));
    when(scoreMapper.toResponse(scoreEntityTime)).thenReturn(mock(WodScoreResponse.class));

    scoreService.logScore(requestTime);

    verify(scoreRepository, atLeastOnce()).save(scoreEntityTime);
    assertTrue(scoreEntityTime.isPersonalRecord(), "Only score should be PR");
  }

  @Test
  @DisplayName("logScore: should use explicit userId if provided")
  void testLogScore_ExplicitUser() {
    Long targetUser = 999L;
    WodScoreRequest requestForOther =
        new WodScoreRequest(
            targetUser,
            1L,
            LocalDate.now(),
            5,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            ScalingLevel.RX,
            false,
            null,
            null);
    WodScore scoreForOther =
        WodScore.builder().id(60L).wod(wodProxy).userId(targetUser).timeSeconds(300).build();

    when(wodRepository.getReferenceById(1L)).thenReturn(wodProxy);
    when(scoreMapper.toEntity(requestForOther)).thenReturn(scoreForOther);
    when(scoreRepository.save(any())).thenReturn(scoreForOther);
    mockWodDetailHit(ScoreType.TIME);
    when(scoreRepository.findByWodIdAndUserId(1L, targetUser)).thenReturn(List.of(scoreForOther));
    when(scoreMapper.toResponse(scoreForOther)).thenReturn(mock(WodScoreResponse.class));

    scoreService.logScore(requestForOther);

    ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
    verify(scoreRepository, atLeastOnce()).save(captor.capture());
    assertEquals(targetUser, captor.getValue().getUserId());
  }

  @Test
  @DisplayName("updateScore: should update fields and trigger PR check")
  void testUpdateScore_Success() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    when(scoreRepository.save(scoreEntityTime)).thenReturn(scoreEntityTime);
    mockWodDetailHit(ScoreType.TIME);
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(scoreEntityTime));
    when(scoreMapper.toResponse(scoreEntityTime)).thenReturn(mock(WodScoreResponse.class));

    scoreService.updateScore(50L, requestTime);

    verify(scoreMapper).updateEntity(requestTime, scoreEntityTime);
    verify(scoreRepository, atLeastOnce()).save(scoreEntityTime);
  }

  @Test
  @DisplayName("updateScore: should throw exception if Score not found")
  void testUpdateScore_NotFound() {
    when(scoreRepository.findById(99L)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> scoreService.updateScore(99L, requestTime));
  }

  @Test
  @DisplayName("deleteScore: should delete and flush before PR check")
  void testDeleteScore_Success() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    mockWodDetailHit(ScoreType.TIME);
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of());

    scoreService.deleteScore(50L);

    verify(scoreRepository).delete(scoreEntityTime);
    verify(scoreRepository).flush();
  }

  @Test
  @DisplayName("deleteScore: should throw exception if Score not found")
  void testDeleteScore_NotFound() {
    when(scoreRepository.findById(99L)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> scoreService.deleteScore(99L));
  }

  // =========================================================================
  // PR CALCULATION ENGINE (Tested via deleteScore to trigger recalculation)
  // =========================================================================

  @Test
  @DisplayName("PR Calculation: Time (Lower is Better)")
  void testPrCalculation_Time() {
    WodScore deletedScore = WodScore.builder().id(99L).wod(wodProxy).userId(userId).build();
    when(scoreRepository.findById(99L)).thenReturn(Optional.of(deletedScore));
    mockWodDetailHit(ScoreType.TIME);

    WodScore s1 = WodScore.builder().id(10L).timeSeconds(100).personalRecord(true).build();
    WodScore s2 =
        WodScore.builder().id(11L).timeSeconds(90).personalRecord(false).build(); // Better
    WodScore s3 = WodScore.builder().id(12L).timeSeconds(110).personalRecord(false).build();

    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(s1, s2, s3));

    scoreService.deleteScore(99L); // Triggers PR check

    assertFalse(s1.isPersonalRecord(), "Score 10 should lose PR status");
    assertTrue(s2.isPersonalRecord(), "Score 11 should gain PR status");
    assertFalse(s3.isPersonalRecord());
    verify(scoreRepository).saveAll(List.of(s1, s2, s3));
  }

  @Test
  @DisplayName("PR Calculation: Rounds+Reps (Higher is Better)")
  void testPrCalculation_RoundsReps() {
    WodScore deletedScore = WodScore.builder().id(99L).wod(wodProxy).userId(userId).build();
    when(scoreRepository.findById(99L)).thenReturn(Optional.of(deletedScore));
    mockWodDetailHit(ScoreType.ROUNDS_REPS);

    WodScore s1 = WodScore.builder().id(10L).rounds(5).reps(10).build();
    WodScore s2 = WodScore.builder().id(11L).rounds(5).reps(20).build(); // Better
    WodScore s3 = WodScore.builder().id(12L).rounds(4).reps(50).build();

    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(s1, s2, s3));

    scoreService.deleteScore(99L);

    assertTrue(s2.isPersonalRecord());
    assertFalse(s3.isPersonalRecord());
  }

  @Test
  @DisplayName("PR Calculation: Weight (Higher is Better)")
  void testPrCalculation_Weight() {
    WodScore deletedScore = WodScore.builder().id(99L).wod(wodProxy).userId(userId).build();
    when(scoreRepository.findById(99L)).thenReturn(Optional.of(deletedScore));
    mockWodDetailHit(ScoreType.WEIGHT);

    WodScore s1 = WodScore.builder().id(10L).maxWeightKg(100.0).build();
    WodScore s2 = WodScore.builder().id(11L).maxWeightKg(105.5).build(); // Better

    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(s1, s2));

    scoreService.deleteScore(99L);

    assertFalse(s1.isPersonalRecord());
    assertTrue(s2.isPersonalRecord());
  }

  @Test
  @DisplayName("PR Calculation: ScoreType NONE short-circuits")
  void testPrCalculation_ScoreTypeNone() {
    WodScore deletedScore = WodScore.builder().id(99L).wod(wodProxy).userId(userId).build();
    when(scoreRepository.findById(99L)).thenReturn(Optional.of(deletedScore));
    mockWodDetailHit(ScoreType.NONE);

    scoreService.deleteScore(99L);

    verify(scoreRepository, never()).findByWodIdAndUserId(any(), any());
  }

  @Test
  @DisplayName("PR Calculation: Empty list does nothing")
  void testPrCalculation_Empty() {
    WodScore deletedScore = WodScore.builder().id(99L).wod(wodProxy).userId(userId).build();
    when(scoreRepository.findById(99L)).thenReturn(Optional.of(deletedScore));
    mockWodDetailHit(ScoreType.TIME);

    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of());

    scoreService.deleteScore(99L);

    verify(scoreRepository, never()).saveAll(any());
  }

  // =========================================================================
  // ANALYTICS
  // =========================================================================

  @Test
  @DisplayName("compareScore: TIME should fetch DTO and calculate percentile")
  void testCompareScore_Time() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    mockWodDetailHit(ScoreType.TIME);

    when(scoreRepository.countByWodIdAndScaling(1L, ScalingLevel.RX)).thenReturn(10L);
    when(scoreRepository.countBetterTime(1L, ScalingLevel.RX, 300))
        .thenReturn(2L); // 2 better => Rank 3

    ScoreComparisonResponse response = scoreService.compareScore(50L);

    assertEquals(3L, response.rank());
    assertEquals(10L, response.totalScores());
    assertEquals(77.77, response.percentile(), 0.01);
  }

  @Test
  @DisplayName("compareScore: ROUNDS_REPS should call countBetterRoundsReps")
  void testCompareScore_RoundsReps() {
    WodScore score =
        WodScore.builder()
            .id(51L)
            .wod(wodProxy)
            .scaling(ScalingLevel.RX)
            .rounds(5)
            .reps(10)
            .build();

    when(scoreRepository.findById(51L)).thenReturn(Optional.of(score));
    mockWodDetailHit(ScoreType.ROUNDS_REPS);
    when(scoreRepository.countByWodIdAndScaling(1L, ScalingLevel.RX)).thenReturn(10L);
    when(scoreRepository.countBetterRoundsReps(1L, ScalingLevel.RX, 5, 10)).thenReturn(3L);

    ScoreComparisonResponse response = scoreService.compareScore(51L);

    assertEquals(4L, response.rank());
    verify(scoreRepository).countBetterRoundsReps(1L, ScalingLevel.RX, 5, 10);
  }

  @Test
  @DisplayName("compareScore: WEIGHT should call countBetterWeight")
  void testCompareScore_Weight() {
    WodScore score =
        WodScore.builder()
            .id(52L)
            .wod(wodProxy)
            .scaling(ScalingLevel.RX)
            .maxWeightKg(100.0)
            .build();

    when(scoreRepository.findById(52L)).thenReturn(Optional.of(score));
    mockWodDetailHit(ScoreType.WEIGHT);
    when(scoreRepository.countByWodIdAndScaling(1L, ScalingLevel.RX)).thenReturn(5L);
    when(scoreRepository.countBetterWeight(1L, ScalingLevel.RX, 100.0)).thenReturn(1L);

    ScoreComparisonResponse response = scoreService.compareScore(52L);

    assertEquals(2L, response.rank());
    verify(scoreRepository).countBetterWeight(1L, ScalingLevel.RX, 100.0);
  }

  @Test
  @DisplayName("compareScore: DISTANCE should call countBetterDistance")
  void testCompareScore_Distance() {
    WodScore score =
        WodScore.builder()
            .id(53L)
            .wod(wodProxy)
            .scaling(ScalingLevel.RX)
            .totalDistanceMeters(5000.0)
            .build();

    when(scoreRepository.findById(53L)).thenReturn(Optional.of(score));
    mockWodDetailHit(ScoreType.DISTANCE);
    when(scoreRepository.countByWodIdAndScaling(1L, ScalingLevel.RX)).thenReturn(20L);
    when(scoreRepository.countBetterDistance(1L, ScalingLevel.RX, 5000.0)).thenReturn(5L);

    ScoreComparisonResponse response = scoreService.compareScore(53L);

    assertEquals(6L, response.rank());
  }

  @Test
  @DisplayName("compareScore: NONE should return rank 1 (0 better)")
  void testCompareScore_None() {
    WodScore score = WodScore.builder().id(54L).wod(wodProxy).scaling(ScalingLevel.RX).build();

    when(scoreRepository.findById(54L)).thenReturn(Optional.of(score));
    mockWodDetailHit(ScoreType.NONE);
    when(scoreRepository.countByWodIdAndScaling(1L, ScalingLevel.RX)).thenReturn(10L);

    ScoreComparisonResponse response = scoreService.compareScore(54L);

    assertEquals(1L, response.rank());
    assertEquals(100.0, response.percentile());
  }

  @Test
  @DisplayName("compareScore: Single entry should handle division by zero safely")
  void testCompareScore_SingleEntry() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    mockWodDetailHit(ScoreType.TIME);

    when(scoreRepository.countByWodIdAndScaling(1L, ScalingLevel.RX))
        .thenReturn(1L); // Seulement l'utilisateur
    when(scoreRepository.countBetterTime(1L, ScalingLevel.RX, 300)).thenReturn(0L);

    ScoreComparisonResponse response = scoreService.compareScore(50L);

    assertEquals(1L, response.rank());
    assertEquals(1L, response.totalScores());
    assertEquals(100.0, response.percentile(), "Percentile for lone score should be 100%");
  }
}
