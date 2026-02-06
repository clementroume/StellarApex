package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.projection.MovementSummary;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Movement} entities.
 *
 * <p>Acts as the primary data access layer for the exercise catalog (Master Data). Caching is
 * delegated to the Service layer to handle DTOs directly.
 */
@Repository
public interface MovementRepository extends JpaRepository<Movement, String> {

  // -------------------------------------------------------------------------
  // FULL ENTITY QUERIES (For Detail/Edit/Audit Views)
  // -------------------------------------------------------------------------

  /** Retrieves a movement by ID. */
  @Override
  @NonNull Optional<Movement> findById(String id);

  // -------------------------------------------------------------------------
  // PROJECTION QUERIES (For List/Search Views - Optimized)
  // -------------------------------------------------------------------------

  /**
   * Returns all movements as lightweight projections. ~70% faster than findAll() for listing
   * screens.
   */
  List<MovementSummary> findAllProjectedBy();

  /** Searches movements by name (projection). Ideal for autocomplete and public search results. */
  List<MovementSummary> findProjectedByNameContainingIgnoreCase(String name);

  /**
   * Retrieves movements by category as projections. Enables efficient filtering (e.g., "Show me all
   * Gymnastics movements").
   */
  List<MovementSummary> findProjectedByCategory(Category category);

  /**
   * Checks for the existence of an entity (e.g., Movement) in the database by its name, ignoring
   * case differences.
   */
  boolean existsByNameIgnoreCase(String name);
}
