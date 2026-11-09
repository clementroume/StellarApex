package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.MovementRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Materialized per-movement records (lot C). */
@Repository
public interface MovementRecordRepository extends JpaRepository<MovementRecord, Long> {

  /**
   * Serializes the record rebuilds of one athlete until the end of the transaction: two scores
   * written at the same time would otherwise both re-insert the same record (unique key violation,
   * the second score lost).
   *
   * @param userId The athlete.
   */
  @Query(value = "SELECT pg_advisory_xact_lock(:userId)", nativeQuery = true)
  Object lockAthlete(Long userId);

  /**
   * Removes an athlete's records of a movement, before they are rebuilt.
   *
   * @param userId The athlete.
   * @param movementId The movement.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = false)
  @Query("DELETE FROM MovementRecord r WHERE r.userId = :userId AND r.movement.id = :movementId")
  void deleteByUserAndMovement(Long userId, Long movementId);

  /**
   * An athlete's records, with their movement, score and WOD.
   *
   * @param userId The athlete.
   * @return The records.
   */
  @Query(
      "SELECT r FROM MovementRecord r JOIN FETCH r.movement JOIN FETCH r.score s JOIN FETCH s.wod"
          + " WHERE r.userId = :userId")
  List<MovementRecord> findByUserId(Long userId);

  /**
   * An athlete's records of one movement, with their movement, score and WOD.
   *
   * @param userId The athlete.
   * @param movementId The movement.
   * @return The records.
   */
  @Query(
      "SELECT r FROM MovementRecord r JOIN FETCH r.movement JOIN FETCH r.score s JOIN FETCH s.wod"
          + " WHERE r.userId = :userId AND r.movement.id = :movementId")
  List<MovementRecord> findByUserIdAndMovementId(Long userId, Long movementId);

  /**
   * The records of some athletes on some movements (records board of a box).
   *
   * @param movementIds The movements.
   * @param userIds The athletes (never empty).
   * @return The records.
   */
  @Query(
      "SELECT r FROM MovementRecord r JOIN FETCH r.movement"
          + " WHERE r.movement.id IN :movementIds AND r.userId IN :userIds")
  List<MovementRecord> findForBoard(Collection<Long> movementIds, Collection<Long> userIds);
}
