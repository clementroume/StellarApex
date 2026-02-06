package apex.stellar.antares.repository.jpa;

import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for managing {@link Gym} entities.
 *
 * <p>Provides standard CRUD operations and custom queries for gym management. <br>
 * <b>Security Note:</b> Searching by enrollment code is intentionally omitted to prevent
 * brute-force scanning. Users must identify the gym by ID or Name before providing the code.
 */
@Repository
public interface GymRepository extends JpaRepository<Gym, Long> {

  /**
   * Checks if a gym with the specified name already exists.
   *
   * <p>Used primarily during gym creation to enforce uniqueness constraints efficiently.
   *
   * @param name the unique name of the gym.
   * @return {@code true} if the name exists, {@code false} otherwise.
   */
  boolean existsByName(String name);

  /**
   * Retrieves a gym by its unique name.
   *
   * @param name the unique name of the gym.
   * @return an {@link Optional} containing the gym if found.
   */
  Optional<Gym> findByName(String name);

  /**
   * Retrieves a list of gyms filtered by their lifecycle status.
   *
   * @param status the {@link GymStatus} to filter by (e.g., ACTIVE, PENDING_APPROVAL).
   * @return a list of matching gyms.
   */
  List<Gym> findByStatus(GymStatus status);
}
