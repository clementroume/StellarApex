package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import java.util.List;
import java.util.Optional;
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
   * Retrieves a muscle by its unique medical name.
   *
   * @param medicalName The Latin/Medical name of the muscle.
   * @return An {@link Optional} containing the muscle if found.
   */
  Optional<Muscle> findByMedicalName(String medicalName);

  /**
   * Retrieves all muscles belonging to a specific anatomical group.
   *
   * @param muscleGroup The target muscle group (e.g., CHEST, LEGS).
   * @return A list of matching muscles.
   */
  List<Muscle> findByMuscleGroup(MuscleGroup muscleGroup);
}
