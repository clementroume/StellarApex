package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.ScoreReferenceData;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for managing WOD (Workout of the Day) scores. Handles logging, updating,
 * deleting, and calculating personal records and leaderboards.
 */
@Service
@RequiredArgsConstructor
public class ScoreService {

  private static final String ERROR_SCORE_NOT_FOUND = "error.score.not.found";
  private static final String ERROR_WOD_NOT_FOUND = "error.wod.not.found";

  private final ScoreRepository scoreRepository;
  private final WodRepository wodRepository;
  private final ScoreMapper scoreMapper;
  private final SecurityService securityService;
  private final WodService wodService;

  /**
   * Retrieves a paginated list of scores for the currently authenticated user.
   *
   * @param wodId Optional WOD ID to filter scores.
   * @param pageable Pagination and sorting information.
   * @return A slice of ScoreResponse objects.
   */
  @Transactional(readOnly = true)
  public Slice<ScoreResponse> getMyScores(Long wodId, Pageable pageable) {
    Long userId = securityService.getCurrentUserId();

    Pageable sortedPageable =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by("date").descending());

    Slice<Score> scores =
        (wodId != null)
            ? scoreRepository.findByUserIdAndWodId(userId, wodId, sortedPageable)
            : scoreRepository.findByUserId(userId, sortedPageable);

