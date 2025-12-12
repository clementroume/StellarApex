package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.WodScore;
import java.time.LocalDate;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
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
  List<WodScore> findByUserIdOrderByDateDesc(String userId);

  /**
   * Retrieves all scores for a specific WOD definition. Used to build leaderboards.
   *
   * <p>Cached with a short TTL (5 min) via "wod-scores" config to reduce database load on heavy
   * workouts (e.g. "Murph").
   */
  @Cacheable(value = "wod-scores", key = "#wodId")
  List<WodScore> findByWodId(Long wodId);

  /**
   * Retrieves all scores flagged as Personal Records (PR) for a user.
   *
   * @param userId The ID of the user.
   * @return A list of the user's PR performances.
   */
  List<WodScore> findByUserIdAndPersonalRecordTrue(String userId);

  /**
   * Retrieves scores for a specific user on a specific date.
   *
   * @param userId The ID of the user.
   * @param date The date of the workout.
   * @return A list of scores found.
   */
  List<WodScore> findByUserIdAndDate(String userId, LocalDate date);
}
