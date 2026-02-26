package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Wod} entities.
 *
 * <p>Provides access to the workout definitions ("The Recipe").
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
  // SECURE PROJECTION QUERIES
  // -------------------------------------------------------------------------

  /**
   * Retrieves all authorized WODs as lightweight projections.
   *
   * @param pageable Pagination information.
   * @return A pageable list of WOD summaries matching the security context.
   */
  @Query(
      """
      SELECT w FROM Wod w
      WHERE (?#{@securityService.isCurrentUserAdmin()} = true
         OR w.isPublic = true
         OR w.authorId = ?#{@securityService.getCurrentUserId()}
         OR (w.gymId IS NOT NULL AND w.gymId = ?#{@securityService.getCurrentUserGymId()}))
      """)
  Slice<WodSummary> findAllSecure(Pageable pageable);

  /**
   * Searches for authorized WODs by title.
   *
   * @param title The title fragment to search for (case-insensitive).
   * @return A list of matching WOD summaries.
   */
  @Query(
      """
      SELECT w FROM Wod w
      WHERE LOWER(w.title) LIKE LOWER(CONCAT('%', :title, '%'))
      AND (?#{@securityService.isCurrentUserAdmin()} = true
         OR w.isPublic = true
         OR w.authorId = ?#{@securityService.getCurrentUserId()}
         OR (w.gymId IS NOT NULL AND w.gymId = ?#{@securityService.getCurrentUserGymId()}))
      """)
  Slice<WodSummary> findByTitleSecure(@Param("title") String title, Pageable pageable);

  /**
   * Retrieves authorized WODs filtered by their structural type.
   *
   * @param wodType The type of WOD (e.g., AMRAP, FOR TIME).
   * @param pageable Pagination information.
   * @return A pageable list of matching WOD summaries.
   */
  @Query(
      """
      SELECT w FROM Wod w
      WHERE w.wodType = :wodType
      AND (?#{@securityService.isCurrentUserAdmin()} = true
         OR w.isPublic = true
         OR w.authorId = ?#{@securityService.getCurrentUserId()}
         OR (w.gymId IS NOT NULL AND w.gymId = ?#{@securityService.getCurrentUserGymId()}))
      """)
  Slice<WodSummary> findByTypeSecure(@Param("wodType") WodType wodType, Pageable pageable);

  /**
   * Retrieves authorized WODs containing a specific movement.
   *
   * @param movementId The ID of the movement (e.g., "GY-PU-001").
   * @param pageable Pagination information.
   * @return A pageable list of matching WOD summaries.
   */
  @Query(
      """
      SELECT w FROM Wod w JOIN w.movements wm
      WHERE wm.movement.id = :movementId
      AND (?#{@securityService.isCurrentUserAdmin()} = true
         OR w.isPublic = true
         OR w.authorId = ?#{@securityService.getCurrentUserId()}
         OR (w.gymId IS NOT NULL AND w.gymId = ?#{@securityService.getCurrentUserGymId()}))
      """)
  Slice<WodSummary> findByMovementSecure(@Param("movementId") String movementId, Pageable pageable);
}
