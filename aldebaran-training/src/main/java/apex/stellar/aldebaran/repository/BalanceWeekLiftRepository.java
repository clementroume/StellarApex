package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.BalanceWeekLift;
import apex.stellar.aldebaran.repository.projection.BalanceLiftSum;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Strength sets per lift of the stored weeks of the balance (lot D). */
@Repository
public interface BalanceWeekLiftRepository extends JpaRepository<BalanceWeekLift, BalanceWeekLift.Key> {

  /**
   * Removes a week's rows before it is stored again.
   *
   * @param userId The athlete.
   * @param weekStart Its Monday.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM BalanceWeekLift b WHERE b.id.userId = :userId AND b.id.weekStart = :weekStart")
  void deleteWeek(Long userId, LocalDate weekStart);

  /**
   * Strength sets per lift of an athlete over some weeks, the most worked first.
   *
   * @param userId The athlete.
   * @param from First Monday.
   * @param to Last Monday.
   * @return One row per lift.
   */
  @Query(
      "SELECT b.id.label AS label, MIN(b.movementId) AS movementId, SUM(b.sets) AS sets,"
          + " MAX(CASE WHEN b.nearRecord = true THEN 1 ELSE 0 END) AS nearRecord"
          + " FROM BalanceWeekLift b"
          + " WHERE b.id.userId = :userId AND b.id.weekStart BETWEEN :from AND :to"
          + " GROUP BY b.id.label ORDER BY SUM(b.sets) DESC, b.id.label")
  List<BalanceLiftSum> sumByLift(Long userId, LocalDate from, LocalDate to);
}
