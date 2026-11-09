package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MOVEMENT;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MOVEMENTS;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_USUAL_NAMES;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_WODS;

import apex.stellar.aldebaran.dto.MovementComponentDto;
import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementReferenceData;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.dto.MovementVariantResponse;
import apex.stellar.aldebaran.dto.RoleAdjustmentDto;
import apex.stellar.aldebaran.dto.UsualNameDto;
import apex.stellar.aldebaran.dto.UsualNameOptionResponse;
import apex.stellar.aldebaran.dto.VariantMuscleResponse;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.MovementMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementComponent;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment;
import apex.stellar.aldebaran.model.entities.MovementUsualName;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final WodRepository wodRepository;
  private final MovementMapper movementMapper;

  /**
   * Retrieves a lightweight list of movements.
   *
   * <p>This method uses Database Projections to fetch only the necessary fields, significantly
   * reducing memory footprint and latency compared to fetching full entities.
   *
   * @return A list of summarized movement data.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = CACHE_MOVEMENTS, key = "'summaries'")
  public List<MovementSummaryResponse> getAllMovements() {

    Map<Long, List<String>> components = namesById(movementRepository.findComponentNames());
    Map<Long, List<String>> accepted = namesById(movementRepository.findAcceptedNames());
    return movementRepository.findAllProjectedBy().stream()
        .map(movementMapper::toSummary)
        .map(
            s ->
                s.withComposition(
                    components.getOrDefault(s.id(), List.of()),
                    accepted.getOrDefault(s.id(), List.of())))
        .toList();
  }

  /**
   * A movement for a chosen variant: its usual name, its roles and what to show about it.
   *
   * @param id The movement.
   * @param techniques The chosen techniques.
   * @param equipment The chosen equipment.
   * @return The variant.
   * @throws ResourceNotFoundException if the movement does not exist.
   */
  @Transactional(readOnly = true)
  public MovementVariantResponse getVariant(
      Long id, Set<String> requestedTechniques, Set<String> equipment) {
    Movement movement = findMovement(id);
    // A value the movement does not allow is no variant of it (1:POWER belongs to a component)
    Set<String> techniques = new LinkedHashSet<>(requestedTechniques);
    techniques.removeIf(
        t -> !t.contains(":") && movement.getTechniques().stream().noneMatch(a -> a.name().equals(t)));
    CatalogRules.Spec spec = CatalogRules.Spec.of(movement);
    Optional<MovementUsualName> usual =
        CatalogRules.usualName(
            movement, CatalogRules.visibleEquipment(spec, equipment, techniques), techniques);
    Comparator<VariantMuscleResponse> order =
        Comparator.comparing(VariantMuscleResponse::role)
            .thenComparing(VariantMuscleResponse::medicalName);
    List<VariantMuscleResponse> muscles =
        CatalogRules.effectiveRoles(movement, techniques, equipment).entrySet().stream()
            .map(
                e ->
                    new VariantMuscleResponse(
                        e.getKey().getId(),
                        e.getKey().getMedicalName(),
                        e.getKey().getCommonNameEn(),
                        e.getKey().getCommonNameFr(),
                        e.getValue()))
            .sorted(order)
            .toList();
    String descriptionEn = usual.map(MovementUsualName::getDescriptionEn).orElse(null);
    String descriptionFr = usual.map(MovementUsualName::getDescriptionFr).orElse(null);
    String video = usual.map(MovementUsualName::getVideoUrl).orElse(null);
    return new MovementVariantResponse(
        usual.map(MovementUsualName::getNameEn).orElse(null),
        usual.map(MovementUsualName::getNameFr).orElse(null),
        descriptionEn != null ? descriptionEn : movement.getDescriptionEn(),
        descriptionFr != null ? descriptionFr : movement.getDescriptionFr(),
        video != null ? video : movement.getVideoUrl(),
        CatalogRules.implementCount(spec, equipment, techniques),
        LiftVariant.key(movement, equipment, techniques),
        muscles);
  }

  /**
   * Lists the usual names of the catalog: what the movement search of the WOD forms offers.
   *
   * @return The usual names, by English name.
   */
  @Transactional(readOnly = true)
  @Cacheable(value = CACHE_USUAL_NAMES, key = "'all'")
  public List<UsualNameOptionResponse> getUsualNames() {
    return movementRepository.findAllUsualNames().stream()
        .map(movementMapper::toUsualNameOption)
        .toList();
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
      value = {CACHE_MOVEMENTS, CACHE_USUAL_NAMES, CACHE_MOVEMENT},
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

    applyCatalogModel(movement, request);

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
   * <p>Equipment and techniques are the allowed variants of the movement: one that WODs still
   * prescribe cannot be removed (see {@link #checkRemovedVariantsUnused}).
   *
   * @param id The ID of the movement to update.
   * @param request The update payload.
   * @return The updated movement details.
   * @throws ResourceNotFoundException if the movement ID is invalid.
   * @throws DataConflictException if the name is taken or a removed variant is used by a WOD.
   */
  @Transactional
  @CacheEvict(
      value = {CACHE_MOVEMENTS, CACHE_USUAL_NAMES, CACHE_MOVEMENT, CACHE_WODS},
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

    checkRemovedVariantsUnused(movement, request);
    movementMapper.updateEntity(request, movement);

    if (request.muscles() != null) {
      movement.getTargetedMuscles().clear();
      for (MovementMuscleRequest mmRequest : request.muscles()) {
        linkMuscleToMovement(movement, mmRequest);
      }
    }

    // Unique keys: the old rows must be gone before the new ones are inserted
    movement.getComponents().clear();
    movement.getRoleAdjustments().clear();
    movement.getUsualNames().clear();
    movementRepository.flush();
    applyCatalogModel(movement, request);

    Movement saved = movementRepository.save(movement);
    log.info("Updated movement: {} ({})", saved.getName(), saved.getId());
    return movementMapper.toResponse(saved);
  }

  /**
   * Deletes a movement record with the specified ID from the database. All associated cache entries
   * for movements will be invalidated.
   *
   * @param id the unique identifier of the movement to be deleted
   * @throws ResourceNotFoundException if no movement is found with the given ID
   */
  @Transactional
  @CacheEvict(
      value = {CACHE_MOVEMENTS, CACHE_USUAL_NAMES, CACHE_MOVEMENT, CACHE_WODS},
      allEntries = true)
  public void deleteMovement(Long id) {

    log.info("Deleting Movement with ID: {}", id);
    if (!movementRepository.existsById(id)) {
      throw new ResourceNotFoundException("error.movement.not.found", id);
    }
    try {
      movementRepository.deleteById(id);
      movementRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new DataConflictException("error.movement.in.use", id);
    }
  }

  /**
   * Retrieves the structured reference data for Movement forms. Maintains the declaration order of
   * the Enums.
   */
  public MovementReferenceData getReferenceData() {

    Map<String, List<String>> categoryGroups = new LinkedHashMap<>();
    for (Category c : Category.values()) {
      categoryGroups
          .computeIfAbsent(c.getModality().name(), ignored -> new ArrayList<>())
          .add(c.name());
    }

    Map<String, List<String>> equipmentGroups = new LinkedHashMap<>();
    for (Equipment e : Equipment.values()) {
      equipmentGroups
          .computeIfAbsent(e.getCategory().name(), ignored -> new ArrayList<>())
          .add(e.name());
    }

    Map<String, List<String>> techniqueGroups = new LinkedHashMap<>();
    for (Technique t : Technique.values()) {
      techniqueGroups
          .computeIfAbsent(t.getCategory().name(), ignored -> new ArrayList<>())
          .add(t.name());
    }

    List<List<String>> exclusive =
        Technique.exclusiveSets().stream()
            .map(set -> set.stream().map(Enum::name).toList())
            .toList();
    return new MovementReferenceData(categoryGroups, equipmentGroups, techniqueGroups, exclusive);
  }

  // -------------------------------------------------------------------------
  // Helper Methods
  // -------------------------------------------------------------------------

  /**
   * Rejects removing an equipment or technique that a WOD movement still uses (any WOD, public,
   * gym or personal): those WODs would no longer satisfy {@code
   * WodMovement.checkVariantsAllowed}. Values are checked in enum declaration order.
   *
   * @param movement The movement in its current (persisted) state.
   * @param request The update payload.
   * @throws DataConflictException on the first removed value still in use.
   */
  private void checkRemovedVariantsUnused(Movement movement, MovementRequest request) {
    for (Equipment equipment : Equipment.values()) {
      if (movement.getEquipment().contains(equipment)
          && !request.equipment().contains(equipment)) {
        checkVariantUnused(
            movement,
            equipment,
            wodRepository.countByMovementEquipment(movement.getId(), equipment.name()));
      }
    }
    for (Technique technique : Technique.values()) {
      if (movement.getTechniques().contains(technique)
          && !request.techniques().contains(technique)) {
        checkVariantUnused(
            movement,
            technique,
            wodRepository.countByMovementTechnique(movement.getId(), technique.name()));
      }
    }
  }

  private static void checkVariantUnused(Movement movement, Enum<?> variant, long wodCount) {
    if (wodCount > 0) {
      throw new DataConflictException(
          "error.movement.variant.in.use", variant.name(), movement.getName(), wodCount);
    }
  }

  /** Rebuilds the relations of the catalog model from the request, then checks the movement. */
  private void applyCatalogModel(Movement movement, MovementRequest request) {
    for (MovementComponentDto dto : request.components()) {
      movement
          .getComponents()
          .add(
              MovementComponent.builder()
                  .chain(movement)
                  .componentOrder(dto.componentOrder())
                  .component(findMovement(dto.movementId()))
                  .countsForRecords(dto.countsForRecords())
                  .techniques(orEmpty(dto.techniques()))
                  .equipment(orEmpty(dto.equipment()))
                  .build());
    }
    movement.getAcceptedMovements().clear();
    request.acceptedMovementIds().stream()
        .map(this::findMovement)
        .forEach(movement.getAcceptedMovements()::add);
    for (RoleAdjustmentDto dto : request.roleAdjustments()) {
      Muscle muscle =
          muscleRepository
              .findById(dto.muscleId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("error.muscle.not.found", dto.muscleId()));
      movement
          .getRoleAdjustments()
          .add(
              MovementRoleAdjustment.builder()
                  .movement(movement)
                  .triggerKind(dto.triggerKind())
                  .trigger(dto.trigger())
                  .muscle(muscle)
                  .role(dto.role())
                  .build());
    }
    for (UsualNameDto dto : request.usualNames()) {
      movement
          .getUsualNames()
          .add(
              MovementUsualName.builder()
                  .movement(movement)
                  .nameEn(dto.nameEn())
                  .nameFr(dto.nameFr())
                  .searchTerms(dto.searchTerms())
                  .techniques(orEmpty(dto.techniques()))
                  .equipment(orEmpty(dto.equipment()))
                  .descriptionEn(dto.descriptionEn())
                  .descriptionFr(dto.descriptionFr())
                  .videoUrl(dto.videoUrl())
                  .build());
    }
    CatalogRules.checkMovement(movement);
  }

  private static Map<Long, List<String>> namesById(List<Object[]> rows) {
    Map<Long, List<String>> names = new HashMap<>();
    for (Object[] row : rows) {
      names.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((String) row[1]);
    }
    return names;
  }

  private Movement findMovement(Long id) {
    return movementRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("error.movement.not.found", id));
  }

  private static Set<String> orEmpty(Set<String> values) {
    return values == null ? new LinkedHashSet<>() : new LinkedHashSet<>(values);
  }

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
