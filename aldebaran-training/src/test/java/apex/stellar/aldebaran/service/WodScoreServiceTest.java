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
import apex.stellar.aldebaran.security.SecurityService;
import java.time.LocalDate;
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
  @Mock private WodScoreMapper scoreMapper;
  @Mock private SecurityService securityService;
  @Mock private WodPrService wodPrService;
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
    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
    when(scoreRepository.findByWodIdAndScalingAndPersonalRecordTrue(
            eq(1L), eq(ScalingLevel.RX), any(Pageable.class)))
        .thenReturn(Page.empty());

    Slice<WodScoreResponse> result = scoreService.getLeaderboard(1L, ScalingLevel.RX, pageable);

    assertNotNull(result);
    verify(scoreRepository)
        .findByWodIdAndScalingAndPersonalRecordTrue(
            eq(1L), eq(ScalingLevel.RX), any(Pageable.class));
  }

  @Test
  @DisplayName("logScore: should save new PR when no previous history")
  void testLogScore_NewPr() {
    when(securityService.getCurrentUserId()).thenReturn(userId);

    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
    when(scoreMapper.toEntity(requestTime)).thenReturn(scoreEntityTime);
    when(scoreRepository.save(any(WodScore.class))).thenReturn(scoreEntityTime);
    when(scoreMapper.toResponse(scoreEntityTime)).thenReturn(mock(WodScoreResponse.class));

    when(wodPrService.updatePrStatus(wodTime, userId, 50L)).thenReturn(true);

    // When
    scoreService.logScore(requestTime);

    // Then
    // On capture toutes les valeurs passées à save().
    ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
    verify(scoreRepository).save(captor.capture());
    verify(wodPrService).updatePrStatus(wodTime, userId, 50L);

    assertTrue(captor.getValue().isPersonalRecord(), "The final saved state should be a PR");
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
    when(wodPrService.updatePrStatus(wodTime, targetUser, 60L)).thenReturn(true);

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

    when(wodPrService.updatePrStatus(any(), eq(userId), eq(50L))).thenReturn(true);

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

    scoreService.deleteScore(50L);
    verify(scoreRepository).delete(scoreEntityTime);
    verify(wodPrService).updatePrStatus(any(), eq(userId), isNull());
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
  @DisplayName("compareScore: ROUNDS_REPS should call countBetterRoundsReps")
  void testCompareScore_RoundsReps() {
    Wod wod = Wod.builder().id(2L).scoreType(ScoreType.ROUNDS_REPS).build();
    WodScore score =
        WodScore.builder().id(51L).wod(wod).scaling(ScalingLevel.RX).rounds(5).reps(10).build();

    when(scoreRepository.findById(51L)).thenReturn(Optional.of(score));
    when(scoreRepository.countByWodIdAndScaling(2L, ScalingLevel.RX)).thenReturn(10L);
    when(scoreRepository.countBetterRoundsReps(2L, ScalingLevel.RX, 5, 10)).thenReturn(3L);

    ScoreComparisonResponse response = scoreService.compareScore(51L);

    assertEquals(4L, response.rank()); // 3 better -> rank 4
    verify(scoreRepository).countBetterRoundsReps(2L, ScalingLevel.RX, 5, 10);
  }

  @Test
  @DisplayName("compareScore: WEIGHT should call countBetterWeight")
  void testCompareScore_Weight() {
    Wod wod = Wod.builder().id(3L).scoreType(ScoreType.WEIGHT).build();
    WodScore score =
        WodScore.builder().id(52L).wod(wod).scaling(ScalingLevel.RX).maxWeightKg(100.0).build();

    when(scoreRepository.findById(52L)).thenReturn(Optional.of(score));
    when(scoreRepository.countByWodIdAndScaling(3L, ScalingLevel.RX)).thenReturn(5L);
    when(scoreRepository.countBetterWeight(3L, ScalingLevel.RX, 100.0)).thenReturn(1L);

    ScoreComparisonResponse response = scoreService.compareScore(52L);

    assertEquals(2L, response.rank());
    verify(scoreRepository).countBetterWeight(3L, ScalingLevel.RX, 100.0);
  }

  @Test
  @DisplayName("compareScore: DISTANCE should call countBetterDistance")
  void testCompareScore_Distance() {
    Wod wod = Wod.builder().id(4L).scoreType(ScoreType.DISTANCE).build();
    WodScore score =
        WodScore.builder()
            .id(53L)
            .wod(wod)
            .scaling(ScalingLevel.RX)
            .totalDistanceMeters(5000.0)
            .build();

    when(scoreRepository.findById(53L)).thenReturn(Optional.of(score));
    when(scoreRepository.countByWodIdAndScaling(4L, ScalingLevel.RX)).thenReturn(20L);
    when(scoreRepository.countBetterDistance(4L, ScalingLevel.RX, 5000.0)).thenReturn(5L);

    ScoreComparisonResponse response = scoreService.compareScore(53L);

    assertEquals(6L, response.rank());
    verify(scoreRepository).countBetterDistance(4L, ScalingLevel.RX, 5000.0);
  }

  @Test
  @DisplayName("compareScore: NONE should return rank 1 (0 better)")
  void testCompareScore_None() {
    Wod wod = Wod.builder().id(5L).scoreType(ScoreType.NONE).build();
    WodScore score = WodScore.builder().id(54L).wod(wod).scaling(ScalingLevel.RX).build();

    when(scoreRepository.findById(54L)).thenReturn(Optional.of(score));
    when(scoreRepository.countByWodIdAndScaling(5L, ScalingLevel.RX)).thenReturn(10L);

    ScoreComparisonResponse response = scoreService.compareScore(54L);

    assertEquals(1L, response.rank()); // better is hardcoded to 0
    assertEquals(100.0, response.percentile()); // (10-1)/(10-1) * 100 = 100%
  }

  @Test
  @DisplayName("compareScore: Single entry should handle division by zero")
  void testCompareScore_SingleEntry() {
    Wod wod = Wod.builder().id(6L).scoreType(ScoreType.TIME).build();
    WodScore score =
        WodScore.builder().id(55L).wod(wod).scaling(ScalingLevel.RX).timeSeconds(60).build();

    when(scoreRepository.findById(55L)).thenReturn(Optional.of(score));
    when(scoreRepository.countByWodIdAndScaling(6L, ScalingLevel.RX)).thenReturn(1L); // Only me
    when(scoreRepository.countBetterTime(6L, ScalingLevel.RX, 60)).thenReturn(0L);

    ScoreComparisonResponse response = scoreService.compareScore(55L);

    assertEquals(1L, response.rank());
    assertEquals(1L, response.totalScores());
    assertEquals(100.0, response.percentile());
  }
}
