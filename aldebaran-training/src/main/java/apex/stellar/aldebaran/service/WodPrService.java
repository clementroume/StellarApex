package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service dedicated to Personal Record (PR) calculations.
 *
 * <p>Extracted from WodScoreService to handle transaction isolation and avoid race conditions
 * during PR updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WodPrService {

  private final WodScoreRepository scoreRepository;

  /**
   * Re-evaluates all scores for a given User/WOD combination to determine the single PR.
   *
   * <p><b>Concurrency Control:</b> Uses {@code SERIALIZABLE} isolation in a new transaction to
   * ensure exclusive access to the user's history for this WOD during calculation, preventing
   * race conditions where two concurrent requests could both be flagged as PRs.
   *
   * @param wod The WOD being scored.
   * @param userId The user ID.
   * @param currentScoreId The ID of the score currently being processed (or null if deleting).
   * @return true if the currentScoreId is the new PR, false otherwise.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean updatePrStatus(Wod wod, Long userId, Long currentScoreId) {
    if (wod.getScoreType() == ScoreType.NONE) {
      return false;
    }

    List<WodScore> allScores = scoreRepository.findByWodIdAndUserId(wod.getId(), userId);
    if (allScores.isEmpty()) {
      return false;
    }

    // 1. Reset all PR flags to false (in memory)
    allScores.forEach(s -> s.setPersonalRecord(false));

    // 2. Find the best score
    WodScore bestScore =
        allScores.stream()
            .reduce((s1, s2) -> isBetterScore(s1, s2, wod.getScoreType()) ? s1 : s2)
            .orElse(allScores.getFirst());

    // 3. Set PR flag on the winner
    bestScore.setPersonalRecord(true);

    // 4. Persist changes
    // Using saveAll to ensure all flag updates (resets and sets) are flushed
    scoreRepository.saveAll(allScores);

    // 5. Check if the current score is the winner
    return bestScore.getId().equals(currentScoreId);
  }

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

  private boolean compareTime(WodScore current, WodScore other) {
    if (current.getTimeSeconds() == null) {
      return false;
    }
    return other.getTimeSeconds() == null || current.getTimeSeconds() < other.getTimeSeconds();
  }

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

  private boolean compareValues(Number current, Number other) {
    double c = current != null ? current.doubleValue() : 0.0;
    double o = other != null ? other.doubleValue() : 0.0;
    return c > o;
  }
}