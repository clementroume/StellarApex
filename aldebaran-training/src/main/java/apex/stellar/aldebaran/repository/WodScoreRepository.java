package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link WodScore} entities.
 *
 * <p>Handles data access for athlete logs and workout results.
 */
@Repository
public interface WodScoreRepository extends JpaRepository<WodScore, Long> {

  /**
   * Retrieves the score history for a specific user, ordered by date (newest first).
   *
   * <p><b>Optimization:</b> Uses {@link EntityGraph} to eagerly load the associated {@code Wod}
   * entity. This prevents the N+1 select problem when mapping the list to response DTOs that
   * include WOD summaries.
   *
   * @param userId The ID of the user.
   * @return A list of the user's scores with WOD details loaded.
   */
  @EntityGraph(attributePaths = {"wod"})
  Slice<WodScore> findByUserId(Long userId, Pageable pageable);

  /**
   * Retrieves the score history for a specific user on a specific WOD.
   *
   * @param userId The ID of the user.
   * @param wodId The ID of the WOD.
   * @param pageable Pagination info.
   * @return A list of scores ordered by date.
   */
  @EntityGraph(attributePaths = {"wod"})
  Slice<WodScore> findByUserIdAndWodId(Long userId, Long wodId, Pageable pageable);

  /**
   * Retrieves all scores for a user on a WOD (unordered). Used for PR recalculation.
   *
   * @param wodId The ID of the WOD.
   * @param userId The ID of the user.
   * @return List of scores.
   */
  List<WodScore> findByWodIdAndUserId(Long wodId, Long userId);

  /**
   * Checks if any scores exist for a given WOD.
   *
   * @param wodId The ID of the WOD.
   * @return true if at least one score exists.
   */
  boolean existsByWodId(Long wodId);

  /**
   * Retrieves the leaderboard (PRs only) for a specific WOD and scaling level.
   *
   * @param wodId The WOD ID.
   * @param scaling The scaling level (RX, SCALED...).
   * @param pageable Pagination and Sorting (Sort is handled by Service based on WOD type).
   * @return A page of Personal Record scores.
   */
  Slice<WodScore> findByWodIdAndScalingAndPersonalRecordTrue(
      Long wodId, ScalingLevel scaling, Pageable pageable);

  // -------------------------------------------------------------------------
  // LEADERBOARD / RANKING QUERIES
  // -------------------------------------------------------------------------

  /**
   * Counts the total number of Personal Records (PRs) for a given WOD and scaling.
   *
   * @param wodId The WOD ID.
   * @param scaling The scaling level.
   * @return The count of PRs.
   */
  @Query(
      """
      SELECT COUNT(s) FROM WodScore s
      WHERE s.wod.id = :wodId
      AND s.scaling = :scaling
      AND s.personalRecord = true
      """)
  long countByWodIdAndScaling(Long wodId, ScalingLevel scaling);

  /** Counts how many PRs have a better (lower) time. */
  @Query(
      """
      SELECT COUNT(s) FROM WodScore s
      WHERE s.wod.id = :wodId
      AND s.scaling = :scaling
      AND s.personalRecord = true
      AND s.timeSeconds < :time
      """)
  long countBetterTime(Long wodId, ScalingLevel scaling, Integer time);

  /** Counts how many PRs have a better (higher) score in Rounds + Reps. */
  @Query(
      """
      SELECT COUNT(s) FROM WodScore s
      WHERE s.wod.id = :wodId
      AND s.scaling = :scaling
      AND s.personalRecord = true
      AND (s.rounds > :rounds OR (s.rounds = :rounds AND s.reps > :reps))
      """)
  long countBetterRoundsReps(Long wodId, ScalingLevel scaling, Integer rounds, Integer reps);

  /** Counts how many PRs have more reps. */
  @Query(
      """
      SELECT COUNT(s) FROM WodScore s
      WHERE s.wod.id = :wodId
      AND s.scaling = :scaling
      AND s.personalRecord = true
      AND s.reps > :reps
      """)
  long countBetterReps(Long wodId, ScalingLevel scaling, Integer reps);

  /** Counts how many PRs have a heavier weight. */
  @Query(
      """
      SELECT COUNT(s) FROM WodScore s
      WHERE s.wod.id = :wodId
      AND s.scaling = :scaling
      AND s.personalRecord = true
      AND s.maxWeightKg > :weight
      """)
  long countBetterWeight(Long wodId, ScalingLevel scaling, Double weight);

  /** Counts how many PRs have a higher total load. */
  @Query(
      """
      SELECT COUNT(s) FROM WodScore s
      WHERE s.wod.id = :wodId
      AND s.scaling = :scaling
      AND s.personalRecord = true
      AND s.totalLoadKg > :load
      """)
  long countBetterLoad(Long wodId, ScalingLevel scaling, Double load);

  /** Counts how many PRs have a greater distance. */
  @Query(
      """
      SELECT COUNT(s) FROM WodScore s
      WHERE s.wod.id = :wodId
      AND s.scaling = :scaling
      AND s.personalRecord = true
      AND s.totalDistanceMeters > :distance
      """)
  long countBetterDistance(Long wodId, ScalingLevel scaling, Double distance);

  /** Counts how many PRs have more calories. */
  @Query(
      """
      SELECT COUNT(s) FROM WodScore s
      WHERE s.wod.id = :wodId
      AND s.scaling = :scaling
      AND s.personalRecord = true
      AND s.totalCalories > :calories
      """)
  long countBetterCalories(Long wodId, ScalingLevel scaling, Integer calories);
}
