package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.util.Collection;
import java.util.List;
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
  @EntityGraph(
      attributePaths = {
        "movements",
        "movements.movement",
        "movements.equipment",
        "movements.techniques"
      })
  @Query("SELECT w FROM Wod w WHERE w.id = :id")
  Optional<Wod> findByIdWithMovements(@Param("id") Long id);

  /**
   * Retrieves every public WOD with its movements, ordered by title (temporary CSV export).
   *
   * @return The public WODs.
   */
  @EntityGraph(attributePaths = {"movements", "movements.movement"})
  @Query("SELECT w FROM Wod w WHERE w.isPublic = true ORDER BY w.title, w.id")
  List<Wod> findAllPublicWithMovements();

  /**
   * Checks whether at least one public WOD exists (seeding guard).
   *
   * @return true if a public WOD exists.
   */
  @Query("SELECT COUNT(w) > 0 FROM Wod w WHERE w.isPublic = true")
  boolean existsPublic();

  /**
   * Checks whether a gym already owns WODs (dev seeding guard).
   *
   * @param gymId The gym ID.
   * @return true if the gym has at least one WOD.
   */
  boolean existsByGymId(Long gymId);

  // -------------------------------------------------------------------------
  // SECURE PROJECTION QUERIES
  // -------------------------------------------------------------------------

  /**
   * Shared filters of the secure list and facet queries.
   *
   * <p><b>Security clause:</b> admin OR public OR authored by the user OR owned by one of the
   * user's member gyms.
   *
   * <p><b>Scopes:</b>
   *
   * <ul>
   *   <li>{@code ALL}: everything readable;
   *   <li>{@code MINE}: personal WODs authored by the user (no gym);
   *   <li>{@code GYM}: WODs of the member gyms (admins: any gym), narrowed to {@code gymId} if
   *       given;
   *   <li>{@code PUBLIC}: public WODs.
   * </ul>
   *
   * <p>{@code authorId} narrows to the personal WODs (no gym) of that author. Optional parameters
   * are never wrapped in SQL functions so PostgreSQL can always infer their type when bound as
   * {@code null}; the search pattern is lower-cased by the caller.
   */
  String SECURE_FILTERS =
      """
      (:isAdmin = true
         OR w.isPublic = true
         OR w.authorId = :userId
         OR w.gymId IN :memberGymIds)
      AND (:searchPattern IS NULL OR LOWER(w.title) LIKE :searchPattern)
      AND (:wodType IS NULL OR w.wodType = :wodType)
      AND (:movementId IS NULL OR EXISTS (
            SELECT 1 FROM WodMovement wm WHERE wm.wod = w AND wm.movement.id = :movementId))
      AND (:scope = 'ALL'
         OR (:scope = 'MINE' AND w.authorId = :userId AND w.gymId IS NULL)
         OR (:scope = 'GYM'
             AND (w.gymId IN :memberGymIds OR (:isAdmin = true AND w.gymId IS NOT NULL))
             AND (:gymId IS NULL OR w.gymId = :gymId))
         OR (:scope = 'PUBLIC' AND w.isPublic = true))
      AND (:authorId IS NULL OR (w.authorId = :authorId AND w.gymId IS NULL))
      """;

  /**
   * Retrieves authorized WODs as lightweight projections, with optional combinable filters (see
   * {@link #SECURE_FILTERS}).
   *
   * @param searchPattern Lower-cased {@code LIKE} pattern on the title, or {@code null}.
   * @param wodType WOD type filter, or {@code null}.
   * @param movementId Movement contained in the WOD, or {@code null}.
   * @param scope Scope name (see {@code WodScope}).
   * @param gymId Gym filter for the {@code GYM} scope, or {@code null}.
   * @param authorId Author of personal WODs, or {@code null}.
   * @param userId Current user ID.
   * @param memberGymIds Gyms readable by the user (never empty, use {@code {-1}} as a sentinel).
   * @param isAdmin Whether the user is a platform admin.
   * @param pageable Pagination information.
   * @return A slice of WOD summaries matching the filters and the security context.
   */
  @Query("SELECT w FROM Wod w WHERE " + SECURE_FILTERS)
  Slice<WodSummary> findSecure(
      @Param("searchPattern") String searchPattern,
      @Param("wodType") WodType wodType,
      @Param("movementId") Long movementId,
      @Param("scope") String scope,
      @Param("gymId") Long gymId,
      @Param("authorId") Long authorId,
      @Param("userId") Long userId,
      @Param("memberGymIds") Collection<Long> memberGymIds,
      @Param("isAdmin") boolean isAdmin,
      Pageable pageable);

  /**
   * Distinct WOD types among the readable WODs matching the filters (dynamic type filter).
   *
   * @return The WOD types present (unordered).
   */
  @Query("SELECT DISTINCT w.wodType FROM Wod w WHERE " + SECURE_FILTERS)
  List<WodType> findSecureWodTypes(
      @Param("searchPattern") String searchPattern,
      @Param("wodType") WodType wodType,
      @Param("movementId") Long movementId,
      @Param("scope") String scope,
      @Param("gymId") Long gymId,
      @Param("authorId") Long authorId,
      @Param("userId") Long userId,
      @Param("memberGymIds") Collection<Long> memberGymIds,
      @Param("isAdmin") boolean isAdmin);

  /**
   * Distinct gyms owning readable WODs matching the filters (admin gym selector).
   *
   * @return The gym IDs (unordered).
   */
  @Query("SELECT DISTINCT w.gymId FROM Wod w WHERE w.gymId IS NOT NULL AND " + SECURE_FILTERS)
  List<Long> findSecureGymIds(
      @Param("searchPattern") String searchPattern,
      @Param("wodType") WodType wodType,
      @Param("movementId") Long movementId,
      @Param("scope") String scope,
      @Param("gymId") Long gymId,
      @Param("authorId") Long authorId,
      @Param("userId") Long userId,
      @Param("memberGymIds") Collection<Long> memberGymIds,
      @Param("isAdmin") boolean isAdmin);

  /**
   * Distinct authors of readable personal WODs matching the filters (admin athlete selector).
   *
   * @return The author IDs (unordered).
   */
  @Query(
      "SELECT DISTINCT w.authorId FROM Wod w WHERE w.authorId IS NOT NULL AND w.gymId IS NULL AND "
          + SECURE_FILTERS)
  List<Long> findSecureAuthorIds(
      @Param("searchPattern") String searchPattern,
      @Param("wodType") WodType wodType,
      @Param("movementId") Long movementId,
      @Param("scope") String scope,
      @Param("gymId") Long gymId,
      @Param("authorId") Long authorId,
      @Param("userId") Long userId,
      @Param("memberGymIds") Collection<Long> memberGymIds,
      @Param("isAdmin") boolean isAdmin);
}
