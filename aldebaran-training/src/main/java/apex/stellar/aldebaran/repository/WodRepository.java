package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
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
   * attributePaths} ensures we join Wod -> WodMovement -> Movement. The variant collections
   * (equipment, techniques) are not part of the graph: joining them with the movements list would
   * duplicate a movement once per variant combination; they are batch-loaded instead
   * ({@code @BatchSize} on {@link apex.stellar.aldebaran.model.entities.WodMovement}).
   *
   * @param id The unique identifier of the WOD.
   * @return An {@link Optional} containing the WOD if found.
   */
  @EntityGraph(
      attributePaths = {"movements", "movements.movement"})
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
      AND (:category IS NULL OR w.category = :category)
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
   * @param wodType WOD format filter, or {@code null}.
   * @param category WOD category filter, or {@code null}.
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
      @Param("category") WodCategory category,
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
      @Param("category") WodCategory category,
      @Param("movementId") Long movementId,
      @Param("scope") String scope,
      @Param("gymId") Long gymId,
      @Param("authorId") Long authorId,
      @Param("userId") Long userId,
      @Param("memberGymIds") Collection<Long> memberGymIds,
      @Param("isAdmin") boolean isAdmin);

  /**
   * Distinct WOD categories among the readable WODs matching the filters (dynamic category
   * filter).
   *
   * @return The WOD categories present (unordered, without null).
   */
  @Query("SELECT DISTINCT w.category FROM Wod w WHERE w.category IS NOT NULL AND " + SECURE_FILTERS)
  List<WodCategory> findSecureCategories(
      @Param("searchPattern") String searchPattern,
      @Param("wodType") WodType wodType,
      @Param("category") WodCategory category,
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
      @Param("category") WodCategory category,
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
      @Param("category") WodCategory category,
      @Param("movementId") Long movementId,
      @Param("scope") String scope,
      @Param("gymId") Long gymId,
      @Param("authorId") Long authorId,
      @Param("userId") Long userId,
      @Param("memberGymIds") Collection<Long> memberGymIds,
      @Param("isAdmin") boolean isAdmin);

  // -------------------------------------------------------------------------
  // CATALOG CONSISTENCY
  // -------------------------------------------------------------------------

  /**
   * Counts the WODs (any visibility) with a movement prescribed with the given equipment. Used to
   * prevent removing a variant from a movement while WODs still use it.
   *
   * @param movementId The catalog movement ID.
   * @param equipment The equipment name (e.g. {@code RINGS}).
   * @return The number of distinct WODs.
   */
  @Query(
      "SELECT COUNT(DISTINCT wm.wod.id) FROM WodMovement wm JOIN wm.equipment e "
          + "WHERE wm.movement.id = :movementId AND e = :equipment")
  long countByMovementEquipment(
      @Param("movementId") Long movementId, @Param("equipment") String equipment);

  /**
   * Counts the WODs (any visibility) with a movement prescribed with the given technique.
   *
   * @param movementId The catalog movement ID.
   * @param technique The technique name (e.g. {@code KIPPING}).
   * @return The number of distinct WODs.
   */
  @Query(
      "SELECT COUNT(DISTINCT wm.wod.id) FROM WodMovement wm JOIN wm.techniques t "
          + "WHERE wm.movement.id = :movementId AND t = :technique")
  long countByMovementTechnique(
      @Param("movementId") Long movementId, @Param("technique") String technique);
}
