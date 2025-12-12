package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Muscle;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Muscle} entities.
 *
 * <p>Handles data access for the anatomical reference table.
 */
@Repository
public interface MuscleRepository extends JpaRepository<Muscle, Long> {

  /**
   * Retrieves a muscle by its unique medical name. This serves as the business key lookup.
   *
   * @param medicalName The Latin/Medical name of the muscle (e.g., "Pectoralis Major").
   * @return An {@link Optional} containing the muscle if found.
   */
  @Cacheable(value = "muscles", key = "#medicalName")
  Optional<Muscle> findByMedicalName(String medicalName);

  @Override
  @Cacheable(value = "muscles", key = "'all'")
  List<Muscle> findAll();
}
