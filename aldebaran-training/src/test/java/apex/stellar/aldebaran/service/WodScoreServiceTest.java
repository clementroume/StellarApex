package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.config.SecurityUtils;
import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.mapper.WodScoreMapper;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.time.LocalDate;
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
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class WodScoreServiceTest {

  private final String userId = "user-123";
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
        WodScore.builder().id(50L).wod(wodTime).userId(userId).timeSeconds(300).scaling(ScalingLevel.RX).build();
  }

  @Test
  @DisplayName("logScore: should save new PR when no previous history")
  void testLogScore_NewPr() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
      when(scoreMapper.toEntity(requestTime)).thenReturn(scoreEntityTime);

      // Optimized Repository Query: returns Empty (No previous PR)
      when(scoreRepository.findByWodIdAndUserIdAndPersonalRecordTrue(1L, userId))
          .thenReturn(Optional.empty());

      when(scoreRepository.save(any(WodScore.class))).thenReturn(scoreEntityTime);
      when(scoreMapper.toResponse(scoreEntityTime)).thenReturn(mock(WodScoreResponse.class));

      // When
      scoreService.logScore(requestTime);

      // Then
      ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
      verify(scoreRepository).save(captor.capture());
      assertTrue(captor.getValue().isPersonalRecord(), "First score should be a PR");
    }
  }

  @Test
  @DisplayName("logScore: should verify PR calculation (Time: Lower is Better)")
  void testLogScore_TimeImprovement() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      // Old PR = 400 seconds (Normalized)
      WodScore oldPr =
          WodScore.builder().wod(wodTime).timeSeconds(400).personalRecord(true).build();

      // New Score = 300 seconds (Better)
      scoreEntityTime.setTimeSeconds(300);

      when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
      when(scoreMapper.toEntity(requestTime)).thenReturn(scoreEntityTime);

      // Found previous PR
      when(scoreRepository.findByWodIdAndUserIdAndPersonalRecordTrue(1L, userId))
          .thenReturn(Optional.of(oldPr));

      when(scoreRepository.save(any())).thenReturn(scoreEntityTime);

      // When
      scoreService.logScore(requestTime);

      // Then
      ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
      verify(scoreRepository).save(captor.capture());
      assertTrue(captor.getValue().isPersonalRecord(), "300s is faster than 400s -> Should be PR");
    }
  }

  @Test
  @DisplayName("deleteScore: should throw AccessDenied if user is not owner")
  void testDeleteScore_Unauthorized() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      // Mock Current User as "hacker"
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn("hacker");
      // Security Check fails
      utilities.when(() -> SecurityUtils.isCurrentUser(userId)).thenReturn(false);

      when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));

      assertThrows(AccessDeniedException.class, () -> scoreService.deleteScore(50L));
      verify(scoreRepository, never()).delete(any());
    }
  }

  @Test
  @DisplayName("deleteScore: should delete if user is owner")
  void testDeleteScore_Success() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
      utilities.when(() -> SecurityUtils.isCurrentUser(userId)).thenReturn(true);

      when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));

      scoreService.deleteScore(50L);
      verify(scoreRepository).delete(scoreEntityTime);
    }
  }

  @Test
  @DisplayName("compareScore: should calculate rank and percentile correctly")
  void testCompareScore() {
    // Given
    // Score ID 50, Time 300s.
    // Total scores = 10.
    // Better scores (faster) = 2.
    // Expected Rank = 3.
    // Expected Percentile = (10 - 3) / 9 * 100 = 7/9 * 100 = 77.77%

    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    when(scoreRepository.countByWodIdAndScaling(1L, ScalingLevel.RX)).thenReturn(10L);
    when(scoreRepository.countBetterTime(1L, ScalingLevel.RX, 300)).thenReturn(2L);

    // When
    ScoreComparisonResponse response = scoreService.compareScore(50L);

    // Then
    assertEquals(3L, response.rank());
    assertEquals(10L, response.totalScores());
    assertEquals(77.77, response.percentile(), 0.01);
  }
}
