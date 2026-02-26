package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MOVEMENTS;

import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.MovementMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.projection.MovementSummary;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing the exercise catalog (Movements).
 *
 * <p>Handles the lifecycle of {@link Movement} entities, including the generation of semantic
 * business IDs and the orchestration of complex anatomical relationships (Muscle linking).
 *
 * <p>Caching Strategy:
 *
 * <ul>
 *   <li>Read operations (Detail) are cached.
 *   <li>Write operations evict relevant caches to ensure consistency.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovementService {

  private final MovementRepository movementRepository;
  private final MuscleRepository muscleRepository;
  private final MuscleService muscleService;
  private final MovementMapper movementMapper;

  /**
   * Retrieves a lightweight list of movements, optionally filtered by a search query.
   *
   * <p>This method uses Database Projections to fetch only the necessary fields, significantly
   * reducing memory footprint and latency compared to fetching full entities.
   *
   * @param query The search term (case-insensitive). If empty, returns all movements.
   * @return A list of summarized movement data.
   */
  @Transactional(readOnly = true)
  @Cacheable(
      value = CACHE_MOVEMENTS,
      key = "'all'",
      condition = "#query == null || #query.isBlank()")
  public List<MovementSummaryResponse> searchMovements(String query) {
    List<MovementSummary> projections;

    if (query == null || query.isBlank()) {
      projections = movementRepository.findAllProjectedBy();
    } else {
      projections = movementRepository.findProjectedByNameContainingIgnoreCase(query);
    }

    // Map projection interface to DTO
    return projections.stream().map(movementMapper::toSummary).toList();
  }

  /**
   * Retrieves full details of a movement by its business ID.
   *
   * @param id The unique business ID (e.g., "WL-SQ-A1B2").
   * @return The detailed movement response including anatomy and coaching cues.
   * @throws ResourceNotFoundException if the movement does not exist.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = CACHE_MOVEMENTS, key = "#id")
  public MovementResponse getMovement(String id) {

    return movementRepository
        .findById(id)
        .map(movementMapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("error.movement.not.found", id));
  }

  /**
   * Retrieves movements filtered by category using lightweight projections.
   *
   * @param category The functional category to filter by.
   * @return A list of movement summaries.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = CACHE_MOVEMENTS, key = "'cat-' + #category.name()")
  public List<MovementSummaryResponse> getMovementsByCategory(Category category) {

    return movementRepository.findProjectedByCategory(category).stream()
        .map(movementMapper::toSummary)
        .toList();
  }

  /**
   * Creates a new movement in the catalog.
   *
   * <p>The process involves:
   *
   * <ol>
   *   <li>Mapping the DTO to an entity.
   *   <li>Generating a semantic Business ID based on the category (e.g., "WL-SQ-XXXX").
   *   <li>Resolving and linking targeted muscles from the database (Manual relationship
   *       management).
   *   <li>Persisting the entity and evicting caches.
   * </ol>
   *
   * @param request The creation payload.
   * @return The created movement with its generated ID.
   */
  @Transactional
  @CacheEvict(value = CACHE_MOVEMENTS, allEntries = true)
  public MovementResponse createMovement(MovementRequest request) {

    if (movementRepository.existsByNameIgnoreCase(request.name())) {
      throw new DataConflictException("error.movement.duplicate");
    }

    Movement movement = movementMapper.toEntity(request);

    // 1. Generate Business ID
    String businessId = generateBusinessId(movement);
    movement.setId(businessId);

    // 2. Link Muscles (Manual relationship management)
    if (request.muscles() != null) {
      // Ensure the collection is initialized
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
   * <p>This method uses a "Full Replacement" strategy for muscle relationships: the existing list
   * is cleared and rebuilt from the request to ensure the state matches exactly.
   *
   * @param id The ID of the movement to update.
   * @param request The update payload.
   * @return The updated movement details.
   * @throws ResourceNotFoundException if the movement ID is invalid.
   */
  @Transactional
  @CacheEvict(value = CACHE_MOVEMENTS, allEntries = true)
  public MovementResponse updateMovement(String id, MovementRequest request) {
    Movement movement =
        movementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.movement.not.found", id));

    if (movementRepository.existsByNameIgnoreCase(request.name())) {
      throw new DataConflictException("error.movement.duplicate");
    }

    movementMapper.updateEntity(request, movement);

    // Re-process muscle links (Full Replacement)
    if (request.muscles() != null) {
      movement.getTargetedMuscles().clear();
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
   * @param movement The parent movement entity.
   * @param req The request containing the muscle reference and role.
   * @throws ResourceNotFoundException if the referenced muscle name does not exist.
   */
  private void linkMuscleToMovement(Movement movement, MovementMuscleRequest req) {
    MuscleResponse muscleResponse = muscleService.getMuscle(req.medicalName());
    Muscle muscle = muscleRepository.getReferenceById(muscleResponse.id());

    MovementMuscle joinEntity = movementMapper.toMuscleEntity(req);
    joinEntity.setMovement(movement);
    joinEntity.setMuscle(muscle);

    movement.getTargetedMuscles().add(joinEntity);
  }

  /**
   * Generates a unique semantic business ID for a movement.
   *
   * @param movement The movement for which to generate the ID.
   * @return A string in the format {CATEGORY_PREFIX}-{SHORT_UUID} (e.g., "WL-SQ-A1B2").
   */
  private String generateBusinessId(Movement movement) {
    String prefix = movement.getSemanticIdPrefix();
    if (prefix == null) {
      prefix = "GEN"; // Fallback
    }
    // Generate a short, somewhat unique suffix (4 chars)
    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    return String.format("%s-%s", prefix, uniqueSuffix);
  }
}
