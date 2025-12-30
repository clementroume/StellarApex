package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.config.SecurityUtils;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.mapper.WodScoreMapper;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.util.List;
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

  private final String USER_ID = "user-123";
  @Mock private WodScoreRepository scoreRepository;
  @Mock private WodRepository wodRepository;
  @Mock private WodScoreMapper scoreMapper;
  @InjectMocks private WodScoreService scoreService;
  private Wod wodTime;
  private Wod wodReps;
  private WodScoreRequest request;
  private WodScore scoreEntity;

  @BeforeEach
  void setUp() {
    wodTime = Wod.builder()
        .id(1L)
        .title("Fran")
        .scoreType(Wod.ScoreType.TIME)
        .build();

    wodReps = Wod.builder()
        .id(2L)
        .title("Cindy")
        .scoreType(Wod.ScoreType.ROUNDS_REPS)
        .build();

    request = new WodScoreRequest(
        1L, java.time.LocalDate.now(),
        300, Unit.SECONDS, null, null, null, null, null, null, null, null,
        WodScore.ScalingLevel.RX, false, null, null
    );

    scoreEntity = WodScore.builder()
        .id(50L)
        .wod(wodTime)
        .userId(USER_ID)
        .timeSeconds(300)
        .build();
  }

  @Test
  @DisplayName("logScore: should save new PR when no previous history")
  void testLogScore_NewPr() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
      when(scoreMapper.toEntity(request)).thenReturn(scoreEntity);
      when(scoreRepository.findByUserIdAndPersonalRecordTrue(USER_ID)).thenReturn(List.of()); // No previous PR
      when(scoreRepository.save(any(WodScore.class))).thenReturn(scoreEntity);
      when(scoreMapper.toResponse(scoreEntity)).thenReturn(mock(WodScoreResponse.class));

      scoreService.logScore(request);

      ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
      verify(scoreRepository).save(captor.capture());
      assertTrue(captor.getValue().isPersonalRecord(), "First score should be a PR");
    }
  }

  @Test
  @DisplayName("logScore: should verify PR calculation (Time: Lower is Better)")
  void testLogScore_TimeImprovement() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      // Old PR = 400 seconds
      WodScore oldPr = WodScore.builder().wod(wodTime).timeSeconds(400).personalRecord(true).build();

      // New Score = 300 seconds (Better)
      scoreEntity.setTimeSeconds(300);

      when(wodRepository.findById(1L)).thenReturn(Optional.of(wodTime));
      when(scoreMapper.toEntity(request)).thenReturn(scoreEntity);
      when(scoreRepository.findByUserIdAndPersonalRecordTrue(USER_ID)).thenReturn(List.of(oldPr));
      when(scoreRepository.save(any())).thenReturn(scoreEntity);

      scoreService.logScore(request);

      ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
      verify(scoreRepository).save(captor.capture());
      assertTrue(captor.getValue().isPersonalRecord(), "300s is faster than 400s -> Should be PR");
    }
  }

  @Test
  @DisplayName("logScore: should verify PR calculation (Rounds: Higher is Better)")
  void testLogScore_RoundsImprovement() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      // Old PR = 10 Rounds
      WodScore oldPr = WodScore.builder().wod(wodReps).rounds(10).reps(0).personalRecord(true).build();

      // New Score = 11 Rounds
      WodScore newScore = WodScore.builder().wod(wodReps).rounds(11).reps(0).build();
      WodScoreRequest reqReps = new WodScoreRequest(2L, java.time.LocalDate.now(), null, null, 11, 0, null, null, null, null, null, null, WodScore.ScalingLevel.RX, false, null, null);

      when(wodRepository.findById(2L)).thenReturn(Optional.of(wodReps));
      when(scoreMapper.toEntity(reqReps)).thenReturn(newScore);
      when(scoreRepository.findByUserIdAndPersonalRecordTrue(USER_ID)).thenReturn(List.of(oldPr));
      when(scoreRepository.save(any())).thenReturn(newScore);

      scoreService.logScore(reqReps);

      ArgumentCaptor<WodScore> captor = ArgumentCaptor.forClass(WodScore.class);
      verify(scoreRepository).save(captor.capture());
      assertTrue(captor.getValue().isPersonalRecord(), "11 Rounds > 10 Rounds -> Should be PR");
    }
  }

  @Test
  @DisplayName("deleteScore: should throw AccessDenied if user is not owner")
  void testDeleteScore_Unauthorized() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      // Mock Current User as "hacker"
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn("hacker");
      // Mock SecurityUtils check to fail
      utilities.when(() -> SecurityUtils.isCurrentUser(USER_ID)).thenReturn(false);

      when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntity)); // Entity owned by USER_ID

      assertThrows(AccessDeniedException.class, () -> scoreService.deleteScore(50L));
      verify(scoreRepository, never()).delete(any());
    }
  }

  @Test
  @DisplayName("deleteScore: should delete if user is owner")
  void testDeleteScore_Success() {
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
      utilities.when(() -> SecurityUtils.isCurrentUser(USER_ID)).thenReturn(true);

      when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntity));

      scoreService.deleteScore(50L);
      verify(scoreRepository).delete(scoreEntity);
    }
  }
}