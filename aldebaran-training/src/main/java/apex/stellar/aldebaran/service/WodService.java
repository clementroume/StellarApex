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
 * Service for managing Workout of the Day (WOD) definitions.
 *
 * <p>Handles the lifecycle of {@link Wod} entities, including the orchestration of nested {@link
 * WodMovement} components and the aggregation of training modalities.
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
  // READ OPERATIONS (Optimized)
  // =========================================================================

  /**
   * Retrieves a list of WODs based on filters.
   *
   * <p><b>Optimization:</b> Uses Database Projections ({@link WodSummary}) exclusively. This avoids
   * loading the heavy `movements` collection for simple list views.
   *
   * @param search Optional title search string.
   * @param type Optional WOD type filter.
   * @param movementId Optional movement ID filter.
   * @param pageable Pagination information.
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
   * <p><b>Optimization:</b> Uses {@link org.springframework.data.jpa.repository.EntityGraph} via
   * {@code findByIdWithMovements} to fetch the WOD and all its associated movements in a single SQL
   * query, preventing N+1 issues.
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
  // WRITE OPERATIONS (Transactional)
  // =========================================================================

  /**
   * Creates a new WOD.
   *
   * <p>Automatically links the creator from the {@code X-Auth-User-Id} header and aggregates
   * modalities (e.g., GYMNASTICS) from the movements.
   *
   * @param request The creation payload.
   * @return The created WOD details.
   */
  @Transactional
  public WodResponse createWod(WodRequest request) {
    Wod wod = wodMapper.toEntity(request);

    // 1. Assign Creator from Security Context (ForwardedAuthFilter)
    assignCreator(wod);

    // 2. Link Movements & Calculate Modalities
    mergeWodMovements(wod, request.movements());

    Wod savedWod = wodRepository.save(wod);
    log.info(
        "Created WOD id={} with {} movements", savedWod.getId(), savedWod.getMovements().size());

    return wodMapper.toResponse(savedWod);
  }

  /**
   * Updates an existing WOD.
   *
   * <p>Strategies:
   *
   * <ul>
   *   <li>Loads entity with {@code EntityGraph} to ensure collections are initialized.
   *   <li>Uses "Smart Merge" to update movements in place, avoiding table churn.
   *   <li>Recalculates Modalities based on new movements.
   * </ul>
   *
   * @param id The ID of the WOD to update.
   * @param request The update payload.
   * @return The updated WOD details.
   * @throws ResourceNotFoundException if the WOD is not found.
   */
  @Transactional
  @CacheEvict(value = CACHE_WODS, key = "#id")
  public WodResponse updateWod(Long id, WodRequest request) {
    // Integrity Check: Lock WOD if scores exist
    if (wodScoreRepository.existsByWodId(id)) {
      throw new WodLockedException("error.wod.locked", id);
    }

    Wod existingWod =
        wodRepository
            .findByIdWithMovements(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", id));

    // Update basic fields via Mapper (or setters if partial update logic is complex)
    wodMapper.updateEntity(request, existingWod);

    // Smart Merge Movements
    mergeWodMovements(existingWod, request.movements());

    Wod savedWod = wodRepository.save(existingWod);
    log.info("Updated WOD id={}", savedWod.getId());
    return wodMapper.toResponse(savedWod);
  }

  /**
   * Deletes a WOD definition.
   *
   * @param id The ID of the WOD to delete.
   * @throws ResourceNotFoundException if the WOD is not found.
   */
  @Transactional
  @CacheEvict(value = CACHE_WODS, key = "#id")
  public void deleteWod(Long id) {
    if (!wodRepository.existsById(id)) {
      throw new ResourceNotFoundException("error.wod.not.found", id);
    }
    wodRepository.deleteById(id);
    log.info("Deleted WOD id={}", id);
  }

  // =========================================================================
  // INTERNAL HELPERS
  // =========================================================================

  /**
   * Assigns the creator ID to the WOD based on the current authenticated user context.
   *
   * @param wod The WOD entity to update.
   */
  private void assignCreator(Wod wod) {
    try {
      Long userId = securityService.getCurrentUserId();
      wod.setAuthorId(userId);
    } catch (Exception e) {
      // Log as warning but don't block creation if user context is missing or ID is non-numeric
      log.warn("Unable to assign creator ID: {}", e.getMessage());
    }
  }

  /**
   * Orchestrates the linking of Movement Entities to the WOD using a Smart Merge strategy.
   *
   * <p>Matches existing movements by {@code orderIndex} to update them in place, avoiding
   * unnecessary deletes and inserts.
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

    // 1. Pre-fetch Movements (Optimization)
    Set<String> movementIds =
        requests.stream().map(WodMovementRequest::movementId).collect(Collectors.toSet());

    Map<String, Movement> movementMap =
        movementRepository.findAllById(movementIds).stream()
            .collect(Collectors.toMap(Movement::getId, Function.identity()));

    if (movementMap.size() != movementIds.size()) {
      List<String> missingIds =
          movementIds.stream().filter(id -> !movementMap.containsKey(id)).toList();
      throw new ResourceNotFoundException("error.movement.not.found", missingIds);
    }

    // 2. Map existing WodMovements by OrderIndex for quick lookup
    Map<Integer, WodMovement> existingMap =
        wod.getMovements().stream()
            .collect(Collectors.toMap(WodMovement::getOrderIndex, Function.identity()));

    List<WodMovement> updatedList = new ArrayList<>();
    Set<Modality> newModalities = new HashSet<>();

    // 3. Process Requests
    for (WodMovementRequest req : requests) {
      Movement movement = movementMap.get(req.movementId());
      WodMovement wodMovement;

      if (existingMap.containsKey(req.orderIndex())) {
        // UPDATE existing (Preserve ID)
        wodMovement = existingMap.get(req.orderIndex());
        updateWodMovementFields(wodMovement, req);
      } else {
        // CREATE new
        wodMovement = wodMapper.toWodMovementEntity(req);
        wodMovement.setWod(wod);
      }

      // Ensure relationship is set/updated
      wodMovement.setMovement(movement);

      updatedList.add(wodMovement);

      // Aggregate Modality (e.g., Pull-up -> GYMNASTICS)
      if (movement.getModality() != null) {
        newModalities.add(movement.getModality());
      }
    }

    // 4. Apply changes to the collection (Orphan Removal + Add New)
    List<WodMovement> currentList = wod.getMovements();

    // Remove items that are no longer present in the updated list
    currentList.removeIf(existing -> !updatedList.contains(existing));

    // Add new items
    for (WodMovement item : updatedList) {
      if (!currentList.contains(item)) {
        currentList.add(item);
      }
    }

    // 5. Update Modalities
    wod.getModalities().clear();
    wod.getModalities().addAll(newModalities);
  }

  private void updateWodMovementFields(WodMovement target, WodMovementRequest source) {
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
