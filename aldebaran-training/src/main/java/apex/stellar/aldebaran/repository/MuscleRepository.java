package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Muscle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Muscle} entities.
 *
 * <p>Handles data access for the anatomical reference table. Caching is now delegated to the
 * Service layer to store DTOs instead of managed Entities.
 */
@Repository
public interface MuscleRepository extends JpaRepository<Muscle, Long> {

  /**
   * Checks if a muscle with the specified medical name exists in the data store.
   *
   * @param medicalName The Latin/Medical name of the muscle to be checked.
   * @return {@code true} if a muscle with the given medical name exists, {@code false} otherwise.
   */
  boolean existsByMedicalNameIgnoreCase(String medicalName);
}