    return scores.map(scoreMapper::toResponse);
  }

  /**
   * Logs a new score for a WOD.
   *
   * @param request The score details.
   * @return The newly created ScoreResponse.
   * @throws ResourceNotFoundException if the WOD does not exist.
   * @throws InvalidScoreException if the score does not match the WOD's score type.
   */
  @Transactional
  public ScoreResponse logScore(ScoreRequest request) {
    Wod wod =
        wodRepository
            .findById(request.wodId())
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_WOD_NOT_FOUND, request.wodId()));
    Long targetUserId =
        request.userId() != null ? request.userId() : securityService.getCurrentUserId();

    Score newScore = scoreMapper.toEntity(request);
    newScore.setWod(wod);
    newScore.setUserId(targetUserId);
    newScore.setLoggedAt(LocalDateTime.now());
    normalizeAndCheck(newScore, wod.getScoreType());

    Score savedScore = scoreRepository.save(newScore);

    recalculatePrsForUser(wod, targetUserId);

    return scoreMapper.toResponse(savedScore);
  }

  /**
   * Updates an existing score. The athlete and the WOD of a score never change.
   *
   * @param id The ID of the score to update.
   * @param request The updated score details.
   * @return The updated ScoreResponse.
   * @throws InvalidScoreException if the request points at another WOD or does not match the
   *     WOD's score type.
   */
  @Transactional
  public ScoreResponse updateScore(Long id, ScoreRequest request) {
    Score existingScore =
        scoreRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, id));
    Wod wod = existingScore.getWod();
    if (!Objects.equals(wod.getId(), request.wodId())) {
      throw new InvalidScoreException("error.score.wod.mismatch", id, wod.getId());
    }

    scoreMapper.updateEntity(request, existingScore);
    normalizeAndCheck(existingScore, wod.getScoreType());

    Score savedScore = scoreRepository.save(existingScore);

    recalculatePrsForUser(wod, savedScore.getUserId());

    return scoreMapper.toResponse(savedScore);
  }

  /**
   * Deletes a score and triggers a PR recalculation for the user.
   *
   * @param scoreId The ID of the score to delete.
   */
  @Transactional
  public void deleteScore(Long scoreId) {
    Score score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));

    Wod wod = score.getWod();
    Long userId = score.getUserId();

    scoreRepository.delete(score);
    scoreRepository.flush();

    recalculatePrsForUser(wod, userId);
  }

  /**
   * Ranks a score among the Personal Records of the other athletes on the same WOD and scaling
   * level, with the ordering of {@link ScoreRanking}.
   *
   * @param scoreId The ID of the score to compare.
   * @return A ScoreComparisonResponse containing rank, total and percentile.
   */
  @Transactional(readOnly = true)
  public ScoreComparisonResponse compareScore(Long scoreId) {
    Score score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));

    Comparator<Score> order =
        ScoreRanking.performanceBestFirst(score.getWod().getScoreType());
    List<Score> others =
        scoreRepository
            .findByWodIdAndScalingAndPersonalRecordTrue(score.getWod().getId(), score.getScaling())
            .stream()
            .filter(other -> !Objects.equals(other.getUserId(), score.getUserId()))
            .toList();

    long better = others.stream().filter(other -> order.compare(other, score) < 0).count();
    long total = others.size() + 1L;
    long rank = better + 1;
    double percentile = total > 1 ? ((double) (total - rank) / (total - 1)) * 100.0 : 100.0;

    return new ScoreComparisonResponse(rank, total, percentile);
  }

  /**
   * Retrieves the leaderboard for a specific WOD and scaling level: the Personal Record of each
   * athlete whose best score is at that level, best first.
   *
   * @param wodId The ID of the WOD.
   * @param scaling The scaling level (e.g., RX, Scaled).
   * @param pageable Pagination information.
   * @return A slice of ScoreResponse objects representing the leaderboard.
   */
  @Transactional(readOnly = true)
  public Slice<ScoreResponse> getLeaderboard(Long wodId, ScalingLevel scaling, Pageable pageable) {
    WodResponse wod = wodService.getWodDetail(wodId);

    Pageable sortedPageable =
        PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            ScoreRanking.leaderboardSort(wod.scoreType()));

    return scoreRepository
        .findByWodIdAndScalingAndPersonalRecordTrue(wodId, scaling, sortedPageable)
        .map(scoreMapper::toResponse);
  }

  /** Retrieves reference data for WOD Score forms. */
  public ScoreReferenceData getReferenceData() {
    List<String> scalingLevels = Arrays.stream(ScalingLevel.values()).map(Enum::name).toList();
    return new ScoreReferenceData(scalingLevels);
  }

  /**
   * Clears the values that do not apply to the WOD's score type, then checks that the value it
   * needs is present. A time-capped TIME score is recorded as the reps completed at the cap.
   *
   * @param score The score about to be saved.
   * @param type The WOD's score type.
   * @throws InvalidScoreException if the required value is missing.
   */
  private void normalizeAndCheck(Score score, ScoreType type) {
    boolean capped = type == ScoreType.TIME && Boolean.TRUE.equals(score.getTimeCapped());
    score.setTimeCapped(capped);
    if (capped) {
      score.setTimeSeconds(null);
    }

    boolean valid =
        switch (type) {
          case TIME ->
              capped
                  ? score.getReps() != null
                  : score.getTimeSeconds() != null && score.getTimeSeconds() > 0;
          case ROUNDS_REPS -> score.getRounds() != null || score.getReps() != null;
          case REPS -> score.getReps() != null;
          case WEIGHT -> score.getMaxWeightKg() != null && score.getMaxWeightKg() > 0;
          case LOAD -> score.getTotalLoadKg() != null;
          case CALORIES -> score.getTotalCalories() != null;
          case DISTANCE -> score.getTotalDistanceMeters() != null;
          case NONE -> true;
        };
    if (!valid) {
      throw new InvalidScoreException(
          capped ? "error.score.capped.reps.required" : "error.score.value.required." + type);
    }
  }

  /**
   * Recalculates which score is the Personal Record (PR) for a user on a specific WOD, with the
   * ordering of {@link ScoreRanking} (scaling tier first, then performance).
   *
   * @param wod The WOD.
   * @param userId The ID of the user.
   */
  private void recalculatePrsForUser(Wod wod, Long userId) {
    if (wod.getScoreType() == ScoreType.NONE) {
      return;
    }

    List<Score> allScores = scoreRepository.findByWodIdAndUserId(wod.getId(), userId);
    if (allScores.isEmpty()) {
      return;
    }

    Score bestScore =
        allScores.stream().min(ScoreRanking.bestFirst(wod.getScoreType())).orElseThrow();
    allScores.forEach(s -> s.setPersonalRecord(s == bestScore));

    // Hibernate only issues UPDATEs for rows that actually changed
    scoreRepository.saveAll(allScores);
  }
}
