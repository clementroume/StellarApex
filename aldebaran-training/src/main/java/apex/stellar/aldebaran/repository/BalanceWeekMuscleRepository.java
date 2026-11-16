package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.BalanceWeekMuscle;
import apex.stellar.aldebaran.repository.projection.BalanceMuscleSum;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Sets per muscle of the stored weeks of the balance (lot D). */
@Repository
public interface BalanceWeekMuscleRepository
    extends JpaRepository<BalanceWeekMuscle, BalanceWeekMuscle.Key> {

  /**
   * Removes a week's rows before it is stored again.
   *
   * @param userId The athlete.
   * @param weekStart Its Monday.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM BalanceWeekMuscle b WHERE b.id.userId = :userId AND b.id.weekStart = :weekStart")
  void deleteWeek(Long userId, LocalDate weekStart);

  /**
   * Sets per muscle of an athlete over some weeks, with what the screen names the muscle by.
   *
   * @param userId The athlete.
   * @param from First Monday.
   * @param to Last Monday.
   * @return One row per muscle worked.
   */
  @Query(
      "SELECT m.id AS muscleId, m.code AS code, m.region AS region, m.medicalName AS medicalName,"
          + " m.commonNameEn AS commonNameEn, m.commonNameFr AS commonNameFr,"
          + " SUM(b.movingSets) AS movingSets, SUM(b.holdingSets) AS holdingSets"
          + " FROM BalanceWeekMuscle b JOIN Muscle m ON m.id = b.id.muscleId"
          + " WHERE b.id.userId = :userId AND b.id.weekStart BETWEEN :from AND :to"
          + " GROUP BY m.id, m.code, m.region, m.medicalName, m.commonNameEn, m.commonNameFr")
  List<BalanceMuscleSum> sumByMuscle(Long userId, LocalDate from, LocalDate to);
}
