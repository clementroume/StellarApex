package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.config.SecurityUtils;
import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.WodMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing WOD definitions (The "Recipe").
 *
 * <p>Handles the creation and retrieval of workouts, including the automatic aggregation of
 * training modalities based on the movements prescribed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WodService {

  private final WodRepository wodRepository;
  private final MovementRepository movementRepository;
  private final WodMapper wodMapper;

  /**
   * Retrieves a lightweight list of all WODs.
   *
   * <p>Uses a database projection to fetch only summary fields, optimizing performance for list
   * views and autocomplete.
   *
   * @return A list of WOD summaries.
   */
  @Transactional(readOnly = true)
  public List<WodSummaryResponse> findAllWods() {
    return wodRepository.findAllProjectedBy().stream()
        .map(p -> new WodSummaryResponse(p.getId(), p.getTitle(), p.getWodType(), p.getScoreType()))
        .toList();
  }

  /**
   * Retrieves a full WOD definition by its ID.
   *
   * <p>This method initializes the {@code movements} collection eagerly via the repository to avoid
   * LazyInitializationException. The result is cached.
   *
   * @param id The ID of the WOD.
   * @return The detailed WOD response.
   * @throws ResourceNotFoundException if the WOD does not exist.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = "wods", key = "#id")
  public WodResponse getWod(Long id) {
    return wodRepository
        .findByIdWithMovements(id)
        .map(wodMapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", id));
  }

  /**
   * Creates a new WOD definition.
   *
   * <p>The service performs the following logic:
   *
   * <ul>
   *   <li>Assigns the creator ID from the security context (if authenticated).
   *   <li>Links specific movements to the WOD.
   *   <li>Aggregates {@link Modality} tags (e.g., "GYMNASTICS") from the movements.
   * </ul>
   *
   * @param request The WOD creation request.
   * @return The created WOD details.
   * @throws ResourceNotFoundException if a referenced movement ID is invalid.
   */
  @Transactional
  @CacheEvict(value = "wods", allEntries = true) // Invalidate lists if necessary
  public WodResponse createWod(WodRequest request) {
    Wod wod = wodMapper.toEntity(request);

    // Set Creator from Security Context
    if (SecurityUtils.isAuthenticated()) {
      try {
        String userId = SecurityUtils.getCurrentUserId();
        wod.setCreatorId(Long.valueOf(userId));
      } catch (NumberFormatException e) {
        log.warn("Authenticated User ID is not numeric. CreatorID skipped.");
      }
    }

    // Process movements and link them
    processWodMovements(wod, request.movements());

    Wod saved = wodRepository.save(wod);
    log.info(
        "Created WOD '{}' (ID: {}) with {} movements",
        saved.getTitle(),
        saved.getId(),
        saved.getMovements().size());
    return wodMapper.toResponse(saved);
  }

  // -------------------------------------------------------------------------
  // Internal Logic
  // -------------------------------------------------------------------------

  /** Links movement entities to the WOD and aggregates functional modalities. */
  private void processWodMovements(Wod wod, List<WodMovementRequest> movementRequests) {
    if (movementRequests == null || movementRequests.isEmpty()) {
      return;
    }

    wod.setMovements(new ArrayList<>());
    wod.setModalities(new HashSet<>());

    for (WodMovementRequest req : movementRequests) {
      Movement movement =
          movementRepository
              .findById(req.movementId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("error.movement.not.found", req.movementId()));

      // 1. Create Link Entity
      WodMovement link = wodMapper.toWodMovementEntity(req);
      link.setWod(wod);
      link.setMovement(movement);

      wod.getMovements().add(link);

      // 2. Aggregate Modalities (e.g., if WOD has Running -> Add MONOSTRUCTURAL)
      Modality mod = movement.getModality();
      if (mod != null) {
        wod.getModalities().add(mod);
      }
    }
  }
}
