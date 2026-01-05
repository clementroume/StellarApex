package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  Page<WodScore> findByUserId(Long userId, Pageable pageable);

  /**
   * Retrieves the score history for a specific user on a specific WOD.
   *
   * @param userId The ID of the user.
   * @param wodId The ID of the WOD.
   * @param pageable Pagination info.
   * @return A list of scores ordered by date.
   */
  @EntityGraph(attributePaths = {"wod"})
  Page<WodScore> findByUserIdAndWodId(Long userId, Long wodId, Pageable pageable);

  /**
   * Retrieves all scores for a user on a WOD (unordered). Used for PR recalculation.
   *
   * @param wodId The ID of the WOD.
   * @param userId The ID of the user.
   * @return List of scores.
   */
  List<WodScore> findByWodIdAndUserId(Long wodId, Long userId);

  /**
   * Finds the existing Personal Record (PR) for a specific user on a specific WOD.
   *
   * <p>Used by the Service to compare a new score against the previous best.
   *
   * @param wodId The ID of the WOD.
   * @param userId The ID of the athlete.
   * @return An Optional containing the PR score if it exists.
   */
  @EntityGraph(attributePaths = {"wod"})
  Optional<WodScore> findByWodIdAndUserIdAndPersonalRecordTrue(Long wodId, Long userId);

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
  Page<WodScore> findByWodIdAndScalingAndPersonalRecordTrue(
      Long wodId, ScalingLevel scaling, Pageable pageable);

  // -------------------------------------------------------------------------
  // LEADERBOARD / RANKING QUERIES
  // -------------------------------------------------------------------------

  long countByWodIdAndScaling(Long wodId, ScalingLevel scaling);

  @Query(
      "SELECT COUNT(s) FROM WodScore s WHERE s.wod.id = :wodId AND s.scaling = :scaling AND s.timeSeconds < :time")
  long countBetterTime(Long wodId, ScalingLevel scaling, Integer time);

  @Query(
      "SELECT COUNT(s) FROM WodScore s WHERE s.wod.id = :wodId AND s.scaling = :scaling AND (s.rounds > :rounds OR (s.rounds = :rounds AND s.reps > :reps))")
  long countBetterRoundsReps(Long wodId, ScalingLevel scaling, Integer rounds, Integer reps);

  @Query(
      "SELECT COUNT(s) FROM WodScore s WHERE s.wod.id = :wodId AND s.scaling = :scaling AND s.reps > :reps")
  long countBetterReps(Long wodId, ScalingLevel scaling, Integer reps);

  @Query(
      "SELECT COUNT(s) FROM WodScore s WHERE s.wod.id = :wodId AND s.scaling = :scaling AND s.maxWeightKg > :weight")
  long countBetterWeight(Long wodId, ScalingLevel scaling, Double weight);

  @Query(
      "SELECT COUNT(s) FROM WodScore s WHERE s.wod.id = :wodId AND s.scaling = :scaling AND s.totalLoadKg > :load")
  long countBetterLoad(Long wodId, ScalingLevel scaling, Double load);

  @Query(
      "SELECT COUNT(s) FROM WodScore s WHERE s.wod.id = :wodId AND s.scaling = :scaling AND s.totalDistanceMeters > :distance")
  long countBetterDistance(Long wodId, ScalingLevel scaling, Double distance);

  @Query(
      "SELECT COUNT(s) FROM WodScore s WHERE s.wod.id = :wodId AND s.scaling = :scaling AND s.totalCalories > :calories")
  long countBetterCalories(Long wodId, ScalingLevel scaling, Integer calories);
}
