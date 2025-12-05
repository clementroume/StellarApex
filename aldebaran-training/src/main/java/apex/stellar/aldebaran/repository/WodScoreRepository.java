package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.WodScore;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
   * @param userId The ID of the user.
   * @return A list of the user's scores.
   */
  List<WodScore> findByUserIdOrderByDateDesc(Long userId);

  /**
   * Retrieves all scores for a specific WOD definition. Used to build leaderboards.
   *
   * @param wodId The ID of the WOD definition.
   * @return A list of scores for this WOD.
   */
  List<WodScore> findByWodId(Long wodId);

  /**
   * Retrieves all scores flagged as Personal Records (PR) for a user.
   *
   * @param userId The ID of the user.
   * @return A list of the user's PR performances.
   */
  List<WodScore> findByUserIdAndPersonalRecordTrue(Long userId);

  /**
   * Retrieves scores for a specific user on a specific date.
   *
   * @param userId The ID of the user.
   * @param date The date of the workout.
   * @return A list of scores found.
   */
  List<WodScore> findByUserIdAndDate(Long userId, LocalDate date);
}
