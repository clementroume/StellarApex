package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.config.SecurityUtils;
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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for logging and managing athlete performance scores.
 *
 * <p>Handles security checks (ownership), PR (Personal Record) calculations, and DTO mapping.
 * Integrates with {@link WodScoreRepository} for optimized data access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WodScoreService {

  private final WodScoreRepository scoreRepository;
  private final WodRepository wodRepository;
  private final WodScoreMapper scoreMapper;

  /**
   * Retrieves all scores for the currently authenticated user.
   *
   * <p>The results are ordered by date (descending). Can be filtered by WOD ID to see progress on a specific workout.
   * <p><b>Note:</b> Caching is removed here in favor of pagination to avoid complex cache key management and stale data on large lists.
   *
   * @param wodId Optional WOD ID to filter the history.
   * @param pageable Pagination info.
   * @return Page of score responses.
   */
  @Transactional(readOnly = true)
  public Page<WodScoreResponse> getMyScores(Long wodId, Pageable pageable) {
    Long userId = SecurityUtils.getCurrentUserId();
    
    // Force sort by Date DESC if not specified
    Pageable sortedPageable = pageable.getSort().isSorted() 
        ? pageable 
        : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("date").descending());

    Page<WodScore> scores = (wodId != null)
        ? scoreRepository.findByUserIdAndWodId(userId, wodId, sortedPageable)
        : scoreRepository.findByUserId(userId, sortedPageable);

    return scores.map(scoreMapper::toResponse);
  }

  /**
   * Retrieves the leaderboard for a specific WOD.
   *
   * @param wodId The WOD ID.
   * @param scaling The scaling level (default RX).
   * @param pageable Pagination info.
   * @return Page of scores sorted by performance (Best first).
   */
  @Transactional(readOnly = true)
  public Page<WodScoreResponse> getLeaderboard(Long wodId, ScalingLevel scaling, Pageable pageable) {
    Wod wod = wodRepository.findById(wodId)
        .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", wodId));

    // Determine Sort direction based on ScoreType
    Sort sort = getSortForScoreType(wod.getScoreType());
    Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

    return scoreRepository.findByWodIdAndScalingAndPersonalRecordTrue(wodId, scaling, sortedPageable)
        .map(scoreMapper::toResponse);
  }

  /**
   * Logs a new score for the current user.
   *
   * <p>Automatically calculates if this result constitutes a new Personal Record (PR) based on the
   * WOD's {@link ScoreType} and the user's history. The mapper handles normalization of units.
   *
   * @param request The score data to log.
   * @return The persisted score response.
   * @throws ResourceNotFoundException if the WOD ID is invalid.
   */
  @Transactional
  // No generic cache eviction needed as pagination makes caching 'my-scores' impractical.
  public WodScoreResponse logScore(WodScoreRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();

    Wod wod =
        wodRepository
            .findById(request.wodId())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.wod.not.found", request.wodId()));

    // Map DTO to Entity (Values converted to Base Units)
    WodScore score = scoreMapper.toEntity(request);
    score.setWod(wod);
    score.setUserId(userId);
    score.setLoggedAt(java.time.LocalDateTime.now());

    // PR Logic: Check if this is the best performance
    Optional<WodScore> oldPr = scoreRepository.findByWodIdAndUserIdAndPersonalRecordTrue(wod.getId(), userId);
    boolean isPr = checkIsPersonalRecord(wod, score, oldPr.orElse(null));
    
    score.setPersonalRecord(isPr);

    // If new PR, unmark the old one
    if (isPr && oldPr.isPresent()) {
      oldPr.get().setPersonalRecord(false);
      scoreRepository.save(oldPr.get());
    }

    WodScore saved = scoreRepository.save(score);

    if (isPr) {
      log.info("New PR for user {} on WOD {}!", userId, wod.getTitle());
    }

    return scoreMapper.toResponse(saved);
  }

  /**
   * Deletes a score if it belongs to the current user.
   *
   * @param scoreId The ID of the score to delete.
   * @throws ResourceNotFoundException if the score does not exist.
   * @throws AccessDeniedException if the user does not own the score.
   */
  @Transactional
  public void deleteScore(Long scoreId) {
    WodScore score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException("error.score.not.found", scoreId));

    // Check ownership (converting Long to String for utility check, or use direct comparison)
    if (!score.getUserId().equals(SecurityUtils.getCurrentUserId())) {
      throw new AccessDeniedException("error.score.unauthorized.delete");
    }

    // PR Logic: If deleting a PR, promote the next best score
    if (score.isPersonalRecord()) {
      recalculatePrOnDelete(score);
    }

    scoreRepository.delete(score);
  }

  /**
   * Calculates the rank and percentile of a specific score within its WOD and Scaling category.
   *
   * @param scoreId The ID of the score to analyze.
   * @return The comparison result (Rank, Total, Percentile).
   * @throws ResourceNotFoundException if the score is not found.
   */
  @Transactional(readOnly = true)
  public ScoreComparisonResponse compareScore(Long scoreId) {
    WodScore score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException("error.score.not.found", scoreId));

    Wod wod = score.getWod();
    long total = scoreRepository.countByWodIdAndScaling(wod.getId(), score.getScaling());
    long better = 0;

    switch (wod.getScoreType()) {
      case TIME -> better = scoreRepository.countBetterTime(wod.getId(), score.getScaling(), score.getTimeSeconds());
      case ROUNDS_REPS -> better = scoreRepository.countBetterRoundsReps(wod.getId(), score.getScaling(), score.getRounds(), score.getReps());
      case REPS -> better = scoreRepository.countBetterReps(wod.getId(), score.getScaling(), score.getReps());
      case WEIGHT -> better = scoreRepository.countBetterWeight(wod.getId(), score.getScaling(), score.getMaxWeightKg());
      case LOAD -> better = scoreRepository.countBetterLoad(wod.getId(), score.getScaling(), score.getTotalLoadKg());
      case DISTANCE -> better = scoreRepository.countBetterDistance(wod.getId(), score.getScaling(), score.getTotalDistanceMeters());
      case CALORIES -> better = scoreRepository.countBetterCalories(wod.getId(), score.getScaling(), score.getTotalCalories());
      case NONE -> better = 0;
    }

    long rank = better + 1;
    double percentile = total > 1 ? ((double) (total - rank) / (total - 1)) * 100.0 : 100.0;

    return new ScoreComparisonResponse(rank, total, percentile);
  }

  // -------------------------------------------------------------------------
  // PR Calculation Logic
  // -------------------------------------------------------------------------

  /**
   * Determines if the current score qualifies as a Personal Record (PR).
   *
   * @param wod The WOD performed.
   * @param current The new score being logged.
   * @param oldPr The previous PR, if any.
   * @return true if the current score is better than the old PR.
   */
  private boolean checkIsPersonalRecord(Wod wod, WodScore current, WodScore oldPr) {
    if (oldPr == null) {
      return wod.getScoreType() != ScoreType.NONE;
    }
    return isBetterScore(current, oldPr, wod.getScoreType());
  }

  /**
   * Compares two scores to determine which one represents a better performance.
   *
   * @param current The new score.
   * @param old The existing score.
   * @param type The scoring metric (e.g., TIME, WEIGHT).
   * @return true if current is better than old.
   */
  private boolean isBetterScore(WodScore current, WodScore old, ScoreType type) {
    return switch (type) {
      case TIME -> compareTime(current, old);
      case ROUNDS_REPS -> compareRoundsReps(current, old);
      case REPS -> compareValues(current.getReps(), old.getReps());
      // Comparison on normalized KG values
      case WEIGHT -> compareValues(current.getMaxWeightKg(), old.getMaxWeightKg());
      case LOAD -> compareValues(current.getTotalLoadKg(), old.getTotalLoadKg());
      case CALORIES -> compareValues(current.getTotalCalories(), old.getTotalCalories());
      // Comparison on normalized Meter values
      case DISTANCE ->
          compareValues(current.getTotalDistanceMeters(), old.getTotalDistanceMeters());
      case NONE -> false;
    };
  }

  /**
   * Compares two time-based scores (lower is better).
   *
   * @param current The new score.
   * @param old The existing score.
   * @return true if current time is less than old time.
   */
  private boolean compareTime(WodScore current, WodScore old) {
    if (current.getTimeSeconds() == null) {
      return false;
    }
    return old.getTimeSeconds() == null || current.getTimeSeconds() < old.getTimeSeconds();
  }

  /**
   * Compares two AMRAP scores based on rounds and repetitions.
   *
   * @param current The new score.
   * @param old The existing score.
   * @return true if current has more rounds, or equal rounds and more reps.
   */
  private boolean compareRoundsReps(WodScore current, WodScore old) {
    int currentRounds = current.getRounds() != null ? current.getRounds() : 0;
    int oldRounds = old.getRounds() != null ? old.getRounds() : 0;

    if (currentRounds != oldRounds) {
      return currentRounds > oldRounds;
    }

    int currentReps = current.getReps() != null ? current.getReps() : 0;
    int oldReps = old.getReps() != null ? old.getReps() : 0;
    return currentReps > oldReps;
  }

  /**
   * Compares two numeric values (higher is better).
   *
   * @param current The new value.
   * @param old The existing value.
   * @return true if current is greater than old.
   */
  private boolean compareValues(Number current, Number old) {
    double c = current != null ? current.doubleValue() : 0.0;
    double o = old != null ? old.doubleValue() : 0.0;
    return c > o;
  }

  /**
   * Recalculates and promotes the next best score to PR status after a PR deletion.
   *
   * @param scoreToDelete The PR score being deleted.
   */
  private void recalculatePrOnDelete(WodScore scoreToDelete) {
    List<WodScore> allScores = scoreRepository.findByWodIdAndUserId(scoreToDelete.getWod().getId(), scoreToDelete.getUserId());
    
    // Find best score excluding the one being deleted
    WodScore newPr = allScores.stream()
        .filter(s -> !s.getId().equals(scoreToDelete.getId()))
        .reduce((s1, s2) -> isBetterScore(s1, s2, scoreToDelete.getWod().getScoreType()) ? s1 : s2)
        .orElse(null);

    if (newPr != null) {
      newPr.setPersonalRecord(true);
      scoreRepository.save(newPr);
      log.info("PR transferred to score {} after deletion", newPr.getId());
    }
  }

  /**
   * Determines the sorting strategy based on the WOD's score type.
   *
   * @param type The score type.
   * @return The Sort object for database queries.
   */
  private Sort getSortForScoreType(ScoreType type) {
    return switch (type) {
      case TIME -> Sort.by("timeSeconds").ascending(); // Lower time is better
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
