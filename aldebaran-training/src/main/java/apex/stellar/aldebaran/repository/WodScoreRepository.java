package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.WodScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
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
   * <p><b>Optimization:</b> Uses {@link EntityGraph} to eagerly load the associated {@code Wod}
   * entity. This prevents the N+1 select problem when mapping the list to response DTOs that
   * include WOD summaries.
   *
   * @param userId The ID of the user.
   * @return A list of the user's scores with WOD details loaded.
   */
  @EntityGraph(attributePaths = {"wod"})
  List<WodScore> findByUserIdOrderByDateDesc(String userId);

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
  Optional<WodScore> findByWodIdAndUserIdAndPersonalRecordTrue(Long wodId, String userId);
}
