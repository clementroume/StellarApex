package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_WODS;

import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.exception.WodLockedException;
import apex.stellar.aldebaran.mapper.WodMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.SecurityService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service responsible for the lifecycle management of Workout of the Day (WOD) entities.
 *
 * <p>This service orchestrates the creation, modification, and retrieval of WODs.
 *
 * <p><b>Performance Strategy:</b>
 *
 * <ul>
 *   <li><b>Batch Fetching:</b> Uses {@code findAllById} to load associated movements in a single
 *       query, eliminating N+1 issues.
 *   <li><b>Read-Optimized:</b> Uses Database Projections for lists and EntityGraphs for details.
 *   <li><b>Smart Updates:</b> Updates collections in-place to minimize database churn.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WodService {

  private final WodRepository wodRepository;
  private final MovementRepository movementRepository;
  private final WodScoreRepository wodScoreRepository;
  private final WodMapper wodMapper;
  private final SecurityService securityService;

  // =========================================================================
  // READ OPERATIONS
  // =========================================================================

  /**
   * Retrieves a paginated list of WOD summaries based on filters.
   *
   * <p><b>Optimization:</b> Uses {@link WodSummary} projections to fetch only essential data,
   * avoiding the cost of loading full entities and their relationships.
   *
   * @param search Optional title search term.
   * @param type Optional WOD type filter.
   * @param movementId Optional movement ID filter.
   * @param pageable Pagination settings.
   * @param principal The authenticated user (for security filtering).
   * @return A list of lightweight WOD summaries.
   */
  @Transactional(readOnly = true)
  public List<WodSummaryResponse> getWods(
      String search,
      WodType type,
      String movementId,
      Pageable pageable,
      AldebaranUserPrincipal principal) {

    // 1. Prepare Security Context
    // If principal is null (should not happen due to auth guards), fallback to restrictive
    // defaults.
    Long userId = principal != null ? principal.getId() : -1L;
    Long gymId = principal != null ? principal.getGymId() : null;
    boolean isAdmin = securityService.isAdmin(principal);

    List<WodSummary> projections;

    // 2. Execute Optimized & Secure Query based on filters
    if (StringUtils.hasText(search)) {
      projections = wodRepository.findByTitleSecure(search, userId, gymId, isAdmin);
    } else if (StringUtils.hasText(movementId)) {
      projections =
          wodRepository.findByMovementSecure(movementId, userId, gymId, isAdmin, pageable);
    } else if (type != null) {
      projections = wodRepository.findByTypeSecure(type, userId, gymId, isAdmin, pageable);
    } else {
      projections = wodRepository.findAllSecure(userId, gymId, isAdmin, pageable);
    }

    // 3. Map Projection to DTO
    return projections.stream()
        .map(
            p ->
                new WodSummaryResponse(
                    p.getId(),
                    p.getTitle(),
                    p.getWodType(),
                    p.getScoreType(),
                    p.getRepScheme(),
                    p.getTimeCapSeconds(),
                    p.getCreatedAt()))
        .toList();
  }

  /**
   * Retrieves full details of a specific WOD.
   *
   * <p><b>Optimization:</b> Uses {@code EntityGraph} to fetch the WOD and all movements in a single
   * query. Results are cached to reduce database load.
   *
   * @param id The WOD ID.
   * @return The detailed response.
   * @throws ResourceNotFoundException if the WOD is not found.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = CACHE_WODS, key = "#id")
  public WodResponse getWodDetail(Long id) {
    return wodRepository
        .findByIdWithMovements(id)
        .map(wodMapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", id));
  }

  // =========================================================================
  // WRITE OPERATIONS
  // =========================================================================

  /**
   * Creates a new WOD.
   *
   * <p><b>Optimization:</b> Pre-fetches all referenced movements in a single SQL query using a Map
   * structure. This prevents the "N+1 Selects" problem while keeping the logic within a single
   * transaction scope.
   *
   * @param request The creation payload.
   * @return The created WOD details.
   */
  @Transactional
  public WodResponse createWod(WodRequest request) {
    Long authorId = securityService.getCurrentUserId();

    // 1. Optimization: Batch fetch all required movements in ONE query
    Set<String> movementIds =
        request.movements().stream()
            .map(WodMovementRequest::movementId)
            .collect(Collectors.toSet());

    Map<String, Movement> movements =
        movementRepository.findAllById(movementIds).stream()
            .collect(Collectors.toMap(Movement::getId, Function.identity()));

    // Validation: Ensure all requested movements exist
    if (movements.size() != movementIds.size()) {
      throw new ResourceNotFoundException("error.movement.not.found");
    }

    // 2. Build Entity
    Wod wod = wodMapper.toEntity(request);
    wod.setAuthorId(authorId);

    // Ensure collections are initialized
    if (wod.getMovements() == null) {
      wod.setMovements(new ArrayList<>());
    }
    if (wod.getModalities() == null) {
      wod.setModalities(new HashSet<>());
    }

    // 3. Link Relations (In-Memory operation, extremely fast)
    request
        .movements()
        .forEach(
            item -> {
              Movement movement = movements.get(item.movementId());

              WodMovement wm = wodMapper.toWodMovementEntity(item);
              wm.setWod(wod);
              wm.setMovement(movement);

              wod.getMovements().add(wm);

              // Aggregate Modality (e.g., Gymnastics, Weightlifting)
              if (movement.getModality() != null) {
                wod.getModalities().add(movement.getModality());
              }
            });

    // 4. Save & Map
    Wod savedWod = wodRepository.save(wod);

    log.info(
        "Created WOD id={} with {} movements", savedWod.getId(), savedWod.getMovements().size());

    // Mapping occurs inside the transaction, avoiding LazyInitializationException
    return wodMapper.toResponse(savedWod);
  }

  /**
   * Updates an existing WOD.
   *
   * <p><b>Strategy:</b>
   *
   * <ul>
   *   <li>Checks for data integrity (locks WOD if scores exist).
   *   <li>Uses "Smart Merge" to update movements in-place, preserving IDs and reducing DB churn.
   * </ul>
   *
   * @param id The ID of the WOD to update.
   * @param request The update payload.
   * @return The updated WOD details.
   * @throws WodLockedException if the WOD has linked scores.
   */
  @Transactional
  @CacheEvict(value = CACHE_WODS, key = "#id")
  public WodResponse updateWod(Long id, WodRequest request) {
    // 1. Integrity Check: Lock WOD if scores exist
    if (wodScoreRepository.existsByWodId(id)) {
      throw new WodLockedException("error.wod.locked", id);
    }

    // 2. Fetch Existing Data (with movements to allow merging)
    Wod existingWod =
        wodRepository
            .findByIdWithMovements(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", id));

    // 3. Update Scalar Fields
    wodMapper.updateEntity(request, existingWod);

    // 4. Smart Merge Movements
    mergeWodMovements(existingWod, request.movements());

    // 5. Save & Return
    Wod savedWod = wodRepository.save(existingWod);
    log.info("Updated WOD id={}", savedWod.getId());

    return wodMapper.toResponse(savedWod);
  }

  /**
   * Deletes a WOD definition.
   *
   * @param id The ID of the WOD to delete.
   * @throws ResourceNotFoundException if the WOD does not exist.
   */
  @Transactional
  @CacheEvict(value = CACHE_WODS, key = "#id")
  public void deleteWod(Long id) {
    if (!wodRepository.existsById(id)) {
      throw new ResourceNotFoundException("error.wod.not.found", id);
    }

    if (wodScoreRepository.existsByWodId(id)) {
      throw new WodLockedException("error.wod.locked", id);
    }

    wodRepository.deleteById(id);
    log.info("Deleted WOD id={}", id);
  }

  // =========================================================================
  // INTERNAL HELPERS
  // =========================================================================

  /**
   * Orchestrates the linking of Movement Entities to the WOD using a Smart Merge strategy. Matches
   * existing movements by {@code orderIndex} to update them in place.
   *
   * @param wod The parent WOD entity.
   * @param requests The list of movement requests.
   */
  private void mergeWodMovements(Wod wod, List<WodMovementRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return;
    }

    if (wod.getMovements() == null) {
      wod.setMovements(new ArrayList<>());
    }
    if (wod.getModalities() == null) {
      wod.setModalities(new HashSet<>());
    }

    // 1. Batch Fetch Movements (Optimization)
    Set<String> movementIds =
        requests.stream().map(WodMovementRequest::movementId).collect(Collectors.toSet());

    Map<String, Movement> movementMap =
        movementRepository.findAllById(movementIds).stream()
            .collect(Collectors.toMap(Movement::getId, Function.identity()));

    if (movementMap.size() != movementIds.size()) {
      throw new ResourceNotFoundException("error.movement.not.found");
    }

    // 2. Map existing items by OrderIndex for O(1) lookup
    Map<Integer, WodMovement> existingMap =
        wod.getMovements().stream()
            .collect(Collectors.toMap(WodMovement::getOrderIndex, Function.identity()));

    List<WodMovement> updatedList = new ArrayList<>();
    Set<Modality> newModalities = new HashSet<>();

    // 3. Process Request Items
    for (WodMovementRequest req : requests) {
      Movement movement = movementMap.get(req.movementId());
      WodMovement target;

      if (existingMap.containsKey(req.orderIndex())) {
        // Update existing entity (Preserve ID)
        target = existingMap.get(req.orderIndex());
        updateWodMovementFields(target, req);
      } else {
        // Create new entity
        target = wodMapper.toWodMovementEntity(req);
        target.setWod(wod);
      }

      target.setMovement(movement);
      updatedList.add(target);

      if (movement.getModality() != null) {
        newModalities.add(movement.getModality());
      }
    }

    // 4. Orphan Removal (Remove items not present in the new list)
    wod.getMovements().removeIf(curr -> !updatedList.contains(curr));

    // 5. Add New Items
    for (WodMovement item : updatedList) {
      if (!wod.getMovements().contains(item)) {
        wod.getMovements().add(item);
      }
    }

    // 6. Update Aggregated Modalities
    wod.getModalities().clear();
    wod.getModalities().addAll(newModalities);
  }

  private void updateWodMovementFields(WodMovement target, WodMovementRequest source) {
    // Leverage Mapper logic to create a temporary entity, then copy fields
    WodMovement temp = wodMapper.toWodMovementEntity(source);

    target.setRepsScheme(temp.getRepsScheme());
    target.setWeight(temp.getWeight());
    target.setWeightUnit(temp.getWeightUnit());
    target.setDurationSeconds(temp.getDurationSeconds());
    target.setDurationDisplayUnit(temp.getDurationDisplayUnit());
    target.setDistance(temp.getDistance());
    target.setDistanceUnit(temp.getDistanceUnit());
    target.setCalories(temp.getCalories());
    target.setNotes(temp.getNotes());
    target.setScalingOptions(temp.getScalingOptions());
    target.setOrderIndex(temp.getOrderIndex());
  }
}
