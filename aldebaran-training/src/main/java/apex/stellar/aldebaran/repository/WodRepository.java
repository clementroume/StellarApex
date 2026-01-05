package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Wod} entities.
 *
 * <p>Provides access to workout definitions ("The Recipe").
 *
 * <p>Performance optimization strategy:
 *
 * <ul>
 *   <li><b>Full Entities:</b> Use {@link EntityGraph} to eagerly load the {@code movements} and
 *       their nested {@code movement} definitions in a single query, avoiding N+1 issues.
 *   <li><b>Summaries:</b> Use database projections ({@link WodSummary}) for list views and search
 *       operations to minimize bandwidth and memory usage.
 * </ul>
 */
@Repository
public interface WodRepository extends JpaRepository<Wod, Long> {

  // -------------------------------------------------------------------------
  // FULL ENTITY QUERIES (Eager Loading)
  // -------------------------------------------------------------------------

  /**
   * Retrieves a WOD with its movements eagerly loaded.
   *
   * <p>Essential for detailed views or editing where the complete structure is required. The {@code
   * attributePaths} ensures we join Wod -> WodMovement -> Movement.
   *
   * @param id The unique identifier of the WOD.
   * @return An {@link Optional} containing the WOD if found.
   */
  @EntityGraph(attributePaths = {"movements", "movements.movement"})
  @Query("SELECT w FROM Wod w WHERE w.id = :id")
  Optional<Wod> findByIdWithMovements(@Param("id") Long id);

  // -------------------------------------------------------------------------
  // PROJECTION QUERIES (Lightweight)
  // -------------------------------------------------------------------------

  /**
   * Retrieves all WODs as lightweight projections.
   *
   * <p>Optimized for list views where deep nesting is not required.
   *
   * @param pageable Pagination information.
   * @return A list of WOD summaries.
   */
  List<WodSummary> findAllProjectedBy(Pageable pageable);

  /**
   * Searches for WODs by title using lightweight projections.
   *
   * @param title The title fragment to search for (case-insensitive).
   * @return A list of matching WOD summaries.
   */
  List<WodSummary> findProjectedByTitleContainingIgnoreCase(String title);

  /**
   * Retrieves WODs by type using lightweight projections.
   *
   * @param wodType The structural type of the WOD.
   * @param pageable Pagination information.
   * @return A list of matching WOD summaries.
   */
  List<WodSummary> findProjectedByWodType(WodType wodType, Pageable pageable);

  /**
   * Retrieves WODs that include a specific movement.
   *
   * @param movementId The ID of the movement (e.g., "GY-PU-001").
   * @param pageable Pagination information.
   * @return A list of matching WOD summaries.
   */
  @Query("SELECT w FROM Wod w JOIN w.movements wm WHERE wm.movement.id = :movementId")
  List<WodSummary> findProjectedByMovementId(@Param("movementId") String movementId, Pageable pageable);
}
