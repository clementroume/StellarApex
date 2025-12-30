package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.config.SecurityUtils;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.WodScoreMapper;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for logging and managing athlete performance scores.
 *
 * <p>Handles security checks (ownership), PR (Personal Record) calculations, and DTO mapping.
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
   * @return List of score responses ordered by date (descending).
   */
  @Transactional(readOnly = true)
  public List<WodScoreResponse> getMyScores() {
    String userId = SecurityUtils.getCurrentUserId();
    return scoreRepository.findByUserIdOrderByDateDesc(userId).stream()
        .map(scoreMapper::toResponse)
        .toList();
  }

  /**
   * Logs a new score for the current user.
   *
   * <p>Automatically calculates if this result constitutes a new Personal Record (PR) based on the
   * WOD's {@link ScoreType} and the user's history.
   *
   * @param request The score data to log.
   * @return The persisted score response.
   * @throws ResourceNotFoundException if the WOD ID is invalid.
   */
  @Transactional
  @CacheEvict(value = "wod-scores", key = "#request.wodId()")
  public WodScoreResponse logScore(WodScoreRequest request) {
    String userId = SecurityUtils.getCurrentUserId();

    Wod wod =
        wodRepository
            .findById(request.wodId())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.wod.not.found", request.wodId()));

    // Map DTO to Entity (ignores ID, User, PR status)
    WodScore score = scoreMapper.toEntity(request);
    score.setWod(wod);
    score.setUserId(userId);
    score.setLoggedAt(java.time.LocalDateTime.now());

    // PR Logic: Check if this is the best performance
    boolean isPr = checkIsPersonalRecord(userId, wod, score);
    score.setPersonalRecord(isPr);

    WodScore saved = scoreRepository.save(score);

    if (isPr) {
      log.info(
          "New PR for user {} on WOD {} (Type: {})!", userId, wod.getTitle(), wod.getScoreType());
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

    if (!SecurityUtils.isCurrentUser(score.getUserId())) {
      throw new AccessDeniedException("error.score.unauthorized.delete");
    }

    scoreRepository.delete(score);
  }

  // -------------------------------------------------------------------------
  // PR Calculation Logic
  // -------------------------------------------------------------------------

  /** Determines if the current score is better than any previous score for this specific WOD. */
  private boolean checkIsPersonalRecord(String userId, Wod wod, WodScore current) {
    // 1. Retrieve previous PR
    WodScore oldPr = findPreviousPr(userId, wod.getId());

    // 2. If no previous PR, it's a PR (unless unscored)
    if (oldPr == null) {
      return wod.getScoreType() != ScoreType.NONE;
    }

    // 3. Delegate comparison based on type
    return isBetterScore(current, oldPr, wod.getScoreType());
  }

  private WodScore findPreviousPr(String userId, Long wodId) {
    return scoreRepository.findByUserIdAndPersonalRecordTrue(userId).stream()
        .filter(s -> s.getWod().getId().equals(wodId))
        .findFirst()
        .orElse(null);
  }

  private boolean isBetterScore(WodScore current, WodScore old, ScoreType type) {
    return switch (type) {
      case TIME -> compareTime(current, old);
      case ROUNDS_REPS -> compareRoundsReps(current, old);
      case REPS -> compareValues(current.getReps(), old.getReps());
      case WEIGHT -> compareValues(current.getMaxWeightInKg(), old.getMaxWeightInKg());
      case LOAD -> compareValues(current.getTotalLoadInKg(), old.getTotalLoadInKg());
      case CALORIES -> compareValues(current.getTotalCalories(), old.getTotalCalories());
      case DISTANCE ->
          compareValues(current.getTotalDistanceInMeters(), old.getTotalDistanceInMeters());
      case NONE -> false;
    };
  }

  // --- Specific Comparators ---

  private boolean compareTime(WodScore current, WodScore old) {
    // Lower is better for time
    if (current.getTimeSeconds() == null) return false;
    return old.getTimeSeconds() == null || current.getTimeSeconds() < old.getTimeSeconds();
  }

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

  private boolean compareValues(Number current, Number old) {
    // Generic "Higher is better" comparison
    double c = current != null ? current.doubleValue() : 0.0;
    double o = old != null ? old.doubleValue() : 0.0;
    return c > o;
  }
}
