package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.repository.projection.MovementSummary;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link Movement} entities. Provides standard CRUD operations and custom
 * query methods for movement data.
 */
@Repository
public interface MovementRepository extends JpaRepository<Movement, Long> {

  /**
   * Checks if a movement exists with the given name, ignoring case.
   *
   * @param name the name to check
   * @return true if a movement with the name exists, false otherwise
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Retrieves a list of all movements projected as {@link MovementSummary}.
   *
   * @return a list of movement summaries
   */
  List<MovementSummary> findAllProjectedBy();
}
