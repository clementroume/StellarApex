package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.enums.AgeGroup;
import apex.stellar.aldebaran.model.enums.Division;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
   * @param ageGroup The age group to keep, or null for every score (unclassified ones included).
   * @param sessionId The session whose journals the scores belong to, or {@link #NO_SESSION}.
   * @return The scores of the WOD kept by the filters.
   */
  @Query(
      "SELECT s FROM Score s WHERE s.wod.id = :wodId"
          + " AND (:filterGyms = false OR s.gymId IN :gymIds)"
          + " AND (:verifiedOnly = false OR s.verifiedAt IS NOT NULL)"
          + " AND (:division IS NULL OR s.division = :division)"
          + " AND (:ageGroup IS NULL OR s.ageGroup = :ageGroup)"
          + " AND (:sessionId = -1 OR s.sessionLogId IN"
          + " (SELECT l.id FROM SessionLog l WHERE l.session.id = :sessionId))")
  List<Score> findFilteredByWod(
      Long wodId,
      boolean filterGyms,
      Collection<Long> gymIds,
      boolean verifiedOnly,
      Division division,
      AgeGroup ageGroup,
      long sessionId);

  /** Session filter value meaning "every score" (PostgreSQL cannot type a null parameter here). */
  long NO_SESSION = -1L;

  /**
   * Retrieves a user's scores on a WOD logged in their journal of a session.
   *
   * @param userId The athlete.
   * @param wodId The WOD.
   * @param sessionId The session.
   * @param pageable Pagination and sorting.
   * @return The scores.
   */
  @Query(
      "SELECT s FROM Score s WHERE s.userId = :userId AND s.wod.id = :wodId AND s.sessionLogId IN"
          + " (SELECT l.id FROM SessionLog l WHERE l.session.id = :sessionId)")
  Slice<Score> findByUserIdAndWodIdInSession(
      Long userId, Long wodId, Long sessionId, Pageable pageable);

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
   * Gives an age group to the athlete's own scores logged before they gave a birth year, each with
   * the group of its own year (D55). Team scores keep none.
   *
   * @param userId The athlete.
   * @param birthYear The athlete's birth year (used here, never stored).
   * @return The number of scores updated.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "UPDATE {h-schema}scores SET age_group = CASE"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 15 THEN 'AGE_15_AND_UNDER'"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 17 THEN 'AGE_16_17'"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 34 THEN 'AGE_18_34'"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 39 THEN 'AGE_35_39'"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 44 THEN 'AGE_40_44'"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 49 THEN 'AGE_45_49'"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 54 THEN 'AGE_50_54'"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 59 THEN 'AGE_55_59'"
              + " WHEN EXTRACT(YEAR FROM date) - :birthYear <= 64 THEN 'AGE_60_64'"
              + " ELSE 'AGE_65_PLUS' END"
              + " WHERE user_id = :userId AND age_group IS NULL AND team_id IS NULL"
              + " AND EXTRACT(YEAR FROM date) >= :birthYear",
      nativeQuery = true)
  int assignAgeGroupToUnclassified(Long userId, int birthYear);

  /**
   * Retrieves an athlete's individual scores on WODs holding a movement, with their sets: the
   * candidates of the movement's records ({@code MovementRecordService} keeps the eligible ones).
   *
   * @param userId The athlete.
   * @param movementId The movement.
   * @param types The score types that may carry a load ({@code WEIGHT}, {@code LOAD}, {@code NONE}).
   * @return The scores.
   */
  @EntityGraph(attributePaths = {"sets", "wod"})
  @Query(
      "SELECT DISTINCT s FROM Score s JOIN s.wod w JOIN w.movements wm"
          + " WHERE s.userId = :userId AND wm.movement.id = :movementId AND s.team IS NULL"
          + " AND w.scoreType IN :types")
  List<Score> findRecordCandidates(Long userId, Long movementId, Collection<ScoreType> types);

  /**
   * Lists the (athlete, movement) pairs that may hold records, for the one-off rebuild of {@code
   * movement_records}.
   *
   * @param types The score types that may carry a load.
   * @return Pairs {@code [userId, movementId]}.
   */
  @Query(
      "SELECT DISTINCT s.userId, wm.movement.id FROM Score s JOIN s.wod w JOIN w.movements wm"
          + " WHERE s.team IS NULL AND w.scoreType IN :types")
  List<Object[]> findRecordOwners(Collection<ScoreType> types);

  /**
   * Retrieves every Personal Record for a WOD and scaling level, for rank comparison (the ordering
   * lives in {@code ScoreRanking}, shared with PRs and leaderboards).
   *
   * @param wodId The WOD ID.
   * @param scaling The scaling level.
   * @return The PR scores of every athlete at that level.
   */
  List<Score> findByWodIdAndScalingAndPersonalRecordTrue(Long wodId, ScalingLevel scaling);

  /**
   * Blocks of a session holding scores (scores attached to a journal of that session for the
   * block; a borrowed WOD's scores logged elsewhere do not count).
   *
   * @param sessionId The session.
   * @return The distinct block IDs.
   */
  @Query(
      "SELECT DISTINCT s.sessionBlockId FROM Score s WHERE s.sessionBlockId IN"
          + " (SELECT b.id FROM SessionBlock b WHERE b.session.id = :sessionId)")
  List<Long> findScoredBlockIdsInSession(@Param("sessionId") Long sessionId);

  /**
   * Checks whether scores are linked to the session journal.
   *
   * @param sessionLogId The journal ID.
   * @return true if at least one score is linked.
   */
  boolean existsBySessionLogId(Long sessionLogId);

  /**
   * Retrieves the scores linked to the given session journals.
   *
   * @param sessionLogIds The journal IDs.
   * @return The scores, with their WOD.
   */
  @EntityGraph(attributePaths = {"wod"})
  List<Score> findBySessionLogIdIn(Collection<Long> sessionLogIds);

  /**
   * Scores of a competition WOD submitted for verification and not verified yet (judge queue),
   * oldest first.
   *
   * @param wodId The WOD ID.
   * @return The scores waiting for a judge.
   */
  @EntityGraph(attributePaths = {"wod"})
  @Query(
      "SELECT s FROM Score s WHERE s.wod.id = :wodId AND s.verificationRequested = true"
          + " AND s.verifiedAt IS NULL ORDER BY s.loggedAt, s.id")
  List<Score> findVerificationQueue(Long wodId);

  /**
   * Scores of a team WOD team (one per member).
   *
   * @param teamId The team.
   * @return The members' scores.
   */
  @Query("SELECT s FROM Score s WHERE s.team.id = :teamId")
  List<Score> findByTeamId(@Param("teamId") Long teamId);
}
