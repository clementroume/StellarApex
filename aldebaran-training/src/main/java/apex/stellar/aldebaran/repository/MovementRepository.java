package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementUsualName;
import apex.stellar.aldebaran.repository.projection.MovementSummary;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
   * The "either" movements of the catalog, with what they accept.
   *
   * @return The movements.
   */
  @Query("SELECT DISTINCT m FROM Movement m JOIN FETCH m.acceptedMovements")
  List<Movement> findChoices();

  /**
   * Chains whose performances count for a movement's record.
   *
   * @param movementId The component.
   * @return The chains.
   */
  @Query(
      "SELECT DISTINCT c.chain FROM MovementComponent c"
          + " WHERE c.component.id = :movementId AND c.countsForRecords = TRUE")
  List<Movement> findChainsCountingFor(Long movementId);

  /**
   * Every usual name of the catalog, with its movement.
   *
   * @return The usual names, by English name.
   */
  @Query("SELECT u FROM MovementUsualName u JOIN FETCH u.movement ORDER BY u.nameEn")
  List<MovementUsualName> findAllUsualNames();

  /**
   * Retrieves a list of all movements projected as {@link MovementSummary}.
   *
   * @return a list of movement summaries
   */
  List<MovementSummary> findAllProjectedBy();

  /**
   * The steps of every chain.
   *
   * @return Rows of chain id and step name, in step order.
   */
  @Query(
      "SELECT c.chain.id, c.component.name FROM MovementComponent c"
          + " ORDER BY c.chain.id, c.componentOrder")
  List<Object[]> findComponentNames();

  /**
   * The movements every "either" movement accepts.
   *
   * @return Rows of movement id and accepted name, by name.
   */
  @Query("SELECT m.id, a.name FROM Movement m JOIN m.acceptedMovements a ORDER BY m.id, a.name")
  List<Object[]> findAcceptedNames();
}
