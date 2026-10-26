package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Score} entities.
 *
 * <p>Handles data access for athlete logs and workout results.
 */
@Repository
public interface ScoreRepository extends JpaRepository<Score, Long> {

  /**
   * Retrieves the score history for a specific user, ordered by date (the newest first).
   *
   * <p><b>Optimization:</b> Uses {@link EntityGraph} to eagerly load the associated {@code Wod}
   * entity. This prevents the N+1 select problem when mapping the list to response DTOs that
   * include WOD summaries.
   *
   * @param userId The ID of the user.
   * @return A list of the user's scores with WOD details loaded.
   */
  @EntityGraph(attributePaths = {"wod"})
  Slice<Score> findByUserId(Long userId, Pageable pageable);

  /**
   * Retrieves the score history for a specific user on a specific WOD.
   *
   * @param userId The ID of the user.
   * @param wodId The ID of the WOD.
   * @param pageable Pagination info.
   * @return A list of scores ordered by date.
   */
  @EntityGraph(attributePaths = {"wod"})
  Slice<Score> findByUserIdAndWodId(Long userId, Long wodId, Pageable pageable);

  /**
   * Retrieves all scores for a user on a WOD (unordered). Used for PR recalculation.
   *
   * @param wodId The ID of the WOD.
   * @param userId The ID of the user.
   * @return List of scores.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Score s WHERE s.wod.id = :wodId AND s.userId = :userId")
  List<Score> findByWodIdAndUserId(Long wodId, Long userId);

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
  Slice<Score> findByWodIdAndScalingAndPersonalRecordTrue(
      Long wodId, ScalingLevel scaling, Pageable pageable);

  /**
   * Retrieves every Personal Record for a WOD and scaling level, for rank comparison (the ordering
   * lives in {@code ScoreRanking}, shared with PRs and leaderboards).
   *
   * @param wodId The WOD ID.
   * @param scaling The scaling level.
   * @return The PR scores of every athlete at that level.
   */
  List<Score> findByWodIdAndScalingAndPersonalRecordTrue(Long wodId, ScalingLevel scaling);
}
