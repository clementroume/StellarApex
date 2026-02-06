package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
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
import apex.stellar.aldebaran.security.SecurityUtils;
import java.time.LocalDate;
import java.util.List; // Import List
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WodScoreServiceTest {

  private final Long userId = 123L;
  @Mock private WodScoreRepository scoreRepository;
  @Mock private WodRepository wodRepository;
  @Mock private WodScoreMapper scoreMapper;
  @InjectMocks private WodScoreService scoreService;

  private Wod wodTime;
  private WodScoreRequest requestTime;
  private WodScore scoreEntityTime;

  @BeforeEach
  void setUp() {
    wodTime = Wod.builder().id(1L).title("Fran").scoreType(ScoreType.TIME).build();

    // New Request DTO with split time (5 mins 0 seconds = 300s)
    requestTime =
        new WodScoreRequest(
            null, // userId (defaults to current)
            1L,
            LocalDate.now(),
            5,
            0, // Minutes, Seconds
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

    // Entity stores normalized timeSeconds
    scoreEntityTime =
        WodScore.builder()
            .id(50L)
            .wod(wodTime)
            .userId(userId)
            .timeSeconds(300)
            .scaling(ScalingLevel.RX)
            .personalRecord(false) // Default state before calc
            .build();
  }

  @Test
  @DisplayName("getMyScores: should return user scores filtered by WOD")
  void testGetMyScores_WithWodId() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
      Pageable pageable = PageRequest.of(0, 20);

      when(scoreRepository.findByUserIdAndWodId(eq(userId), eq(1L), any(Pageable.class)))
          .thenReturn(Page.empty());

      Page<WodScoreResponse> result = scoreService.getMyScores(1L, pageable);

      assertNotNull(result);
      verify(scoreRepository).findByUserIdAndWodId(eq(userId), eq(1L), any(Pageable.class));
    }
  }

  @Test
  @DisplayName("getMyScores: should return all user scores when no WOD filter")
  void testGetMyScores_NoFilter() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
      Pageable pageable = PageRequest.of(0, 20);

      when(scoreRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(Page.empty());

      Page<WodScoreResponse> result = scoreService.getMyScores(null, pageable);

      assertNotNull(result);
      verify(scoreRepository).findByUserId(eq(userId), any(Pageable.class));
    }
  }

  @Test
  @DisplayName("getLeaderboard: should return PRs for WOD")
  void testGetLeaderboard_Success() {
    Pageable pageable = PageRequest.of(0, 20);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
    when(scoreRepository.findByWodIdAndScalingAndPersonalRecordTrue(
            eq(1L), eq(ScalingLevel.RX), any(Pageable.class)))
        .thenReturn(Page.empty());

    Page<WodScoreResponse> result = scoreService.getLeaderboard(1L, ScalingLevel.RX, pageable);

    assertNotNull(result);
    verify(scoreRepository)
        .findByWodIdAndScalingAndPersonalRecordTrue(
            eq(1L), eq(ScalingLevel.RX), any(Pageable.class));
  }

  @Test
  @DisplayName("logScore: should save new PR when no previous history")
  void testLogScore_NewPr() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
      when(scoreMapper.toEntity(requestTime)).thenReturn(scoreEntityTime);
      when(scoreRepository.save(any(WodScore.class))).thenReturn(scoreEntityTime);
      when(scoreMapper.toResponse(scoreEntityTime)).thenReturn(mock(WodScoreResponse.class));

      when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(scoreEntityTime));

      // When
      scoreService.logScore(requestTime);

      // Then
      // On capture toutes les valeurs passées à save().
      ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
      // save est appelé 2 fois : 1 fois initialement (false), 1 fois lors de l'update PR (true)
      verify(scoreRepository, atLeastOnce()).save(captor.capture());

      // On vérifie que la DERNIÈRE valeur capturée (l'état final) est bien un PR
      assertTrue(captor.getValue().isPersonalRecord(), "The final saved state should be a PR");
    }
  }

  @Test
  @DisplayName("logScore: should verify PR calculation (Time: Lower is Better)")
  void testLogScore_TimeImprovement() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      // Old PR = 400 seconds (Normalized)
      WodScore oldPr =
          WodScore.builder()
              .id(40L)
              .wod(wodTime)
              .userId(userId)
              .timeSeconds(400)
              .personalRecord(true)
              .build();

      // New Score = 300 seconds (Better)
      scoreEntityTime.setTimeSeconds(300);

      when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
      when(scoreMapper.toEntity(requestTime)).thenReturn(scoreEntityTime);
      when(scoreRepository.save(any(WodScore.class))).thenReturn(scoreEntityTime);
      when(scoreMapper.toResponse(any())).thenReturn(mock(WodScoreResponse.class));

      // CORRECTION: Le mock doit retourner l'ancien PR ET le nouveau score (simulant la DB après le
      // 1er save)
      when(scoreRepository.findByWodIdAndUserId(1L, userId))
          .thenReturn(List.of(oldPr, scoreEntityTime));

      // When
      scoreService.logScore(requestTime);

      // Then
      // 1. Verify old PR is downgraded (saved with false)
      assertFalse(oldPr.isPersonalRecord(), "Old PR should be downgraded");
      verify(scoreRepository).save(oldPr);

      // 2. Verify new score is upgraded (saved with true)
      // Note: scoreEntityTime might be saved twice (initial + update), ensure final state is true
      assertTrue(scoreEntityTime.isPersonalRecord(), "New score should be upgraded to PR");
    }
  }

  @Test
  @DisplayName("logScore: should use explicit userId if provided (Coach/Admin mode)")
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
        WodScore.builder()
            .id(60L)
            .wod(wodTime)
            .userId(targetUser)
            .timeSeconds(300)
            .scaling(ScalingLevel.RX)
            .build();

    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
    when(scoreMapper.toEntity(requestForOther)).thenReturn(scoreForOther);
    when(scoreRepository.save(any())).thenReturn(scoreForOther);
    when(scoreMapper.toResponse(scoreForOther)).thenReturn(mock(WodScoreResponse.class));

    // CORRECTION: Mock needed for the PR calculation step too
    when(scoreRepository.findByWodIdAndUserId(1L, targetUser)).thenReturn(List.of(scoreForOther));

    scoreService.logScore(requestForOther);

    ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
    verify(scoreRepository, atLeastOnce()).save(captor.capture());
    assertEquals(targetUser, captor.getValue().getUserId());
  }

  @Test
  @DisplayName("logScore: should throw exception if WOD not found")
  void testLogScore_WodNotFound() {
    WodScoreRequest request =
        new WodScoreRequest(
            1L,
            99L,
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

    when(wodRepository.findById(99L)).thenReturn(Optional.empty());

    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> scoreService.logScore(request));
    assertEquals("error.wod.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("updateScore: should update fields and recalculate PR")
  void testUpdateScore_Success() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    when(scoreRepository.save(scoreEntityTime)).thenReturn(scoreEntityTime);
    when(scoreMapper.toResponse(scoreEntityTime)).thenReturn(mock(WodScoreResponse.class));

    // CORRECTION: Mock needed for recalculation
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(scoreEntityTime));

    scoreService.updateScore(50L, requestTime);

    verify(scoreMapper).updateEntity(requestTime, scoreEntityTime);
    verify(scoreRepository, atLeastOnce()).save(scoreEntityTime);
  }

  @Test
  @DisplayName("updateScore: should throw exception if Score not found")
  void testUpdateScore_NotFound() {
    when(scoreRepository.findById(99L)).thenReturn(Optional.empty());
    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class, () -> scoreService.updateScore(99L, requestTime));
    assertEquals("error.score.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("deleteScore: should delete if user is owner")
  void testDeleteScore_Success() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    // Mock recalculation after delete (list is empty effectively or contains remaining)
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of());

    scoreService.deleteScore(50L);
    verify(scoreRepository).delete(scoreEntityTime);
  }

  @Test
  @DisplayName("deleteScore: should throw exception if Score not found")
  void testDeleteScore_NotFound() {
    when(scoreRepository.findById(99L)).thenReturn(Optional.empty());
    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> scoreService.deleteScore(99L));
    assertEquals("error.score.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("compareScore: should calculate rank and percentile correctly")
  void testCompareScore() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    when(scoreRepository.countByWodIdAndScaling(1L, ScalingLevel.RX)).thenReturn(10L);
    when(scoreRepository.countBetterTime(1L, ScalingLevel.RX, 300)).thenReturn(2L);

    ScoreComparisonResponse response = scoreService.compareScore(50L);

    assertEquals(3L, response.rank());
    assertEquals(10L, response.totalScores());
    assertEquals(77.77, response.percentile(), 0.01);
  }

  @Test
  @DisplayName("logScore: Should correctly calculate PR for AMRAP (Higher is Better)")
  void testLogScore_AmrapImprovement() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      // WOD AMRAP (Rounds + Reps)
      Wod wodAmrap = Wod.builder().id(2L).scoreType(ScoreType.ROUNDS_REPS).build();

      // Old PR: 10 Rounds + 0 Reps
      WodScore oldPr =
          WodScore.builder()
              .id(60L)
              .wod(wodAmrap)
              .userId(userId)
              .rounds(10)
              .reps(0)
              .personalRecord(true)
              .build();

      // New Score: 10 Rounds + 5 Reps (Better!)
      // Note: We use the Entity directly for simplicity in setup
      WodScore newScore =
          WodScore.builder()
              .id(61L)
              .wod(wodAmrap)
              .userId(userId)
              .rounds(10)
              .reps(5)
              .personalRecord(false)
              .build();

      // Request matching the new score
      WodScoreRequest requestAmrap =
          new WodScoreRequest(
              null,
              2L,
              LocalDate.now(),
              null,
              null,
              10,
              5, // 10 rounds, 5 reps
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

      // Mocks
      when(wodRepository.findById(2L)).thenReturn(Optional.of(wodAmrap));
      when(scoreMapper.toEntity(requestAmrap)).thenReturn(newScore);
      when(scoreRepository.save(any(WodScore.class))).thenReturn(newScore);
      when(scoreMapper.toResponse(any())).thenReturn(mock(WodScoreResponse.class));

      // DB returns both for comparison logic
      when(scoreRepository.findByWodIdAndUserId(2L, userId)).thenReturn(List.of(oldPr, newScore));

      // When
      scoreService.logScore(requestAmrap);

      // Then
      // 1. Old PR lost status
      assertFalse(oldPr.isPersonalRecord(), "Old AMRAP PR should be downgraded");
      verify(scoreRepository).save(oldPr);

      // 2. New Score gained status
      // (We check the object passed to save, or the object state if modified in place)
      assertTrue(newScore.isPersonalRecord(), "New AMRAP score should be upgraded to PR");
    }
  }
}
