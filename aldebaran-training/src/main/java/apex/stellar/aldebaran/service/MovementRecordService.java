package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.client.AntaresClient;
import apex.stellar.aldebaran.dto.BoardRecordResponse;
import apex.stellar.aldebaran.dto.CanonicalLiftResponse;
import apex.stellar.aldebaran.dto.MovementRecordsResponse;
import apex.stellar.aldebaran.dto.RecordEntryResponse;
import apex.stellar.aldebaran.dto.RecordEstimateResponse;
import apex.stellar.aldebaran.dto.RecordPointResponse;
import apex.stellar.aldebaran.dto.RecordSourceResponse;
import apex.stellar.aldebaran.dto.VariantRecordsResponse;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementRecord;
import apex.stellar.aldebaran.model.entities.MovementUsualName;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.ScoreSet;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.repository.MovementRecordRepository;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.security.SecurityService;
import apex.stellar.aldebaran.service.LiftVariant.Lift;
import apex.stellar.aldebaran.service.RecordInheritance.Effective;
import apex.stellar.aldebaran.service.RecordInheritance.Target;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-movement records (lot C): materialized in {@code movement_records} from the scores, rebuilt
 * for an athlete and a movement whenever one of their scores on a lift WOD is written, corrected or
 * deleted (D47-D49, D62).
 *
 * <p>A record candidate is every complete set with a load of a lift WOD score (D16), or, for a
 * score without sets on an "N RM" test, its best load as an N-rep record. The heaviest wins; on a
 * tie, the earliest day (then the oldest score) keeps it. Every scaling level and unverified scores
 * count (D48); team scores never do (D49).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovementRecordService {

  private final MovementRecordRepository recordRepository;
  private final ScoreRepository scoreRepository;
  private final WodRepository wodRepository;
  private final MovementRepository movementRepository;
  private final SecurityService securityService;
  private final AntaresClient antaresClient;

  /** A record candidate from one score. */
  private record Candidate(String variantKey, int reps, double weightKg, Score score) {}

  /** Official competition events: never a canonical lift test (D65). */
  private static final Set<WodCategory> COMPETITION_CATEGORIES =
      EnumSet.of(WodCategory.OPEN, WodCategory.QUARTERFINALS, WodCategory.SEMIFINALS, WodCategory.GAMES);

  /** Heaviest first, then earliest day, then oldest score. */
  private static final Comparator<Candidate> BEST_FIRST =
      Comparator.comparingDouble(Candidate::weightKg)
          .reversed()
          .thenComparing(c -> c.score().getDate())
          .thenComparing(c -> c.score().getId());

  // -------------------------------------------------------------------------
  // Rebuild
  // -------------------------------------------------------------------------

  /**
   * Rebuilds an athlete's records of the movement a WOD lifts, if it feeds records.
   *
   * @param wod The WOD of a score just written, corrected or deleted.
   * @param userId The athlete.
   */
  @Transactional
  public void rebuildFor(Wod wod, Long userId) {
    LiftVariant.of(wod).ifPresent(lift -> rebuild(userId, lift.movement().getId()));
  }

  /**
   * Recomputes every record of an athlete on a movement from their scores.
   *
   * @param userId The athlete.
   * @param movementId The movement.
   */
  @Transactional
  public void rebuild(Long userId, Long movementId) {
    recordRepository.lockAthlete(userId);
    Candidates found = collect(userId, movementId);

    recordRepository.deleteByUserAndMovement(userId, movementId);
    if (found.byKey().isEmpty()) {
      return;
    }
    List<MovementRecord> records = new ArrayList<>();
    for (List<Candidate> group : found.byKey().values()) {
      Candidate c = group.stream().min(BEST_FIRST).orElseThrow();
      // Best load done before the day of the record (the record it beat), if any
      Double previous =
          group.stream()
              .filter(other -> other.score().getDate().isBefore(c.score().getDate()))
              .map(Candidate::weightKg)
              .max(Double::compare)
              .orElse(null);
      records.add(
          MovementRecord.builder()
              .userId(userId)
              .movement(found.movement())
              .variantKey(c.variantKey())
              .reps(c.reps())
              .weightKg(c.weightKg())
              .previousWeightKg(previous)
              .date(c.score().getDate())
              .score(c.score())
              .division(c.score().getDivision())
              .ageGroup(c.score().getAgeGroup())
              .build());
    }
    recordRepository.saveAll(records);
  }

  /** Record candidates of an athlete on a movement, grouped by variant key and reps. */
  private record Candidates(@Nullable Movement movement, Map<String, List<Candidate>> byKey) {}

  private Candidates collect(Long userId, Long movementId) {
    Map<String, List<Candidate>> byKey = new HashMap<>();
    Movement movement = null;
    for (Score score :
        scoreRepository.findRecordCandidates(userId, movementId, LiftVariant.LOAD_TYPES)) {
      if (score.getTeam() != null) {
        continue;
      }
      Optional<Lift> lift = LiftVariant.of(score.getWod());
      if (lift.isEmpty() || !Objects.equals(lift.get().movement().getId(), movementId)) {
        continue;
      }
      movement = lift.get().movement();
      for (Candidate candidate : candidates(score, lift.get().variantKey())) {
        byKey
            .computeIfAbsent(candidate.variantKey() + "#" + candidate.reps(), k -> new ArrayList<>())
            .add(candidate);
      }
    }
    return new Candidates(movement, byKey);
  }

  /**
   * Progression of the current user's N RM on a variant of a movement: each day a new best load was
   * set, oldest first, performances that count for that variant included.
   *
   * @param movementId The movement.
   * @param reps N of the N RM.
   * @param variantKey The variant.
   * @return The successive bests.
   */
  @Transactional(readOnly = true)
  public List<RecordPointResponse> getMyProgress(Long movementId, int reps, String variantKey) {
    Long userId = securityService.getCurrentUserId();
    Optional<Movement> movement = movementRepository.findById(movementId);
    if (movement.isEmpty()) {
      return List.of();
    }
    Target wanted = new Target(movement.get(), variantKey);
    List<Movement> choices = movementRepository.findChoices();
    List<Candidate> group = new ArrayList<>();
    for (Movement source : sourcesOf(movement.get())) {
      for (List<Candidate> candidates : collect(userId, source.getId()).byKey().values()) {
        Candidate first = candidates.getFirst();
        if (first.reps() == reps
            && RecordInheritance.expand(source, first.variantKey(), choices).contains(wanted)) {
          group.addAll(candidates);
        }
      }
    }
    List<Candidate> chronological =
        group.stream()
            .sorted(
                Comparator.comparing((Candidate c) -> c.score().getDate())
                    .thenComparing(c -> c.score().getId()))
            .toList();
    List<RecordPointResponse> points = new ArrayList<>();
    double best = 0;
    for (Candidate c : chronological) {
      if (c.weightKg() > best) {
        best = c.weightKg();
        RecordPointResponse point = new RecordPointResponse(c.score().getDate(), best);
        if (!points.isEmpty() && points.getLast().date().equals(point.date())) {
          points.set(points.size() - 1, point);
        } else {
          points.add(point);
        }
      }
    }
    return points;
  }

  private Set<Movement> sourcesOf(Movement movement) {
    return RecordInheritance.sources(
        movement, m -> movementRepository.findChainsCountingFor(m.getId()));
  }

  /**
   * Rebuilds every record from the scores, once, when the table is empty and scores may feed it
   * (first start after former V18, or a restored database).
   *
   * @return The number of (athlete, movement) pairs rebuilt.
   */
  @Transactional
  public int rebuildAllIfEmpty() {
    if (recordRepository.count() > 0) {
      return 0;
    }
    List<Object[]> owners = scoreRepository.findRecordOwners(LiftVariant.LOAD_TYPES);
    for (Object[] pair : owners) {
      rebuild((Long) pair[0], (Long) pair[1]);
    }
    return owners.size();
  }

  /** The record candidates of a score: its complete loaded sets, else its "N RM" load. */
  private static List<Candidate> candidates(Score score, String variantKey) {
    List<Candidate> list = new ArrayList<>();
    List<ScoreSet> sets = score.getSets() == null ? List.of() : score.getSets();
    if (!sets.isEmpty()) {
      for (ScoreSet set : sets) {
        if (set.isCompleted()
            && set.getReps() != null
            && set.getReps() > 0
            && set.getWeightKg() != null
            && set.getWeightKg() > 0) {
          list.add(new Candidate(variantKey, set.getReps(), set.getWeightKg(), score));
        }
      }
      return list;
    }
    Integer repMax = score.getWod().getRepMax();
    if (repMax != null && score.getMaxWeightKg() != null && score.getMaxWeightKg() > 0) {
      list.add(new Candidate(variantKey, repMax, score.getMaxWeightKg(), score));
    }
    return list;
  }

  // -------------------------------------------------------------------------
  // Reads
  // -------------------------------------------------------------------------

  /**
   * The current user's records, grouped by movement (by name) and variant (base version first).
   * Records are read through the "counts for" rule: an entry that another variant proves carries
   * its source.
   *
   * @param movementId Only this movement, or null for every movement.
   * @return The records.
   */
  @Transactional(readOnly = true)
  public List<MovementRecordsResponse> getMyRecords(@Nullable Long movementId) {
    Long userId = securityService.getCurrentUserId();
    List<Effective> records =
        RecordInheritance.effective(
            recordRepository.findByUserId(userId), movementRepository.findChoices());

    Map<Long, Movement> movements = new HashMap<>();
    Map<Long, Map<String, List<Effective>>> grouped = new HashMap<>();
    for (Effective r : records) {
      if (movementId != null && !movementId.equals(r.movement().getId())) {
        continue;
      }
      movements.putIfAbsent(r.movement().getId(), r.movement());
      grouped
          .computeIfAbsent(r.movement().getId(), id -> new TreeMap<>())
          .computeIfAbsent(r.variantKey(), k -> new ArrayList<>())
          .add(r);
    }

    return movements.values().stream()
        .sorted(Comparator.comparing(Movement::getName, String.CASE_INSENSITIVE_ORDER))
        .map(
            m ->
                new MovementRecordsResponse(
                    m.getId(),
                    m.getName(),
                    m.getCategory() == null ? null : m.getCategory().name(),
                    variants(m, grouped.get(m.getId()))))
        .toList();
  }

  /** Variants in key order: the base version (empty key) sorts first. */
  private static List<VariantRecordsResponse> variants(
      Movement movement, Map<String, List<Effective>> byKey) {
    List<VariantRecordsResponse> list = new ArrayList<>();
    byKey.forEach(
        (key, records) -> {
          List<Effective> sorted =
              records.stream().sorted(Comparator.comparing(e -> e.source().getReps())).toList();
          Double estimate =
              sorted.stream()
                  .map(e -> LiftVariant.epley(e.source().getWeightKg(), e.source().getReps()))
                  .filter(Objects::nonNull)
                  .max(Double::compare)
                  .orElse(null);
          list.add(
              new VariantRecordsResponse(
                  key,
                  CatalogRules.usualName(
                          movement, LiftVariant.equipmentOf(key), LiftVariant.techniquesOf(key))
                      .map(MovementUsualName::getNameEn)
                      .orElse(null),
                  LiftVariant.BASE.equals(key),
                  LiftVariant.equipmentOf(key),
                  LiftVariant.techniquesOf(key),
                  estimate,
                  sorted.stream().map(MovementRecordService::entry).toList(),
                  estimates(sorted.stream().map(Effective::source).toList(), estimate)));
        });
    return list;
  }

  private static RecordEntryResponse entry(Effective e) {
    MovementRecord r = e.source();
    Wod wod = r.getScore().getWod();
    return new RecordEntryResponse(
        r.getReps(),
        r.getWeightKg(),
        e.inherited() ? null : r.getPreviousWeightKg(),
        r.getDate(),
        r.getScore().getId(),
        wod.getId(),
        wod.getTitle(),
        source(e));
  }

  private static @Nullable RecordSourceResponse source(Effective e) {
    if (!e.inherited()) {
      return null;
    }
    MovementRecord r = e.source();
    return new RecordSourceResponse(
        r.getMovement().getId(),
        r.getMovement().getName(),
        r.getVariantKey(),
        LiftVariant.equipmentOf(r.getVariantKey()),
        LiftVariant.techniquesOf(r.getVariantKey()));
  }

  /**
   * Displayed estimates of the usual N RM the athlete has no record for (D63): Epley from the real
   * 1RM, else from the estimated 1RM. Never stored, never ranked.
   */
  private static List<RecordEstimateResponse> estimates(
      List<MovementRecord> records, @Nullable Double estimatedOneRepMax) {
    Double oneRm =
        records.stream()
            .filter(r -> r.getReps() == 1)
            .map(MovementRecord::getWeightKg)
            .findFirst()
            .orElse(estimatedOneRepMax);
    if (oneRm == null) {
      return List.of();
    }
    Set<Integer> held = new HashSet<>();
    records.forEach(r -> held.add(r.getReps()));
    List<RecordEstimateResponse> list = new ArrayList<>();
    for (int reps : LiftVariant.USUAL_REPS) {
      if (!held.contains(reps)) {
        list.add(new RecordEstimateResponse(reps, LiftVariant.fromOneRepMax(oneRm, reps)));
      }
    }
    return list;
  }

  /**
   * The canonical lifts of the catalog (D51, D65): public "N RM" tests, one per movement, variant
   * and N (the oldest when duplicated), by movement name, variant then N. Official competition
   * events (an Open 1RM clean in a time window) are not free tests: their scores still feed the
   * records, but they are never the lift's test nor its leaderboard.
   *
   * @return The lifts.
   */
  @Transactional(readOnly = true)
  public List<CanonicalLiftResponse> getCanonicalLifts() {
    Map<String, CanonicalLiftResponse> lifts = new LinkedHashMap<>();
    for (Wod wod : wodRepository.findPublicRepMaxTests()) {
      if (wod.getCategory() != null && COMPETITION_CATEGORIES.contains(wod.getCategory())) {
        continue;
      }
      LiftVariant.of(wod)
          .ifPresent(
              lift -> {
                WodMovement line = wod.getMovements().getFirst();
                Optional<MovementUsualName> usual = usualNameOf(line);
                lifts.putIfAbsent(
                    lift.movement().getId() + "#" + lift.variantKey() + "#" + wod.getRepMax(),
                    new CanonicalLiftResponse(
                        wod.getId(),
                        lift.movement().getId(),
                        lift.movement().getName(),
                        wod.getRepMax(),
                        lift.variantKey(),
                        usual.map(MovementUsualName::getNameEn).orElse(null),
                        usual.map(MovementUsualName::getNameFr).orElse(null)));
              });
    }
    return lifts.values().stream()
        .sorted(
            Comparator.comparing(CanonicalLiftResponse::movementName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CanonicalLiftResponse::variantKey)
                .thenComparing(CanonicalLiftResponse::repMax))
        .toList();
  }

  private static Optional<MovementUsualName> usualNameOf(WodMovement line) {
    String wanted = line.getMovement().getUsualNames().isEmpty() ? null : LiftVariant.key(line);
    return line.getMovement().getUsualNames().stream()
        .filter(u -> LiftVariant.key(line.getMovement(), u.getEquipment(), u.getTechniques()).equals(wanted))
        .findFirst();
  }

  /**
   * The records board of a box (D56): the records of a variant of a movement of its current usable
   * members (Antares), wherever they were set, performances that count for it included, heaviest
   * first for each number of reps.
   *
   * @param gymId The box (the caller is a member, checked by the controller).
   * @param movementId The movement.
   * @param variantKey The variant.
   * @return The board lines.
   * @throws apex.stellar.aldebaran.exception.AntaresUnavailableException if Antares cannot list
   *     the members (503).
   */
  @Transactional(readOnly = true)
  public List<BoardRecordResponse> getGymBoard(Long gymId, Long movementId, String variantKey) {
    Set<Long> members = new HashSet<>(antaresClient.getGymMemberIds(gymId));
    Optional<Movement> movement = movementRepository.findById(movementId);
    if (members.isEmpty() || movement.isEmpty()) {
      return List.of();
    }
    List<Long> sources = sourcesOf(movement.get()).stream().map(Movement::getId).toList();
    Map<Long, List<MovementRecord>> byUser = new HashMap<>();
    recordRepository
        .findForBoard(sources, members)
        .forEach(r -> byUser.computeIfAbsent(r.getUserId(), id -> new ArrayList<>()).add(r));
    List<Movement> choices = movementRepository.findChoices();
    return byUser.values().stream()
        .flatMap(stored -> RecordInheritance.effective(stored, choices).stream())
        .filter(e -> movementId.equals(e.movement().getId()) && variantKey.equals(e.variantKey()))
        .sorted(
            Comparator.comparingInt((Effective e) -> e.source().getReps())
                .thenComparing(e -> -e.source().getWeightKg())
                .thenComparing(e -> e.source().getDate()))
        .map(
            e ->
                new BoardRecordResponse(
                    e.source().getUserId(),
                    e.source().getReps(),
                    e.source().getWeightKg(),
                    e.source().getDate(),
                    e.source().getDivision(),
                    e.source().getAgeGroup(),
                    source(e)))
        .toList();
  }
}
