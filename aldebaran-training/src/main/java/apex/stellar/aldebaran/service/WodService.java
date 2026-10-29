package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_WODS;

import apex.stellar.aldebaran.client.AntaresClient;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.WodCopyRequest;
import apex.stellar.aldebaran.dto.WodFacetsResponse;
import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodMovementResponse;
import apex.stellar.aldebaran.dto.WodReferenceData;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.InvalidWodException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.exception.WodLockedException;
import apex.stellar.aldebaran.mapper.WodMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.model.enums.WodScope;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.SessionBlockRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import apex.stellar.aldebaran.security.SecurityService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
  private final ScoreRepository scoreRepository;
  private final WodMapper wodMapper;
  private final SecurityService securityService;
  private final AntaresClient antaresClient;
  private final SessionBlockRepository sessionBlockRepository;

  /**
   * Retrieves a paginated list of WOD summaries based on filters.
   *
   * <p><b>Optimization:</b> Uses {@link WodSummary} projections to fetch only essential data,
   * avoiding the cost of loading full entities and their relationships.
   *
   * <p>All filters combine. The security clause (admin, public, author, member gyms) is always
   * applied on top of the requested {@link WodScope}.
   *
   * @param search Optional title search term.
   * @param type Optional WOD format filter.
   * @param category Optional WOD category filter.
   * @param movementId Optional movement ID filter.
   * @param scope List scope (ALL, MINE, GYM, PUBLIC); defaults to ALL when null.
   * @param gymId Optional gym filter, only used by the GYM scope.
   * @param pageable Pagination settings.
   * @return A list of lightweight WOD summaries.
   */
  @Transactional(readOnly = true)
  public Slice<WodSummaryResponse> getWods(
      String search,
      WodType type,
      WodCategory category,
      Long movementId,
      WodScope scope,
      Long gymId,
      Long authorId,
      Pageable pageable) {

    WodScope effectiveScope = scope != null ? scope : WodScope.ALL;

    Slice<WodSummary> projections =
        wodRepository.findSecure(
            toSearchPattern(search),
            type,
            category,
            movementId,
            effectiveScope.name(),
            gymId,
            authorId,
            securityService.getCurrentUserId(),
            securityService.getCurrentUserMemberGymIds(),
            securityService.isCurrentUserAdmin(),
            pageable);

    return projections.map(wodMapper::toSummary);
  }

  /**
   * Computes the values available to filter the WOD list.
   *
   * <p>WOD formats and categories follow every current filter except the format and category
   * themselves, so the UI only offers values that return results. Gyms and authors ignore the
   * scope, gym and author filters: they feed the admin selectors, which must keep offering the
   * other choices.
   *
   * @param search Optional title search.
   * @param scope List scope (defaults to ALL).
   * @param gymId Optional gym filter.
   * @param authorId Optional author filter (personal WODs).
   * @return The available WOD formats, categories, gym IDs and author IDs.
   */
  @Transactional(readOnly = true)
  public WodFacetsResponse getFacets(String search, WodScope scope, Long gymId, Long authorId) {
    String pattern = toSearchPattern(search);
    String effectiveScope = (scope != null ? scope : WodScope.ALL).name();
    Long userId = securityService.getCurrentUserId();
    var memberGymIds = securityService.getCurrentUserMemberGymIds();
    boolean isAdmin = securityService.isCurrentUserAdmin();

    List<WodType> types =
        wodRepository
            .findSecureWodTypes(
                pattern,
                null,
                null,
                null,
                effectiveScope,
                gymId,
                authorId,
                userId,
                memberGymIds,
                isAdmin)
            .stream()
            .sorted()
            .toList();
    List<WodCategory> categories =
        wodRepository
            .findSecureCategories(
                pattern,
                null,
                null,
                null,
                effectiveScope,
                gymId,
                authorId,
                userId,
                memberGymIds,
                isAdmin)
            .stream()
            .sorted()
            .toList();
    List<Long> gymIds =
        wodRepository
            .findSecureGymIds(
                pattern, null, null, null, "ALL", null, null, userId, memberGymIds, isAdmin)
            .stream()
            .sorted()
            .toList();
    List<Long> authorIds =
        wodRepository
            .findSecureAuthorIds(
                pattern, null, null, null, "ALL", null, null, userId, memberGymIds, isAdmin)
            .stream()
            .sorted()
            .toList();

    return new WodFacetsResponse(types, categories, gymIds, authorIds);
  }

  private static String toSearchPattern(String search) {
    return StringUtils.hasText(search) ? "%" + search.trim().toLowerCase(Locale.ROOT) + "%" : null;
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
    return wodMapper.toResponse(create(request, null, true));
  }

  /**
   * Copies a WOD (prescription, texts and movements) into a gym or as a personal WOD of the caller.
   * The copy is never public, is not locked by the scores of its source and keeps a link to it.
   *
   * @param id The ID of the WOD to copy.
   * @param request The target gym and the competition flag.
   * @return The created copy.
   * @throws ResourceNotFoundException if the source WOD does not exist.
   * @throws InvalidWodException if a competition copy has no gym, or the target gym is a virtual
   *     programming (it sells its own programming: no copies, no competitions).
   */
  @Transactional
  public WodResponse copyWod(Long id, WodCopyRequest request) {
    WodResponse source =
        wodRepository
            .findByIdWithMovements(id)
            .map(wodMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", id));
    if (request.gymId() != null && antaresClient.isProgrammingGym(request.gymId())) {
      throw new InvalidWodException("error.wod.copy.programming");
    }

    WodRequest copy =
        new WodRequest(
            source.title(),
            source.wodType(),
            source.category(),
            source.scoreType(),
            null,
            request.gymId(),
            false,
            request.competition(),
            source.descriptionEn(),
            source.descriptionFr(),
            source.notesEn(),
            source.notesFr(),
            source.tiebreakEn(),
            source.tiebreakFr(),
            source.timeCapSeconds(),
            source.repsPerRound(),
            source.emomInterval(),
            source.emomRounds(),
            source.repScheme(),
            source.movements().stream().map(WodService::toMovementRequest).toList());

    Wod saved = create(copy, id, true);
    log.info("Copied WOD id={} into WOD id={} (gym {})", id, saved.getId(), saved.getGymId());
    return wodMapper.toResponse(saved);
  }

  /**
   * Creates the WOD of a session block ({@code standalone = false}): hidden from WOD lists, read
   * and edited through its session. The caller sets its gym, visibility and competition flag.
   *
   * @param request The WOD definition.
   * @return The saved entity.
   */
  @Transactional
  public Wod createSessionWod(WodRequest request) {
    return create(request, null, false);
  }

  /**
   * Copies a standalone WOD (e.g. the public Open WOD) as the competition WOD of a session block of
   * a physical box.
   *
   * @param sourceId The WOD to copy.
   * @param gymId The box.
   * @return The saved copy ({@code standalone = false}, competition).
   * @throws InvalidWodException if the gym is a virtual program.
   */
  @Transactional
  public Wod copySessionWod(Long sourceId, Long gymId) {
    WodResponse copy = copyWod(sourceId, new WodCopyRequest(gymId, true));
    Wod saved = wodRepository.getReferenceById(copy.id());
    saved.setStandalone(false);
    return saved;
  }

  /**
   * Updates the WOD of a session block. Its session is not locked, so the WOD has no scores.
   *
   * @param id The session WOD.
   * @param request The WOD definition (ownership set by the caller).
   * @return The saved entity.
   */
  @Transactional
  @CacheEvict(value = CACHE_WODS, key = "#id")
  public Wod updateSessionWod(Long id, WodRequest request) {
    return update(id, request);
  }

  /**
   * Deletes the WOD of a session block, once no block points at it any more.
   *
   * @param id The session WOD.
   */
  @Transactional
  @CacheEvict(value = CACHE_WODS, key = "#id")
  public void deleteSessionWod(Long id) {
    wodRepository.deleteById(id);
    log.info("Deleted session WOD id={}", id);
  }

  private static WodMovementRequest toMovementRequest(WodMovementResponse wm) {
    return new WodMovementRequest(
        wm.movement().id(),
        wm.orderIndex(),
        wm.repsScheme(),
        wm.weight(),
        wm.weightFemale(),
        wm.weightUnit(),
        wm.durationSeconds(),
        wm.durationDisplayUnit(),
        wm.distance(),
        wm.distanceUnit(),
        wm.calories(),
        wm.caloriesFemale(),
        wm.height(),
        wm.heightFemale(),
        wm.heightUnit(),
        wm.equipment(),
        wm.techniques(),
        wm.notesEn(),
        wm.notesFr(),
        wm.scalingOptionsEn(),
        wm.scalingOptionsFr(),
        wm.sets());
  }

  /**
   * Builds, checks and saves a new WOD authored by the current user.
   *
   * @param request The WOD definition.
   * @param sourceWodId The WOD it was copied from, or null.
   * @param standalone False for the WOD of a session block.
   * @return The saved entity.
   */
  private Wod create(WodRequest request, Long sourceWodId, boolean standalone) {
    Long authorId = securityService.getCurrentUserId();

    // 1. Build Entity
    Wod wod = wodMapper.toEntity(request);
    wod.setAuthorId(authorId);
    wod.setSourceWodId(sourceWodId);
    wod.setStandalone(standalone);
    checkCompetition(wod);

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
              wm.checkVariantsAllowed(
                  movementResponse.name(),
                  movementResponse.equipment(),
                  movementResponse.techniques());
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

    // Mapping occurs inside the transaction (caller), avoiding LazyInitializationException
    return savedWod;
  }

  /**
   * A competition WOD belongs to a physical gym (its staff judges the scores; a virtual
   * programming has no competitions, Antares tells the gym type) and is never public: only the
   * gym's members may score it.
   *
   * @param wod The WOD about to be saved.
   * @throws InvalidWodException if the rule is broken.
   */
  private void checkCompetition(Wod wod) {
    if (!wod.isCompetition()) {
      return;
    }
    if (wod.getGymId() == null) {
      throw new InvalidWodException("error.wod.competition.gym.required");
    }
    if (wod.isPublic()) {
      throw new InvalidWodException("error.wod.competition.public");
    }
    if (antaresClient.isProgrammingGym(wod.getGymId())) {
      throw new InvalidWodException("error.wod.competition.programming");
    }
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
   * @throws InvalidWodException for the WOD of a session block (edited through its session).
   */
  @Transactional
  @CacheEvict(value = CACHE_WODS, key = "#id")
  public WodResponse updateWod(Long id, WodRequest request) {
    // 1. Integrity Check: Lock WOD if scores exist
    if (scoreRepository.existsByWodId(id)) {
      throw new WodLockedException("error.wod.locked", id);
    }
    checkStandalone(id);
    return wodMapper.toResponse(update(id, request));
  }

  /**
   * The WOD of a session block is edited and deleted through its session only.
   *
   * @param id The WOD ID.
   * @throws InvalidWodException for a session WOD.
   */
  private void checkStandalone(Long id) {
    wodRepository
        .findById(id)
        .filter(w -> !w.isStandalone())
        .ifPresent(
            w -> {
              throw new InvalidWodException("error.wod.session.owned");
            });
  }

  private Wod update(Long id, WodRequest request) {
    // 2. Fetch Existing Data (with movements to allow merging)
    Wod existingWod =
        wodRepository
            .findByIdWithMovements(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", id));

    // 3. Update Scalar Fields
    wodMapper.updateEntity(request, existingWod);
    checkCompetition(existingWod);

    // 4. Smart Merge Movements
    mergeWodMovements(existingWod, request.movements());

    // 5. Save & Return
    Wod savedWod = wodRepository.save(existingWod);
    log.info("Updated WOD id={}", savedWod.getId());

    return savedWod;
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

    if (scoreRepository.existsByWodId(id)) {
      throw new WodLockedException("error.wod.locked", id);
    }
    checkStandalone(id);
    if (sessionBlockRepository.existsByWodId(id)) {
      throw new DataConflictException("error.wod.used.by.session", id);
    }

    wodRepository.deleteById(id);
    log.info("Deleted WOD id={}", id);
  }

  /**
   * Retrieves reference data for workouts, including available WOD formats, categories, score
   * types, and unit group mappings.
   *
   * @return An instance of WodReferenceData containing lists of WOD types, score types, and a map
   *     of unit group types to their respective units.
   */
  public WodReferenceData getReferenceData() {
    List<String> wodTypes = Arrays.stream(WodType.values()).map(Enum::name).toList();
    List<String> categories = Arrays.stream(WodCategory.values()).map(Enum::name).toList();
    List<String> scoreTypes = Arrays.stream(ScoreType.values()).map(Enum::name).toList();

    Map<String, List<String>> unitGroups = new LinkedHashMap<>();
    for (Unit u : Unit.values()) {
      unitGroups.computeIfAbsent(u.getType().name(), k -> new ArrayList<>()).add(u.name());
    }

    return new WodReferenceData(wodTypes, categories, scoreTypes, unitGroups);
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

      target.checkVariantsAllowed(
          movementResponse.name(), movementResponse.equipment(), movementResponse.techniques());
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
    target.setCaloriesFemale(temp.getCaloriesFemale());
    target.setHeight(temp.getHeight());
    target.setHeightFemale(temp.getHeightFemale());
    target.setHeightUnit(temp.getHeightUnit());
    target.setWeightFemale(temp.getWeightFemale());
    target.setNotesEn(temp.getNotesEn());
    target.setNotesFr(temp.getNotesFr());
    target.setScalingOptionsEn(temp.getScalingOptionsEn());
    target.setScalingOptionsFr(temp.getScalingOptionsFr());
    target.setOrderIndex(temp.getOrderIndex());
    target.setSets(temp.getSets());

    target.getEquipment().clear();
    if (temp.getEquipment() != null) {
      target.getEquipment().addAll(temp.getEquipment());
    }

    target.getTechniques().clear();
    if (temp.getTechniques() != null) {
      target.getTechniques().addAll(temp.getTechniques());
    }
  }
}
