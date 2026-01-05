package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_WODS;

import apex.stellar.aldebaran.config.SecurityUtils;
import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.WodMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
  private final WodMapper wodMapper;

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
   * @param pageable Pagination information.
   * @return A list of lightweight WOD summaries.
   */
  @Transactional(readOnly = true)
  public List<WodSummaryResponse> getWods(String search, WodType type, Pageable pageable) {
    List<WodSummary> projections;

    if (StringUtils.hasText(search)) {
      // Search by title (Projection)
      projections = wodRepository.findProjectedByTitleContainingIgnoreCase(search);
    } else if (type != null) {
      // Filter by type (Projection)
      projections = wodRepository.findProjectedByWodType(type, pageable);
    } else {
      // Find all (Projection)
      projections = wodRepository.findAllProjectedBy(pageable);
    }

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
    processWodMovements(wod, request.movements());

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
   *   <li>Clears existing movements and rebuilds the list (simplifies logic vs merging).
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
    Wod existingWod =
        wodRepository
            .findByIdWithMovements(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", id));

    // Update basic fields via Mapper (or setters if partial update logic is complex)
    wodMapper.updateEntity(request, existingWod);

    // Update Movements (Clear and Replace strategy)
    if (existingWod.getMovements() != null) {
      existingWod.getMovements().clear();
    }
    if (existingWod.getModalities() != null) {
      existingWod.getModalities().clear();
    }

    processWodMovements(existingWod, request.movements());

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

  /** Extracts the User ID from the Spring Security Context using {@link SecurityUtils}. */
  private void assignCreator(Wod wod) {
    try {
      String userId = SecurityUtils.getCurrentUserId();
      wod.setCreatorId(Long.valueOf(userId));
    } catch (Exception e) {
      // Log as warning but don't block creation if user context is missing or ID is non-numeric
      log.warn("Unable to assign creator ID: {}", e.getMessage());
    }
  }

  /**
   * Orchestrates the linking of Movement Entities to the WOD. Aggregates {@link Modality} to the
   * WOD level for searching.
   *
   * @param wod The parent WOD entity.
   * @param requests The list of movement requests.
   */
  private void processWodMovements(Wod wod, List<WodMovementRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return;
    }

    if (wod.getMovements() == null) wod.setMovements(new ArrayList<>());
    if (wod.getModalities() == null) wod.setModalities(new HashSet<>());

    for (WodMovementRequest req : requests) {
      // Use the MovementRepository's findById (String ID)
      Movement movement =
          movementRepository
              .findById(req.movementId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("error.movement.not.found", req.movementId()));

      // Create the Join Entity
      WodMovement link = wodMapper.toWodMovementEntity(req);
      link.setWod(wod);
      link.setMovement(movement);

      wod.getMovements().add(link);

      // Aggregate Modality (e.g., Pull-up -> GYMNASTICS)
      if (movement.getModality() != null) {
        wod.getModalities().add(movement.getModality());
      }
    }
  }
}
