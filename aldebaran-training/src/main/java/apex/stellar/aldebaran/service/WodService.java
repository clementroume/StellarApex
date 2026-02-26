package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_WODS;

import apex.stellar.aldebaran.dto.MovementResponse;
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
import org.springframework.data.domain.Slice;
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
  private final MovementService movementService;
  private final WodScoreRepository wodScoreRepository;
  private final WodMapper wodMapper;
  private final SecurityService securityService;

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
   * @return A list of lightweight WOD summaries.
   */
  @Transactional(readOnly = true)
  public Slice<WodSummaryResponse> getWods(
      String search, WodType type, String movementId, Pageable pageable) {

    Slice<WodSummary> projections;

    if (StringUtils.hasText(search)) {
      projections = wodRepository.findByTitleSecure(search, pageable);
    } else if (StringUtils.hasText(movementId)) {
      projections = wodRepository.findByMovementSecure(movementId, pageable);
    } else if (type != null) {
      projections = wodRepository.findByTypeSecure(type, pageable);
    } else {
      projections = wodRepository.findAllSecure(pageable);
    }

    return projections.map(wodMapper::toSummary);
  }

  /**
   * Retrieves full details of a specific WOD.
   *
   * <p><b>Optimization:</b> Uses {@code EntityGraph} to fetch the WOD and all movements in a single
   * query. Results are cached to reduce the database load.
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

    // 1. Build Entity
    Wod wod = wodMapper.toEntity(request);
    wod.setAuthorId(authorId);

    // Ensure collections are initialized
    if (wod.getMovements() == null) {
      wod.setMovements(new ArrayList<>());
    }
    if (wod.getModalities() == null) {
      wod.setModalities(new HashSet<>());
    }

    // 2. Link Relations (In-Memory operation, extremely fast)
    request
        .movements()
        .forEach(
            item -> {
              final MovementResponse movementResponse =
                  movementService.getMovement(item.movementId());

              Movement movement = movementRepository.getReferenceById(item.movementId());

              WodMovement wm = wodMapper.toWodMovementEntity(item);
              wm.setWod(wod);
              wm.setMovement(movement);

              wod.getMovements().add(wm);

              // Aggregate Modality (e.g., Gymnastics, Weightlifting)
              if (movementResponse.category() != null
                  && movementResponse.category().getModality() != null) {
                wod.getModalities().add(movementResponse.category().getModality());
              }
            });

    // 3. Save & Map
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

    // 1. Map existing items by OrderIndex for O(1) lookup
    Map<Integer, WodMovement> existingMap =
        wod.getMovements().stream()
            .collect(Collectors.toMap(WodMovement::getOrderIndex, Function.identity()));

    List<WodMovement> updatedList = new ArrayList<>();
    Set<Modality> newModalities = new HashSet<>();

    // 2. Process Request Items
    for (WodMovementRequest req : requests) {
      final MovementResponse movementResponse = movementService.getMovement(req.movementId());
      Movement movement = movementRepository.getReferenceById(req.movementId());

      WodMovement target;

      if (existingMap.containsKey(req.orderIndex())) {
        // Update the existing entity (Preserve ID)
        target = existingMap.get(req.orderIndex());
        updateWodMovementFields(target, req);
      } else {
        // Create a new entity
        target = wodMapper.toWodMovementEntity(req);
        target.setWod(wod);
      }

      target.setMovement(movement);
      updatedList.add(target);

      if (movementResponse.category() != null
          && movementResponse.category().getModality() != null) {
        newModalities.add(movementResponse.category().getModality());
      }
    }

    // 3. Replace List
    wod.getMovements().clear();
    wod.getMovements().addAll(updatedList);

    // 4. Update Aggregated Modalities
    wod.getModalities().clear();
    wod.getModalities().addAll(newModalities);
  }

  /**
   * Updates the fields of an existing WodMovement entity with values from the request DTO.
   *
   * @param target The existing WodMovement entity to update.
   * @param source The request DTO containing the new values.
   */
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
