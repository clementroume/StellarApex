package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.WodScoreMapper;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import apex.stellar.aldebaran.security.SecurityService;
import java.util.List;
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
public class WodScoreService {

  private static final String ERROR_SCORE_NOT_FOUND = "error.score.not.found";

  private final WodScoreRepository scoreRepository;
  private final WodRepository wodRepository;
  private final WodScoreMapper scoreMapper;
  private final SecurityService securityService;
  private final WodService wodService;

  /**
   * Retrieves a paginated list of scores for the currently authenticated user.
   *
   * @param wodId Optional WOD ID to filter scores.
   * @param pageable Pagination and sorting information.
   * @return A slice of WodScoreResponse objects.
   */
  @Transactional(readOnly = true)
  public Slice<WodScoreResponse> getMyScores(Long wodId, Pageable pageable) {
    Long userId = securityService.getCurrentUserId();

    Pageable sortedPageable =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by("date").descending());

    Slice<WodScore> scores =
        (wodId != null)
            ? scoreRepository.findByUserIdAndWodId(userId, wodId, sortedPageable)
            : scoreRepository.findByUserId(userId, sortedPageable);

    return scores.map(scoreMapper::toResponse);
  }

  /**
   * Logs a new score for a WOD.
   *
   * @param request The score details.
   * @return The newly created WodScoreResponse.
   */
  @Transactional
  public WodScoreResponse logScore(WodScoreRequest request) {
    Long targetUserId =
        request.userId() != null ? request.userId() : securityService.getCurrentUserId();

    WodScore newScore = scoreMapper.toEntity(request);
    newScore.setWod(wodRepository.getReferenceById(request.wodId()));
    newScore.setUserId(targetUserId);
    newScore.setLoggedAt(java.time.LocalDateTime.now());

    WodScore savedScore = scoreRepository.save(newScore);

    recalculatePrsForUser(request.wodId(), targetUserId);

    return scoreMapper.toResponse(savedScore);
  }

  /**
   * Updates an existing score.
   *
   * @param id The ID of the score to update.
   * @param request The updated score details.
   * @return The updated WodScoreResponse.
   */
  @Transactional
  public WodScoreResponse updateScore(Long id, WodScoreRequest request) {
    WodScore existingScore =
        scoreRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, id));

    scoreMapper.updateEntity(request, existingScore);

    if (!existingScore.getWod().getId().equals(request.wodId())) {
      existingScore.setWod(wodRepository.getReferenceById(request.wodId()));
    }

    WodScore savedScore = scoreRepository.save(existingScore);

    recalculatePrsForUser(request.wodId(), savedScore.getUserId());

    return scoreMapper.toResponse(savedScore);
  }

  /**
   * Deletes a score and triggers a PR recalculation for the user.
   *
   * @param scoreId The ID of the score to delete.
   */
  @Transactional
  public void deleteScore(Long scoreId) {
    WodScore score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));

    Long wodId = score.getWod().getId();
    Long userId = score.getUserId();

    scoreRepository.delete(score);
    scoreRepository.flush();

    recalculatePrsForUser(wodId, userId);
  }

  /**
   * Compares a specific score against all other scores for the same WOD and scaling.
   *
   * @param scoreId The ID of the score to compare.
   * @return A ScoreComparisonResponse containing rank and percentile.
   */
  @Transactional(readOnly = true)
  public ScoreComparisonResponse compareScore(Long scoreId) {
    WodScore score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));

    Long wodId = score.getWod().getId();
    WodResponse wodResponse = wodService.getWodDetail(wodId);

    long total = scoreRepository.countByWodIdAndScaling(wodId, score.getScaling());

    long better =
        switch (wodResponse.scoreType()) {
          case TIME ->
              scoreRepository.countBetterTime(wodId, score.getScaling(), score.getTimeSeconds());
          case ROUNDS_REPS ->
              scoreRepository.countBetterRoundsReps(
                  wodId, score.getScaling(), score.getRounds(), score.getReps());
          case REPS -> scoreRepository.countBetterReps(wodId, score.getScaling(), score.getReps());
          case WEIGHT ->
              scoreRepository.countBetterWeight(wodId, score.getScaling(), score.getMaxWeightKg());
          case LOAD ->
              scoreRepository.countBetterLoad(wodId, score.getScaling(), score.getTotalLoadKg());
          case DISTANCE ->
              scoreRepository.countBetterDistance(
                  wodId, score.getScaling(), score.getTotalDistanceMeters());
          case CALORIES ->
              scoreRepository.countBetterCalories(
                  wodId, score.getScaling(), score.getTotalCalories());
          case NONE -> 0;
        };

    long rank = better + 1;
    double percentile = total > 1 ? ((double) (total - rank) / (total - 1)) * 100.0 : 100.0;

    return new ScoreComparisonResponse(rank, total, percentile);
  }

  /**
   * Retrieves the leaderboard for a specific WOD and scaling level.
   *
   * @param wodId The ID of the WOD.
   * @param scaling The scaling level (e.g., RX, Scaled).
   * @param pageable Pagination information.
   * @return A slice of WodScoreResponse objects representing the leaderboard.
   */
  @Transactional(readOnly = true)
  public Slice<WodScoreResponse> getLeaderboard(
      Long wodId, ScalingLevel scaling, Pageable pageable) {
    WodResponse wod = wodService.getWodDetail(wodId);

    Sort sort = getSortForScoreType(wod.scoreType());
    Pageable sortedPageable =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

    return scoreRepository
        .findByWodIdAndScalingAndPersonalRecordTrue(wodId, scaling, sortedPageable)
        .map(scoreMapper::toResponse);
  }

  /**
   * Recalculates which score is the Personal Record (PR) for a user on a specific WOD.
   *
   * @param wodId The ID of the WOD.
   * @param userId The ID of the user.
   */
  private void recalculatePrsForUser(Long wodId, Long userId) {
    WodResponse wodResponse = wodService.getWodDetail(wodId);

    if (wodResponse.scoreType() == ScoreType.NONE) {
      return;
    }

    List<WodScore> allScores = scoreRepository.findByWodIdAndUserId(wodId, userId);
    if (allScores.isEmpty()) {
      return;
    }

    // 1. Reset all PR flags in memory
    allScores.forEach(s -> s.setPersonalRecord(false));

    // 2. Find the absolute best score
    WodScore bestScore =
        allScores.stream()
            .reduce((s1, s2) -> isBetterScore(s1, s2, wodResponse.scoreType()) ? s1 : s2)
            .orElse(allScores.getFirst());

    // 3. Mark the winner
    bestScore.setPersonalRecord(true);

    // 4. Save to DB (Hibernate will only issue UPDATEs for rows that actually changed)
    scoreRepository.saveAll(allScores);
  }

  /**
   * Compares two scores to determine which one is better based on the WOD score type.
   *
   * @param current The first score to compare.
   * @param other The second score to compare.
   * @param type The scoring type of the WOD.
   * @return true if the current score is better than the other score, false otherwise.
   */
  private boolean isBetterScore(WodScore current, WodScore other, ScoreType type) {
    return switch (type) {
      case TIME -> compareTime(current, other);
      case ROUNDS_REPS -> compareRoundsReps(current, other);
      case REPS -> compareValues(current.getReps(), other.getReps());
      case WEIGHT -> compareValues(current.getMaxWeightKg(), other.getMaxWeightKg());
      case LOAD -> compareValues(current.getTotalLoadKg(), other.getTotalLoadKg());
      case CALORIES -> compareValues(current.getTotalCalories(), other.getTotalCalories());
      case DISTANCE ->
          compareValues(current.getTotalDistanceMeters(), other.getTotalDistanceMeters());
      case NONE -> false;
    };
  }

  /**
   * Compares two scores for time-based WODs.
   *
   * @param current The current score.
   * @param other The other score.
   * @return true if the current time is faster (lower) than other time.
   */
  private boolean compareTime(WodScore current, WodScore other) {
    if (current.getTimeSeconds() == null) {
      return false;
    }
    return other.getTimeSeconds() == null || current.getTimeSeconds() < other.getTimeSeconds();
  }

  /**
   * Compares two scores for AMRAP WODs (Rounds + Reps).
   *
   * @param current The current score.
   * @param other The other score.
   * @return true if the current has more rounds, or same rounds and more reps.
   */
  private boolean compareRoundsReps(WodScore current, WodScore other) {
    int currentRounds = current.getRounds() != null ? current.getRounds() : 0;
    int otherRounds = other.getRounds() != null ? other.getRounds() : 0;
    if (currentRounds != otherRounds) {
      return currentRounds > otherRounds;
    }
    int currentReps = current.getReps() != null ? current.getReps() : 0;
    int otherReps = other.getReps() != null ? other.getReps() : 0;
    return currentReps > otherReps;
  }

  /**
   * Compares two numeric values where a higher value is better.
   *
   * @param current The current value.
   * @param other The other value.
   * @return true if the current is greater than the other.
   */
  private boolean compareValues(Number current, Number other) {
    double c = current != null ? current.doubleValue() : 0.0;
    double o = other != null ? other.doubleValue() : 0.0;
    return c > o;
  }

  /**
   * Determines the sorting order for the leaderboard based on the WOD's score type.
   *
   * @param type The scoring type.
   * @return A Sort object configured for the specific type.
   */
  private Sort getSortForScoreType(ScoreType type) {
    return switch (type) {
      case TIME -> Sort.by("timeSeconds").ascending();
      case ROUNDS_REPS -> Sort.by("rounds").descending().and(Sort.by("reps").descending());
      case REPS -> Sort.by("reps").descending();
      case WEIGHT -> Sort.by("maxWeightKg").descending();
      case LOAD -> Sort.by("totalLoadKg").descending();
      case DISTANCE -> Sort.by("totalDistanceMeters").descending();
      case CALORIES -> Sort.by("totalCalories").descending();
      case NONE -> Sort.by("date").descending();
    };
  }
}
