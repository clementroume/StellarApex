package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.util.List;
import java.util.Optional;
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
 * <p>Performance Notes:
 *
 * <ul>
 *   <li>Use EntityGraph to avoid N+1 queries when loading movements
 *   <li>Use projections for list views
 * </ul>
 */
@Repository
public interface WodRepository extends JpaRepository<Wod, Long> {

  // -------------------------------------------------------------------------
  // FULL ENTITY QUERIES (With Eager Loading)
  // -------------------------------------------------------------------------

  /**
   * Retrieves a WOD with its movements eagerly loaded (avoids N+1). Use for detail/edit screens
   * where you need the complete structure.
   */
  @EntityGraph(attributePaths = {"movements", "movements.movement"})
  @Query("SELECT w FROM Wod w WHERE w.id = :id")
  Optional<Wod> findByIdWithMovements(@Param("id") Long id);

  /** Retrieves all WODs of a specific type with movements eagerly loaded. */
  @EntityGraph(attributePaths = {"movements", "movements.movement"})
  List<Wod> findByWodType(WodType wodType);

  /** Searches WODs by title with eager loading. Use when you need full WOD data. */
  @EntityGraph(attributePaths = {"movements", "movements.movement"})
  List<Wod> findByTitleContainingIgnoreCase(String title);

  // -------------------------------------------------------------------------
  // PROJECTION QUERIES (For Lists - Optimized)
  // -------------------------------------------------------------------------

  /**
   * Returns all WODs as lightweight projections. Ideal for listing screens (no movements loaded).
   */
  List<WodSummary> findAllProjectedBy();

  /**
   * Searches WODs by title (projection). Faster for search results where movements aren't needed.
   */
  List<WodSummary> findProjectedByTitleContainingIgnoreCase(String title);

  /** Retrieves WODs by type (projection). */
  List<WodSummary> findProjectedByWodType(WodType wodType);
}
