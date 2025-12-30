package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.MovementMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing the exercise catalog (Movements).
 *
 * <p>Handles the lifecycle of {@link Movement} entities, including the generation of semantic
 * business IDs and the orchestration of complex anatomical relationships (Muscle linking).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovementService {

  private final MovementRepository movementRepository;
  private final MuscleRepository muscleRepository;
  private final MovementMapper movementMapper;

  /**
   * Retrieves a lightweight list of all movements for search or autocomplete purposes.
   *
   * @param query The search term to filter movement names (case-insensitive).
   * @return A list of summarized movement data.
   */
  @Transactional(readOnly = true)
  public List<MovementSummaryResponse> searchMovements(String query) {
    return movementRepository.findByNameContainingIgnoreCase(query).stream()
        .map(movementMapper::toSummary)
        .toList();
  }

  /**
   * Retrieves full details of a movement by its business ID.
   *
   * @param id The unique business ID (e.g., "WL-SQ-A1B2").
   * @return The detailed movement response.
   * @throws ResourceNotFoundException if the movement does not exist.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = "movements", key = "#id")
  public MovementResponse getMovement(String id) {
    return movementRepository
        .findById(id)
        .map(movementMapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("error.movement.not.found", id));
  }

  /**
   * Creates a new movement in the catalog.
   *
   * <p>The process involves:
   *
   * <ol>
   *   <li>Mapping the DTO to an entity.
   *   <li>Generating a semantic Business ID based on the category.
   *   <li>Resolving and linking targeted muscles from the database.
   *   <li>Persisting the entity and evicting relevant caches.
   * </ol>
   *
   * @param request The creation payload.
   * @return The created movement with its generated ID.
   */
  @Transactional
  @Caching(
      evict = {
        @CacheEvict(value = "movements", allEntries = true),
        @CacheEvict(value = "movements-by-category", key = "#request.category().name()")
      })
  public MovementResponse createMovement(MovementRequest request) {
    Movement movement = movementMapper.toEntity(request);

    // 1. Generate Business ID (e.g., WL-SQ-XXXX)
    String businessId = generateBusinessId(movement);
    movement.setId(businessId);

    // 2. Link Muscles (Manual relationship management via lookup)
    if (request.muscles() != null) {
      movement.setTargetedMuscles(new HashSet<>());
      for (MovementMuscleRequest mmRequest : request.muscles()) {
        linkMuscleToMovement(movement, mmRequest);
      }
    }

    Movement saved = movementRepository.save(movement);
    log.info("Created new movement: {} ({})", saved.getName(), saved.getId());
    return movementMapper.toResponse(saved);
  }

  /**
   * Updates an existing movement.
   *
   * <p>This method employs a "Full Replacement" strategy for muscle relationships: the existing
   * list is cleared and rebuilt from the request to ensure the state matches exactly.
   *
   * @param id The ID of the movement to update.
   * @param request The update payload.
   * @return The updated movement details.
   * @throws ResourceNotFoundException if the movement ID is invalid.
   */
  @Transactional
  @Caching(
      evict = {
        @CacheEvict(value = "movements", key = "#id"),
        @CacheEvict(value = "movements-by-category", allEntries = true)
      })
  public MovementResponse updateMovement(String id, MovementRequest request) {
    Movement movement =
        movementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.movement.not.found", id));

    movementMapper.updateEntity(request, movement);

    // Re-process muscle links
    if (request.muscles() != null) {
      movement.getTargetedMuscles().clear(); // Remove old links
      for (MovementMuscleRequest mmRequest : request.muscles()) {
        linkMuscleToMovement(movement, mmRequest);
      }
    }

    Movement saved = movementRepository.save(movement);
    log.info("Updated movement: {} ({})", saved.getName(), saved.getId());
    return movementMapper.toResponse(saved);
  }

  // -------------------------------------------------------------------------
  // Helper Methods
  // -------------------------------------------------------------------------

  /**
   * Resolves a muscle by its medical name and links it to the movement.
   *
   * @throws ResourceNotFoundException if the referenced muscle name does not exist in the DB.
   */
  private void linkMuscleToMovement(Movement movement, MovementMuscleRequest req) {
    Muscle muscle =
        muscleRepository
            .findByMedicalName(req.medicalName())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "error.muscle.name.not.found", req.medicalName()));

    MovementMuscle joinEntity = movementMapper.toMuscleEntity(req);
    joinEntity.setMovement(movement);
    joinEntity.setMuscle(muscle);

    movement.getTargetedMuscles().add(joinEntity);
  }

  /**
   * Generates a semantic ID string. Format: {CATEGORY_PREFIX}-{SHORT_UUID} (e.g., "WL-SQ-A1B2").
   */
  private String generateBusinessId(Movement movement) {
    String prefix = movement.getSemanticIdPrefix();
    // Safety check in case category is missing or prefix logic fails
    if (prefix == null) {
      prefix = "GEN";
    }
    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    return String.format("%s-%s", prefix, uniqueSuffix);
  }
}
