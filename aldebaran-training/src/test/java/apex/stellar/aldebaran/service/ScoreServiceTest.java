package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.exception.InvalidScoreException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.ScoreMapper;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.security.SecurityService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
class ScoreServiceTest {

  private final Long userId = 123L;
  @Mock private ScoreRepository scoreRepository;
  @Mock private WodRepository wodRepository;
  @Mock private WodService wodService;
  @Mock private ScoreMapper scoreMapper;
  @Mock private SecurityService securityService;
  @InjectMocks private ScoreService scoreService;

  private Wod wodProxy;
  private ScoreRequest requestTime;
  private Score scoreEntityTime;

  @BeforeEach
  void setUp() {
    wodProxy = Wod.builder().id(1L).scoreType(ScoreType.TIME).build();

    requestTime =
        new ScoreRequest(
            null,
            LocalDate.now(),
            1L,
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
            false, null,
            null,
            null, null);

    scoreEntityTime =
        Score.builder()
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

    Slice<ScoreResponse> result = scoreService.getMyScores(1L, pageable);

    assertNotNull(result);
    verify(scoreRepository).findByUserIdAndWodId(eq(userId), eq(1L), any(Pageable.class));
  }

  @Test
  @DisplayName("getMyScores: should return all user scores when no WOD filter")
  void testGetMyScores_NoFilter() {
    when(securityService.getCurrentUserId()).thenReturn(userId);
    Pageable pageable = PageRequest.of(0, 20);
    when(scoreRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(Page.empty());

    Slice<ScoreResponse> result = scoreService.getMyScores(null, pageable);

    assertNotNull(result);
    verify(scoreRepository).findByUserId(eq(userId), any(Pageable.class));
  }

  @Test
  @DisplayName("getLeaderboard: should return PRs for WOD")
  void testGetLeaderboard_Success() {
    Pageable pageable = PageRequest.of(0, 20);
    mockWodDetailHit(ScoreType.TIME);
    when(scoreRepository.findLeaderboard(
            eq(1L), eq(ScalingLevel.RX), eq(false), any(), eq(false), any(Pageable.class)))
        .thenReturn(Page.empty());

    Slice<ScoreResponse> result =
        scoreService.getLeaderboard(1L, ScalingLevel.RX, null, false, false, pageable);

    assertNotNull(result);
  }

  // =========================================================================
  // WRITE OPERATIONS (logScore, updateScore, deleteScore)
  // =========================================================================

  private ScoreRequest timeRequest(Long wodId, boolean capped) {
    return new ScoreRequest(
        null, LocalDate.now(), wodId, 5, 0, null, null, null, null, null, null, null, null,
        ScalingLevel.RX, capped, null, null, null, null);
  }

  private Score score(long id, ScalingLevel scaling) {
    return Score.builder().id(id).wod(wodProxy).userId(userId).scaling(scaling).build();
  }

  @Test
  @DisplayName("logScore: should save new score and trigger PR check")
  void testLogScore_Success() {
    when(securityService.getCurrentUserId()).thenReturn(userId);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodProxy));
    when(scoreMapper.toEntity(requestTime)).thenReturn(scoreEntityTime);
    when(scoreRepository.save(scoreEntityTime)).thenReturn(scoreEntityTime);
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(scoreEntityTime));
    when(scoreMapper.toResponse(scoreEntityTime)).thenReturn(mock(ScoreResponse.class));

    scoreService.logScore(requestTime);

    verify(scoreRepository, atLeastOnce()).save(scoreEntityTime);
    assertSame(wodProxy, scoreEntityTime.getWod());
    assertTrue(scoreEntityTime.isPersonalRecord(), "Only score should be PR");
  }

  @Test
  @DisplayName("logScore: unknown WOD -> 404, nothing saved")
  void testLogScore_WodNotFound() {
    when(wodRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> scoreService.logScore(requestTime));
    verify(scoreRepository, never()).save(any());
  }

  @Test
  @DisplayName("logScore: should use explicit userId if provided (admin)")
  void testLogScore_ExplicitUser() {
    Long targetUser = 999L;
    ScoreRequest requestForOther =
        new ScoreRequest(
            targetUser, LocalDate.now(), 1L, 5, 0, null, null, null, null, null, null, null, null,
            ScalingLevel.RX, false, null, null, null, null);
    Score scoreForOther =
        Score.builder().id(60L).userId(targetUser).timeSeconds(300).scaling(ScalingLevel.RX).build();

    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodProxy));
    when(scoreMapper.toEntity(requestForOther)).thenReturn(scoreForOther);
    when(scoreRepository.save(any())).thenReturn(scoreForOther);
    when(scoreRepository.findByWodIdAndUserId(1L, targetUser)).thenReturn(List.of(scoreForOther));
    when(scoreMapper.toResponse(scoreForOther)).thenReturn(mock(ScoreResponse.class));

    scoreService.logScore(requestForOther);

    ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
    verify(scoreRepository, atLeastOnce()).save(captor.capture());
    assertEquals(targetUser, captor.getValue().getUserId());
  }

  @Test
  @DisplayName("logScore: time-capped TIME score keeps the reps and drops the time")
  void testLogScore_TimeCapped() {
    ScoreRequest request = timeRequest(1L, true);
    Score capped =
        Score.builder().timeSeconds(300).reps(45).timeCapped(true).scaling(ScalingLevel.RX).build();
    when(securityService.getCurrentUserId()).thenReturn(userId);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodProxy));
    when(scoreMapper.toEntity(request)).thenReturn(capped);
    when(scoreRepository.save(capped)).thenReturn(capped);
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(capped));

    scoreService.logScore(request);

    assertNull(capped.getTimeSeconds());
    assertTrue(capped.getTimeCapped());
    assertEquals(45, capped.getReps());
  }

  @Test
  @DisplayName("logScore: time-capped score without reps -> 400")
  void testLogScore_TimeCappedWithoutReps() {
    ScoreRequest request = timeRequest(1L, true);
    Score capped = Score.builder().timeSeconds(300).timeCapped(true).scaling(ScalingLevel.RX).build();
    when(securityService.getCurrentUserId()).thenReturn(userId);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodProxy));
    when(scoreMapper.toEntity(request)).thenReturn(capped);

    InvalidScoreException ex =
        assertThrows(InvalidScoreException.class, () -> scoreService.logScore(request));
    assertEquals("error.score.capped.reps.required", ex.getMessageKey());
    verify(scoreRepository, never()).save(any());
  }

  @Test
  @DisplayName("logScore: missing value for the WOD's score type -> 400")
  void testLogScore_MissingValue() {
    Wod weightWod = Wod.builder().id(1L).scoreType(ScoreType.WEIGHT).build();
    Score empty = Score.builder().reps(5).timeCapped(true).scaling(ScalingLevel.RX).build();
    when(securityService.getCurrentUserId()).thenReturn(userId);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(weightWod));
    when(scoreMapper.toEntity(requestTime)).thenReturn(empty);

    InvalidScoreException ex =
        assertThrows(InvalidScoreException.class, () -> scoreService.logScore(requestTime));
    assertEquals("error.score.value.required.WEIGHT", ex.getMessageKey());
    assertFalse(empty.getTimeCapped(), "Only TIME scores can be time-capped");
  }

  @Test
  @DisplayName("updateScore: should update fields and trigger PR check")
  void testUpdateScore_Success() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    when(scoreRepository.save(scoreEntityTime)).thenReturn(scoreEntityTime);
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(scoreEntityTime));
    when(scoreMapper.toResponse(scoreEntityTime)).thenReturn(mock(ScoreResponse.class));

    scoreService.updateScore(50L, requestTime);

    verify(scoreMapper).updateEntity(requestTime, scoreEntityTime);
    verify(scoreRepository, atLeastOnce()).save(scoreEntityTime);
  }

  @Test
  @DisplayName("updateScore: request pointing at another WOD -> 400, nothing changed")
  void testUpdateScore_WodMismatch() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));

    InvalidScoreException ex =
        assertThrows(
            InvalidScoreException.class, () -> scoreService.updateScore(50L, timeRequest(2L, false)));
    assertEquals("error.score.wod.mismatch", ex.getMessageKey());
    verify(scoreMapper, never()).updateEntity(any(), any());
    verify(scoreRepository, never()).findByWodIdAndUserId(any(), any());
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

  private void deleteTriggeringPr(ScoreType type, List<Score> remaining) {
    wodProxy.setScoreType(type);
    when(scoreRepository.findById(99L)).thenReturn(Optional.of(score(99L, ScalingLevel.RX)));
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(remaining);
    scoreService.deleteScore(99L);
  }

  @Test
  @DisplayName("PR Calculation: Time (Lower is Better)")
  void testPrCalculation_Time() {
    Score s1 = score(10L, ScalingLevel.RX);
    s1.setTimeSeconds(100);
    s1.setPersonalRecord(true);
    Score s2 = score(11L, ScalingLevel.RX);
    s2.setTimeSeconds(90); // Better
    Score s3 = score(12L, ScalingLevel.RX);
    s3.setTimeSeconds(110);

    deleteTriggeringPr(ScoreType.TIME, List.of(s1, s2, s3));

    assertFalse(s1.isPersonalRecord(), "Score 10 should lose PR status");
    assertTrue(s2.isPersonalRecord(), "Score 11 should gain PR status");
    assertFalse(s3.isPersonalRecord());
    verify(scoreRepository).saveAll(List.of(s1, s2, s3));
  }

  @Test
  @DisplayName("PR Calculation: a finished time beats a time-capped score")
  void testPrCalculation_TimeCapped() {
    Score capped = score(10L, ScalingLevel.RX);
    capped.setTimeCapped(true);
    capped.setReps(200);
    Score slow = score(11L, ScalingLevel.RX);
    slow.setTimeSeconds(1199);

    deleteTriggeringPr(ScoreType.TIME, List.of(capped, slow));

    assertTrue(slow.isPersonalRecord());
    assertFalse(capped.isPersonalRecord());
  }

  @Test
  @DisplayName("PR Calculation: time-capped scores rank by reps at the cap")
  void testPrCalculation_TimeCappedReps() {
    Score fewer = score(10L, ScalingLevel.RX);
    fewer.setTimeCapped(true);
    fewer.setReps(40);
    Score more = score(11L, ScalingLevel.RX);
    more.setTimeCapped(true);
    more.setReps(45);

    deleteTriggeringPr(ScoreType.TIME, List.of(fewer, more));

    assertTrue(more.isPersonalRecord());
    assertFalse(fewer.isPersonalRecord());
  }

  @Test
  @DisplayName("PR Calculation: any Rx score beats a better scaled one, Rx+ beats Rx")
  void testPrCalculation_ScalingTier() {
    Score scaled = score(10L, ScalingLevel.SCALED);
    scaled.setTimeSeconds(200);
    Score rx = score(11L, ScalingLevel.RX);
    rx.setTimeSeconds(400);

    deleteTriggeringPr(ScoreType.TIME, List.of(scaled, rx));
    assertTrue(rx.isPersonalRecord());
    assertFalse(scaled.isPersonalRecord());

    Score elite = score(12L, ScalingLevel.ELITE);
    elite.setTimeSeconds(500);
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(scaled, rx, elite));
    scoreService.deleteScore(99L);
    assertTrue(elite.isPersonalRecord());
    assertFalse(rx.isPersonalRecord());
  }

  @Test
  @DisplayName("PR Calculation: total reps (Higher is Better)")
  void testPrCalculation_TotalReps() {
    Score s1 = score(10L, ScalingLevel.RX);
    s1.setReps(160);
    Score s2 = score(11L, ScalingLevel.RX);
    s2.setReps(170); // Better
    Score s3 = score(12L, ScalingLevel.RX);
    s3.setReps(150);

    deleteTriggeringPr(ScoreType.REPS, List.of(s1, s2, s3));

    assertTrue(s2.isPersonalRecord());
    assertFalse(s3.isPersonalRecord());
  }

  @Test
  @DisplayName("PR Calculation: Weight (Higher is Better)")
  void testPrCalculation_Weight() {
    Score s1 = score(10L, ScalingLevel.RX);
    s1.setMaxWeightKg(100.0);
    Score s2 = score(11L, ScalingLevel.RX);
    s2.setMaxWeightKg(105.5); // Better

    deleteTriggeringPr(ScoreType.WEIGHT, List.of(s1, s2));

    assertFalse(s1.isPersonalRecord());
    assertTrue(s2.isPersonalRecord());
  }

  @Test
  @DisplayName("PR Calculation: ScoreType NONE short-circuits")
  void testPrCalculation_ScoreTypeNone() {
    wodProxy.setScoreType(ScoreType.NONE);
    when(scoreRepository.findById(99L)).thenReturn(Optional.of(score(99L, ScalingLevel.RX)));

    scoreService.deleteScore(99L);

    verify(scoreRepository, never()).findByWodIdAndUserId(any(), any());
  }

  @Test
  @DisplayName("PR Calculation: Empty list does nothing")
  void testPrCalculation_Empty() {
    deleteTriggeringPr(ScoreType.TIME, List.of());

    verify(scoreRepository, never()).saveAll(any());
  }

  // =========================================================================
  // ANALYTICS
  // =========================================================================

  private Score otherPr(long id, long athleteId) {
    Score s = Score.builder().id(id).wod(wodProxy).userId(athleteId).scaling(ScalingLevel.RX).build();
    s.setPersonalRecord(true);
    return s;
  }

  @Test
  @DisplayName("compareScore: ranks among other athletes' PRs (TIME, capped last)")
  void testCompareScore_Time() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime)); // 300 s
    Score faster = otherPr(1L, 1L);
    faster.setTimeSeconds(250);
    Score slower = otherPr(2L, 2L);
    slower.setTimeSeconds(350);
    Score capped = otherPr(3L, 3L);
    capped.setTimeCapped(true);
    capped.setReps(500);
    Score mine = otherPr(4L, userId); // the athlete's own PR is not a rival
    mine.setTimeSeconds(100);
    when(scoreRepository.findByWodIdAndScalingAndPersonalRecordTrue(1L, ScalingLevel.RX))
        .thenReturn(List.of(faster, slower, capped, mine));

    ScoreComparisonResponse response = scoreService.compareScore(50L);

    assertEquals(2L, response.rank());
    assertEquals(4L, response.totalScores());
    assertEquals(66.66, response.percentile(), 0.01);
  }

  @Test
  @DisplayName("compareScore: REPS compares total reps")
  void testCompareScore_Reps() {
    wodProxy.setScoreType(ScoreType.REPS);
    Score score = score(51L, ScalingLevel.RX);
    score.setReps(160);
    Score better = otherPr(1L, 1L);
    better.setReps(161);
    Score worse = otherPr(2L, 2L);
    worse.setReps(150);
    when(scoreRepository.findById(51L)).thenReturn(Optional.of(score));
    when(scoreRepository.findByWodIdAndScalingAndPersonalRecordTrue(1L, ScalingLevel.RX))
        .thenReturn(List.of(better, worse));

    assertEquals(2L, scoreService.compareScore(51L).rank());
  }

  @Test
  @DisplayName("compareScore: Single entry should handle division by zero safely")
  void testCompareScore_SingleEntry() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    when(scoreRepository.findByWodIdAndScalingAndPersonalRecordTrue(1L, ScalingLevel.RX))
        .thenReturn(List.of());

    ScoreComparisonResponse response = scoreService.compareScore(50L);

    assertEquals(1L, response.rank());
    assertEquals(1L, response.totalScores());
    assertEquals(100.0, response.percentile(), "Percentile for lone score should be 100%");
  }

  // =========================================================================
  // ROUNDS (EMOM) AND TIEBREAK
  // =========================================================================

  private ScoreRequest anyRequest(Long wodId) {
    return new ScoreRequest(
        null, LocalDate.now(), wodId, null, null, null, null, null, null, null, null, null, null,
        ScalingLevel.RX, false, null, null, null, null);
  }

  private Score saveThroughLog(Wod wod, Score entity) {
    ScoreRequest request = anyRequest(wod.getId());
    when(securityService.getCurrentUserId()).thenReturn(userId);
    when(wodRepository.findById(wod.getId())).thenReturn(Optional.of(wod));
    when(scoreMapper.toEntity(request)).thenReturn(entity);
    lenient().when(scoreRepository.save(entity)).thenReturn(entity);
    lenient().when(scoreRepository.findByWodIdAndUserId(wod.getId(), userId)).thenReturn(List.of(entity));
    scoreService.logScore(request);
    return entity;
  }

  @Test
  @DisplayName("ROUNDS: rounds on the minute required, at most the EMOM rounds")
  void testRounds_Bounds() {
    Wod chelsea = Wod.builder().id(3L).scoreType(ScoreType.ROUNDS).emomRounds(30).build();

    Score ok = saveThroughLog(chelsea, Score.builder().rounds(24).scaling(ScalingLevel.RX).build());
    assertTrue(ok.isPersonalRecord());

    Score tooMany = Score.builder().rounds(31).scaling(ScalingLevel.RX).build();
    InvalidScoreException ex =
        assertThrows(InvalidScoreException.class, () -> saveThroughLog(chelsea, tooMany));
    assertEquals("error.score.rounds.max", ex.getMessageKey());

    Score missing = Score.builder().reps(10).scaling(ScalingLevel.RX).build();
    ex = assertThrows(InvalidScoreException.class, () -> saveThroughLog(chelsea, missing));
    assertEquals("error.score.value.required.ROUNDS", ex.getMessageKey());
  }

  @Test
  @DisplayName("Tiebreak: dropped when the WOD defines none, or when a TIME WOD is finished")
  void testTiebreak_Dropped() {
    Wod noTiebreak = Wod.builder().id(4L).scoreType(ScoreType.REPS).build();
    Score reps = saveThroughLog(
        noTiebreak, Score.builder().reps(120).tiebreakSeconds(300).scaling(ScalingLevel.RX).build());
    assertNull(reps.getTiebreakSeconds());

    Wod open = Wod.builder().id(5L).scoreType(ScoreType.TIME).tiebreakEn("Last full set").build();
    Score finished = saveThroughLog(
        open, Score.builder().timeSeconds(500).tiebreakSeconds(300).scaling(ScalingLevel.RX).build());
    assertNull(finished.getTiebreakSeconds(), "A finished WOD has no tiebreak");
  }

  @Test
  @DisplayName("Tiebreak: kept for a time-capped score and for an AMRAP of a WOD that has one")
  void testTiebreak_Kept() {
    Wod open = Wod.builder().id(5L).scoreType(ScoreType.TIME).tiebreakFr("Dernière série").build();
    Score capped = saveThroughLog(
        open,
        Score.builder().timeCapped(true).reps(150).tiebreakSeconds(640).scaling(ScalingLevel.RX).build());
    assertEquals(640, capped.getTiebreakSeconds());
  }

  @Test
  @DisplayName("compareScore: equal reps ranked by tiebreak")
  void testCompareScore_Tiebreak() {
    Wod amrap = Wod.builder().id(6L).scoreType(ScoreType.REPS).tiebreakEn("Last set").build();
    Score mine = Score.builder().id(70L).wod(amrap).userId(userId).scaling(ScalingLevel.RX)
        .reps(200).tiebreakSeconds(600).build();
    Score fasterTb = otherPr(71L, 1L);
    fasterTb.setWod(amrap);
    fasterTb.setReps(200);
    fasterTb.setTiebreakSeconds(590);
    Score noTb = otherPr(72L, 2L);
    noTb.setWod(amrap);
    noTb.setReps(200);
    when(scoreRepository.findById(70L)).thenReturn(Optional.of(mine));
    when(scoreRepository.findByWodIdAndScalingAndPersonalRecordTrue(6L, ScalingLevel.RX))
        .thenReturn(List.of(fasterTb, noTb));

    assertEquals(2L, scoreService.compareScore(70L).rank());
  }

  @Test
  @DisplayName("REPS: rounds + reps entered on a fixed-round WOD are stored as total reps")
  void testReps_RoundsToTotal() {
    Wod cindy = Wod.builder().id(8L).scoreType(ScoreType.REPS).repsPerRound(30).build();
    Score entered = Score.builder().rounds(10).reps(12).scaling(ScalingLevel.RX).build();

    saveThroughLog(cindy, entered);

    assertEquals(312, entered.getReps());
    assertNull(entered.getRounds());
  }

  @Test
  @DisplayName("REPS: rounds on a WOD without a fixed round -> 400, total reps accepted")
  void testReps_RoundsWithoutRepsPerRound() {
    Wod ladder = Wod.builder().id(9L).scoreType(ScoreType.REPS).build();

    InvalidScoreException ex =
        assertThrows(
            InvalidScoreException.class,
            () -> saveThroughLog(ladder, Score.builder().rounds(8).scaling(ScalingLevel.RX).build()));
    assertEquals("error.score.rounds.unknown", ex.getMessageKey());

    Score total = saveThroughLog(ladder, Score.builder().reps(150).scaling(ScalingLevel.RX).build());
    assertEquals(150, total.getReps());
  }

  @Test
  @DisplayName("Gym: a gym WOD score takes the WOD's gym, a public WOD score the chosen gym")
  void testGymOfScore() {
    Wod gymWod = Wod.builder().id(20L).scoreType(ScoreType.TIME).gymId(100L).build();
    ScoreRequest otherGym =
        new ScoreRequest(
            null, LocalDate.now(), 20L, 5, 0, null, null, null, null, null, null, null, null,
            ScalingLevel.RX, false, null, null, null, 300L);
    Score entity = Score.builder().timeSeconds(300).scaling(ScalingLevel.RX).build();
    when(securityService.getCurrentUserId()).thenReturn(userId);
    when(wodRepository.findById(20L)).thenReturn(Optional.of(gymWod));
    when(scoreMapper.toEntity(otherGym)).thenReturn(entity);
    when(scoreRepository.save(entity)).thenReturn(entity);
    when(scoreRepository.findByWodIdAndUserId(20L, userId)).thenReturn(List.of(entity));

    scoreService.logScore(otherGym);
    assertEquals(100L, entity.getGymId(), "A gym WOD is always scored in its gym");

    Wod open = Wod.builder().id(21L).scoreType(ScoreType.TIME).isPublic(true).build();
    ScoreRequest inMyBox =
        new ScoreRequest(
            null, LocalDate.now(), 21L, 5, 0, null, null, null, null, null, null, null, null,
            ScalingLevel.RX, false, null, null, null, 300L);
    Score openScore = Score.builder().timeSeconds(300).scaling(ScalingLevel.RX).build();
    when(wodRepository.findById(21L)).thenReturn(Optional.of(open));
    when(scoreMapper.toEntity(inMyBox)).thenReturn(openScore);
    when(scoreRepository.save(openScore)).thenReturn(openScore);
    when(scoreRepository.findByWodIdAndUserId(21L, userId)).thenReturn(List.of(openScore));

    scoreService.logScore(inMyBox);
    assertEquals(300L, openScore.getGymId());
  }

  @Test
  @DisplayName("getLeaderboard: gym and verified filters are passed to the query")
  void testGetLeaderboard_Gym() {
    mockWodDetailHit(ScoreType.TIME);
    when(scoreRepository.findLeaderboard(
            eq(1L), eq(ScalingLevel.RX), eq(true), eq(Set.of(300L)), eq(true), any(Pageable.class)))
        .thenReturn(Page.empty());

    scoreService.getLeaderboard(1L, ScalingLevel.RX, 300L, false, true, PageRequest.of(0, 20));

    verify(scoreRepository)
        .findLeaderboard(
            eq(1L), eq(ScalingLevel.RX), eq(true), eq(Set.of(300L)), eq(true), any(Pageable.class));
  }

  @Test
  @DisplayName("getLeaderboard: myGyms keeps the scores performed in any of the user's gyms")
  void testGetLeaderboard_MyGyms() {
    mockWodDetailHit(ScoreType.TIME);
    when(securityService.getCurrentUserMemberGymIds()).thenReturn(Set.of(100L, 200L));
    when(scoreRepository.findLeaderboard(
            eq(1L), eq(ScalingLevel.RX), eq(true), eq(Set.of(100L, 200L)), eq(false),
            any(Pageable.class)))
        .thenReturn(Page.empty());

    scoreService.getLeaderboard(1L, ScalingLevel.RX, null, true, false, PageRequest.of(0, 20));

    verify(scoreRepository)
        .findLeaderboard(
            eq(1L), eq(ScalingLevel.RX), eq(true), eq(Set.of(100L, 200L)), eq(false),
            any(Pageable.class));
  }

  // =========================================================================
  // COMPETITION WODs (staff logging, verification)
  // =========================================================================

  @Test
  @DisplayName("logScore: a score logged by staff for an athlete on a competition WOD is verified")
  void testLogScore_CompetitionStaffForAthlete_Verified() {
    Long coachId = 2L;
    Long athleteId = 99L;
    Wod competition =
        Wod.builder().id(1L).gymId(100L).competition(true).scoreType(ScoreType.TIME).build();
    ScoreRequest request =
        new ScoreRequest(
            athleteId, LocalDate.now(), 1L, 5, 0, null, null, null, null, null, null, null, null,
            ScalingLevel.RX, false, null, null, null, null);
    Score entity = Score.builder().timeSeconds(300).scaling(ScalingLevel.RX).build();
    when(securityService.getCurrentUserId()).thenReturn(coachId);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(competition));
    when(scoreMapper.toEntity(request)).thenReturn(entity);
    when(scoreRepository.save(entity)).thenReturn(entity);
    when(scoreRepository.findByWodIdAndUserId(1L, athleteId)).thenReturn(List.of(entity));

    scoreService.logScore(request);

    assertEquals(athleteId, entity.getUserId());
    assertEquals(coachId, entity.getLoggedBy());
    assertEquals(coachId, entity.getVerifiedBy());
    assertTrue(entity.isVerified());
  }

  @Test
  @DisplayName("logScore: an athlete's own score on a competition WOD waits for verification")
  void testLogScore_CompetitionSelf_NotVerified() {
    wodProxy.setCompetition(true);
    wodProxy.setGymId(100L);
    when(securityService.getCurrentUserId()).thenReturn(userId);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(wodProxy));
    when(scoreMapper.toEntity(requestTime)).thenReturn(scoreEntityTime);
    when(scoreRepository.save(scoreEntityTime)).thenReturn(scoreEntityTime);
    when(scoreRepository.findByWodIdAndUserId(1L, userId)).thenReturn(List.of(scoreEntityTime));

    scoreService.logScore(requestTime);

    assertEquals(userId, scoreEntityTime.getLoggedBy());
    assertFalse(scoreEntityTime.isVerified());
  }

  @Test
  @DisplayName("setVerified: verifies then withdraws the verification of a competition score")
  void testSetVerified() {
    wodProxy.setCompetition(true);
    when(securityService.getCurrentUserId()).thenReturn(2L);
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));
    when(scoreRepository.save(scoreEntityTime)).thenReturn(scoreEntityTime);

    scoreService.setVerified(50L, true);
    assertEquals(2L, scoreEntityTime.getVerifiedBy());
    assertNotNull(scoreEntityTime.getVerifiedAt());

    scoreService.setVerified(50L, false);
    assertNull(scoreEntityTime.getVerifiedBy());
    assertFalse(scoreEntityTime.isVerified());
  }

  @Test
  @DisplayName("setVerified: only competition scores can be verified (400)")
  void testSetVerified_NotCompetition() {
    when(scoreRepository.findById(50L)).thenReturn(Optional.of(scoreEntityTime));

    assertThrows(InvalidScoreException.class, () -> scoreService.setVerified(50L, true));
    verify(scoreRepository, never()).save(any());
  }
}
