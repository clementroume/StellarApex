package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MUSCLES;

import apex.stellar.aldebaran.dto.MuscleRequest;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.MuscleMapper;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.repository.MuscleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class that handles business logic related to muscles.
 *
 * <p>Provides functionalities to manage, retrieve, and update muscle records in
 * a catalog. Supports filtering by anatomical groups and caching mechanisms for
 * performance optimization.
 *
 * <p>The service uses {@link MuscleRepository} for data access and {@link MuscleMapper}
 * for mapping between entity and response/request objects. Caching is implemented
 * using keys based on the type of operation to ensure data consistency and efficiency.
 *
 * <p>Transactions are managed at the method level to ensure atomic operations and
 * consistency within the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MuscleService {

  private final MuscleRepository muscleRepository;
  private final MuscleMapper muscleMapper;

  /**
   * Retrieves all muscles available in the catalog.
   *
   * <p>Results are cached under the "muscles" key to reduce the database load.
   *
   * @return A list of {@link MuscleResponse} objects representing all muscles.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = CACHE_MUSCLES, key = "'all'")
  public List<MuscleResponse> getAllMuscles() {

    return muscleRepository.findAll().stream().map(muscleMapper::toResponse).toList();
  }

  /**
   * Retrieves all muscles belonging to a specific anatomical group.
   *
   * @param group The anatomical group to filter by (e.g., LEGS, CHEST).
   * @return A filtered list of {@link MuscleResponse} objects.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = CACHE_MUSCLES, key = "'group-' + #group.name()")
  public List<MuscleResponse> getMusclesByGroup(MuscleGroup group) {

    return muscleRepository.findByMuscleGroup(group).stream()
        .map(muscleMapper::toResponse)
        .toList();
  }

  /**
   * Retrieves a specific muscle by its unique medical name (Business Key).
   *
   * <p>The result is cached individually using the name as the key.
   *
   * @param medicalName The unique Latin/Medical name of the muscle.
   * @return The {@link MuscleResponse} details.
   * @throws ResourceNotFoundException if the muscle does not exist.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = CACHE_MUSCLES, key = "#medicalName")
  public MuscleResponse getMuscle(String medicalName) {

    return muscleRepository
        .findByMedicalName(medicalName)
        .map(muscleMapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("error.muscle.not.found", medicalName));
  }

  /**
   * Creates a new muscle entry in the catalog.
   *
   * <p>Enforces unique constraints on the medical name. Evicts all muscle-related caches ("all",
   * specific groups, and individual keys) to ensure data consistency.
   *
   * @param request The {@link MuscleRequest} containing the details of the muscle to create.
   * @return The created {@link MuscleResponse}.
   * @throws DataConflictException if a muscle with the same medical name already exists.
   */
  @Transactional
  @CacheEvict(value = CACHE_MUSCLES, allEntries = true)
  public MuscleResponse createMuscle(MuscleRequest request) {

    if (muscleRepository.existsByMedicalNameIgnoreCase(request.medicalName())) {
      throw new DataConflictException("error.muscle.name.exists", request.medicalName());
    }

    Muscle muscle = muscleMapper.toEntity(request);
    Muscle savedMuscle = muscleRepository.save(muscle);
    log.info("Created new muscle: {} (ID: {})", savedMuscle.getMedicalName(), savedMuscle.getId());

    return muscleMapper.toResponse(savedMuscle);
  }

  /**
   * Updates an existing muscle entry.
   *
   * <p>Evicts the entire "muscles" cache to reflect changes immediately across all views. The ID is
   * used here to allow renaming the medical name (Business Key) if necessary.
   *
   * @param id The unique identifier of the muscle to update.
   * @param request The {@link MuscleRequest} containing the updated data.
   * @return The updated {@link MuscleResponse}.
   * @throws ResourceNotFoundException if no muscle is found with the provided ID.
   * @throws DataConflictException if the new medical name conflicts with another existing muscle.
   */
  @Transactional
  @CacheEvict(value = CACHE_MUSCLES, allEntries = true)
  public MuscleResponse updateMuscle(Long id, MuscleRequest request) {

    Muscle muscle =
        muscleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.muscle.not.found", id));

    if (!muscle.getMedicalName().equalsIgnoreCase(request.medicalName())
        && muscleRepository.existsByMedicalNameIgnoreCase(request.medicalName())) {
      throw new DataConflictException("error.muscle.name.exists", request.medicalName());
    }

    muscleMapper.updateEntity(request, muscle);
    Muscle savedMuscle = muscleRepository.save(muscle);
    log.info("Updated muscle: {} (ID: {})", savedMuscle.getMedicalName(), savedMuscle.getId());

    return muscleMapper.toResponse(savedMuscle);
  }
}
