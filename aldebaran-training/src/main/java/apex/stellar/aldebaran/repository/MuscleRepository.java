package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment;
import apex.stellar.aldebaran.model.entities.Muscle;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
   * Retrieves all muscles with their functional group links loaded in one query.
   *
   * @return Every muscle, with {@code parent} and {@code members} initialized.
   */
  @Override
  @EntityGraph(attributePaths = {"parent", "members"})
  List<Muscle> findAll();

  /**
   * Checks if a muscle with the specified medical name exists in the data store.
   *
   * @param medicalName The Latin/Medical name of the muscle to be checked.
   * @return {@code true} if a muscle with the given medical name exists, {@code false} otherwise.
   */
  boolean existsByMedicalNameIgnoreCase(String medicalName);

  /**
   * Checks if a muscle already holds this code.
   *
   * @param code The stable key of a muscle.
   * @return {@code true} if the code is taken.
   */
  boolean existsByCode(String code);

  /**
   * Links between some muscles and the movements that list them.
   *
   * @param muscleIds The muscles.
   * @return The links, with their movement.
   */
  @Query("SELECT mm FROM MovementMuscle mm JOIN FETCH mm.movement WHERE mm.muscle.id IN :muscleIds")
  List<MovementMuscle> findMovementUses(Collection<Long> muscleIds);

  /**
   * Role adjustments on some muscles.
   *
   * @param muscleIds The muscles.
   * @return The adjustments, with their movement.
   */
  @Query("SELECT a FROM MovementRoleAdjustment a JOIN FETCH a.movement WHERE a.muscle.id IN :muscleIds")
  List<MovementRoleAdjustment> findAdjustmentUses(Collection<Long> muscleIds);
}
