package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.MuscleRequest;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.MuscleMapper;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.repository.MuscleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class responsible for managing the anatomical muscle catalog.
 *
 * <p>This service handles the lifecycle of {@link Muscle} entities, including creation, updates,
 * and retrieval. Since muscle data is reference data that changes rarely, read operations are
 * heavily cached to optimize performance.
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
   * <p>This method leverages the "muscles" cache (`key = 'all'`) defined in the repository
   * interface to avoid unnecessary database calls.
   *
   * @return A list of {@link MuscleResponse} objects representing all muscles.
   */
  @Transactional(readOnly = true)
  public List<MuscleResponse> getAllMuscles() {
    return muscleRepository.findAll().stream().map(muscleMapper::toResponse).toList();
  }

  /**
   * Creates a new muscle entry in the catalog.
   *
   * <p>This operation enforces a uniqueness check on the medical name. Upon success, it evicts all
   * entries in the "muscles" cache to ensure data consistency.
   *
   * @param request The {@link MuscleRequest} containing the details of the muscle to create.
   * @return The created {@link MuscleResponse}.
   * @throws DataConflictException if a muscle with the same medical name already exists.
   */
  @Transactional
  @CacheEvict(value = "muscles", allEntries = true)
  public MuscleResponse createMuscle(MuscleRequest request) {
    if (muscleRepository.findByMedicalName(request.medicalName()).isPresent()) {
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
   * <p>Similar to creation, this operation evicts the cache to reflect changes immediately.
   *
   * @param id The unique identifier of the muscle to update.
   * @param request The {@link MuscleRequest} containing the updated data.
   * @return The updated {@link MuscleResponse}.
   * @throws ResourceNotFoundException if no muscle is found with the provided ID.
   */
  @Transactional
  @CacheEvict(value = "muscles", allEntries = true)
  public MuscleResponse updateMuscle(Long id, MuscleRequest request) {
    Muscle muscle =
        muscleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.muscle.not.found", id));

    // Check for unique name conflict if the name is being changed
    if (!muscle.getMedicalName().equals(request.medicalName())
        && muscleRepository.findByMedicalName(request.medicalName()).isPresent()) {
      throw new DataConflictException("error.muscle.name.exists", request.medicalName());
    }

    muscleMapper.updateEntity(request, muscle);
    Muscle savedMuscle = muscleRepository.save(muscle);

    log.info("Updated muscle: {} (ID: {})", savedMuscle.getMedicalName(), savedMuscle.getId());
    return muscleMapper.toResponse(savedMuscle);
  }
}
