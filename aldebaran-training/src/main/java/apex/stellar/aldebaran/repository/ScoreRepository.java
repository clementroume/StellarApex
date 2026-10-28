package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.enums.Division;
import org.springframework.data.jpa.repository.Modifying;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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
   * Retrieves the worldwide leaderboard (PRs only) for a specific WOD and scaling level.
   *
   * @param wodId The WOD ID.
   * @param scaling The scaling level (RX, SCALED...).
   * @param pageable Pagination and Sorting (Sort is handled by Service based on WOD type).
   * @return A page of Personal Record scores.
   */
  @Query(
      "SELECT s FROM Score s WHERE s.wod.id = :wodId AND s.scaling = :scaling"
          + " AND s.personalRecord = true")
  Slice<Score> findLeaderboard(Long wodId, ScalingLevel scaling, Pageable pageable);

  /**
   * Retrieves every score of a WOD, at every scaling level, kept by the leaderboard filters: the
   * scores performed in some gyms and / or the verified scores. Used to rank each athlete by their
   * best score among those kept, which is not always their personal record.
   *
   * @param wodId The WOD ID.
   * @param filterGyms Whether to keep the scores performed in {@code gymIds} only.
   * @param gymIds The gyms to keep (never empty, so that the JPQL {@code IN} stays valid).
   * @param verifiedOnly Whether to keep verified scores only.
   * @param division The division to keep, or null for every score (unclassified ones included).
   * @return The scores of the WOD kept by the filters.
   */
  @Query(
      "SELECT s FROM Score s WHERE s.wod.id = :wodId"
          + " AND (:filterGyms = false OR s.gymId IN :gymIds)"
          + " AND (:verifiedOnly = false OR s.verifiedAt IS NOT NULL)"
          + " AND (:division IS NULL OR s.division = :division)")
  List<Score> findFilteredByWod(
      Long wodId,
      boolean filterGyms,
      Collection<Long> gymIds,
      boolean verifiedOnly,
      Division division);

  /**
   * Gives a division to the athlete's scores logged before they chose one.
   *
   * @param userId The athlete.
   * @param division The athlete's division.
   * @return The number of scores updated.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE Score s SET s.division = :division WHERE s.userId = :userId AND s.division IS NULL")
  int assignDivisionToUnclassified(Long userId, Division division);

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
