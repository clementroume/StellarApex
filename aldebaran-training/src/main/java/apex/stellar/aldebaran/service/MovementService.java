package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MOVEMENT;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MOVEMENTS;

import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementReferenceData;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.MovementMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.projection.MovementSummary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  @Cacheable(value = CACHE_MOVEMENT, key = "#id")
  public MovementResponse getMovement(Long id) {

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
  @CacheEvict(
      value = {CACHE_MOVEMENTS, CACHE_MOVEMENT},
      allEntries = true)
  public MovementResponse createMovement(MovementRequest request) {

    if (movementRepository.existsByNameIgnoreCase(request.name())) {
      throw new DataConflictException("error.movement.duplicate");
    }

    Movement movement = movementMapper.toEntity(request);

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
   * <p>This method uses a "Full Replacement" strategy for muscle relationships: the existing list
   * is cleared and rebuilt from the request to ensure the state matches exactly.
   *
   * @param id The ID of the movement to update.
   * @param request The update payload.
   * @return The updated movement details.
   * @throws ResourceNotFoundException if the movement ID is invalid.
   */
  @Transactional
  @CacheEvict(
      value = {CACHE_MOVEMENTS, CACHE_MOVEMENT},
      allEntries = true)
  public MovementResponse updateMovement(Long id, MovementRequest request) {
    Movement movement =
        movementRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.movement.not.found", id));

    if (!movement.getName().equalsIgnoreCase(request.name())
        && movementRepository.existsByNameIgnoreCase(request.name())) {
      throw new DataConflictException("error.movement.duplicate");
    }

    movementMapper.updateEntity(request, movement);

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

  /**
   * Retrieves the structured reference data for Movement forms. Maintains the declaration order of
   * the Enums.
   */
  public MovementReferenceData getReferenceData() {

    Map<String, List<String>> categoryGroups = new LinkedHashMap<>();
    for (Category c : Category.values()) {
      categoryGroups.computeIfAbsent(c.getModality().name(), k -> new ArrayList<>()).add(c.name());
    }

    Map<String, List<String>> equipmentGroups = new LinkedHashMap<>();
    for (Equipment e : Equipment.values()) {
      equipmentGroups.computeIfAbsent(e.getCategory().name(), k -> new ArrayList<>()).add(e.name());
    }

    Map<String, List<String>> techniqueGroups = new LinkedHashMap<>();
    for (Technique t : Technique.values()) {
      techniqueGroups.computeIfAbsent(t.getCategory().name(), k -> new ArrayList<>()).add(t.name());
    }

    return new MovementReferenceData(categoryGroups, equipmentGroups, techniqueGroups);
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
    Muscle muscle =
        muscleRepository
            .findById(req.muscleId())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.muscle.not.found", req.muscleId()));

    MovementMuscle joinEntity = movementMapper.toMuscleEntity(req);
    joinEntity.setMovement(movement);
    joinEntity.setMuscle(muscle);

    movement.getTargetedMuscles().add(joinEntity);
  }
}
