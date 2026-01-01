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
   * <p>The results are ordered by date (descending). Uses an optimized repository query to fetch
   * WOD details efficiently.
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
   * WOD's {@link ScoreType} and the user's history. Normalization of units is handled by the
   * mapper.
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

    // Map DTO to Entity (Values converted to Base Units)
    WodScore score = scoreMapper.toEntity(request);
    score.setWod(wod);
    score.setUserId(userId);
    score.setLoggedAt(java.time.LocalDateTime.now());

    // PR Logic: Check if this is the best performance
    boolean isPr = checkIsPersonalRecord(userId, wod, score);
    score.setPersonalRecord(isPr);

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

    if (!SecurityUtils.isCurrentUser(score.getUserId())) {
      throw new AccessDeniedException("error.score.unauthorized.delete");
    }

    scoreRepository.delete(score);
  }

  // -------------------------------------------------------------------------
  // PR Calculation Logic
  // -------------------------------------------------------------------------

  private boolean checkIsPersonalRecord(String userId, Wod wod, WodScore current) {
    WodScore oldPr =
        scoreRepository.findByWodIdAndUserIdAndPersonalRecordTrue(wod.getId(), userId).orElse(null);

    if (oldPr == null) {
      return wod.getScoreType() != ScoreType.NONE;
    }
    return isBetterScore(current, oldPr, wod.getScoreType());
  }

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

  private boolean compareTime(WodScore current, WodScore old) {
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
    double c = current != null ? current.doubleValue() : 0.0;
    double o = old != null ? old.doubleValue() : 0.0;
    return c > o;
  }
}
