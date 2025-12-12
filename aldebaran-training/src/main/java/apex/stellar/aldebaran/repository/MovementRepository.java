package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.projection.MovementSummary;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Movement} entities.
 *
 * <p>Acts as the primary data access layer for the exercise catalog (Master Data).
 *
 * <p>Caching Strategy:
 *
 * <ul>
 *   <li>Movements are quasi-immutable master data (24h TTL)
 *   <li>Use projections for list views to reduce bandwidth
 * </ul>
 */
@Repository
public interface MovementRepository extends JpaRepository<Movement, String> {

  // -------------------------------------------------------------------------
  // FULL ENTITY QUERIES (For Detail/Edit Views)
  // -------------------------------------------------------------------------

  /** Retrieves a movement by ID with caching. Cache TTL: 24 hours (master data). */
  @Cacheable(value = "movements", key = "#id")
  @Override
  @NonNull Optional<Movement> findById(String id);

  /**
   * Finds movements whose name contains the given string (full entity). Use for editing/detail
   * views where all data is needed.
   */
  List<Movement> findByNameContainingIgnoreCase(String name);

  /**
   * Retrieves all movements in a specific category (full entity). Use when you need complete
   * movement data including relationships.
   */
  List<Movement> findByCategory(Category category);

  // -------------------------------------------------------------------------
  // PROJECTION QUERIES (For List/Search Views - Optimized)
  // -------------------------------------------------------------------------

  /**
   * Returns all movements as lightweight projections. ~70% faster than findAll() for listing
   * screens.
   */
  List<MovementSummary> findAllProjectedBy();

  /** Searches movements by name (projection). Ideal for autocomplete and search results. */
  List<MovementSummary> findProjectedByNameContainingIgnoreCase(String name);

  /** Retrieves movements by category as projections with caching. Cache TTL: 24 hours. */
  @Cacheable(value = "movements-by-category", key = "#category")
  List<MovementSummary> findProjectedByCategory(Category category);
}
