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
  // SECURE PROJECTION QUERIES
  // -------------------------------------------------------------------------

  /**
   * Retrieves all authorized WODs as lightweight projections.
   *
   * @param userId The ID of the authenticated user (for authorship check).
   * @param gymId The Gym ID from the user's current context (nullable).
   * @param isAdmin Boolean flag to bypass security checks for administrators.
   * @param pageable Pagination information.
   * @return A pageable list of WOD summaries matching the security context.
   */
  @Query(
      """
      SELECT w FROM Wod w
      WHERE (:isAdmin = true
         OR w.isPublic = true
         OR w.authorId = :userId
         OR (:gymId IS NOT NULL AND w.gymId = :gymId))
      """)
  List<WodSummary> findAllSecure(
      @Param("userId") Long userId,
      @Param("gymId") Long gymId,
      @Param("isAdmin") boolean isAdmin,
      Pageable pageable);

  /**
   * Searches for authorized WODs by title.
   *
   * @param title The title fragment to search for (case-insensitive).
   * @param userId The ID of the authenticated user.
   * @param gymId The Gym ID from the user's current context.
   * @param isAdmin Boolean flag for admin bypass.
   * @return A list of matching WOD summaries.
   */
  @Query(
      """
      SELECT w FROM Wod w
      WHERE LOWER(w.title) LIKE LOWER(CONCAT('%', :title, '%'))
      AND (:isAdmin = true
         OR w.isPublic = true
         OR w.authorId = :userId
         OR (:gymId IS NOT NULL AND w.gymId = :gymId))
      """)
  List<WodSummary> findByTitleSecure(
      @Param("title") String title,
      @Param("userId") Long userId,
      @Param("gymId") Long gymId,
      @Param("isAdmin") boolean isAdmin);

  /**
   * Retrieves authorized WODs filtered by their structural type.
   *
   * @param wodType The type of WOD (e.g., AMRAP, FORTIME).
   * @param userId The ID of the authenticated user.
   * @param gymId The Gym ID from the user's current context.
   * @param isAdmin Boolean flag for admin bypass.
   * @param pageable Pagination information.
   * @return A pageable list of matching WOD summaries.
   */
  @Query(
      """
      SELECT w FROM Wod w
      WHERE w.wodType = :wodType
      AND (:isAdmin = true
         OR w.isPublic = true
         OR w.authorId = :userId
         OR (:gymId IS NOT NULL AND w.gymId = :gymId))
      """)
  List<WodSummary> findByTypeSecure(
      @Param("wodType") WodType wodType,
      @Param("userId") Long userId,
      @Param("gymId") Long gymId,
      @Param("isAdmin") boolean isAdmin,
      Pageable pageable);

  /**
   * Retrieves authorized WODs containing a specific movement.
   *
   * @param movementId The ID of the movement (e.g., "GY-PU-001").
   * @param userId The ID of the authenticated user.
   * @param gymId The Gym ID from the user's current context.
   * @param isAdmin Boolean flag for admin bypass.
   * @param pageable Pagination information.
   * @return A pageable list of matching WOD summaries.
   */
  @Query(
      """
      SELECT w FROM Wod w JOIN w.movements wm
      WHERE wm.movement.id = :movementId
      AND (:isAdmin = true
         OR w.isPublic = true
         OR w.authorId = :userId
         OR (:gymId IS NOT NULL AND w.gymId = :gymId))
      """)
  List<WodSummary> findByMovementSecure(
      @Param("movementId") String movementId,
      @Param("userId") Long userId,
      @Param("gymId") Long gymId,
      @Param("isAdmin") boolean isAdmin,
      Pageable pageable);
}
