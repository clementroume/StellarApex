package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.enums.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Movement} entities.
 *
 * <p>Acts as the primary data access layer for the exercise catalog (Master Data).
 */
@Repository
public interface MovementRepository extends JpaRepository<Movement, String> {

  /**
   * Finds movements whose name contains the given string, ignoring case. Useful for autocomplete
   * fields or search bars.
   *
   * @param name The partial name to search for.
   * @return A list of matching movements.
   */
  List<Movement> findByNameContainingIgnoreCase(String name);

  /**
   * Retrieves all movements belonging to a specific functional category.
   *
   * @param category The {@link Category} to filter by (e.g., SQUAT, CLEAN).
   * @return A list of movements in the specified category.
   */
  List<Movement> findByCategory(Category category);
}
