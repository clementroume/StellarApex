package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.client.AntaresClient;
import apex.stellar.aldebaran.dto.BoardRecordResponse;
import apex.stellar.aldebaran.dto.CanonicalLiftResponse;
import apex.stellar.aldebaran.dto.MovementRecordsResponse;
import apex.stellar.aldebaran.dto.RecordEntryResponse;
import apex.stellar.aldebaran.dto.VariantRecordsResponse;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementRecord;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.ScoreSet;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.MovementRecordRepository;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.security.SecurityService;
import apex.stellar.aldebaran.service.LiftVariant.Lift;
import java.util.ArrayList;
import java.util.Comparator;
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
  private final SecurityService securityService;
  private final AntaresClient antaresClient;

  /** A record candidate from one score. */
  private record Candidate(String variantKey, int reps, double weightKg, Score score) {}

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
    Map<String, Candidate> best = new HashMap<>();
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
        best.merge(
            candidate.variantKey() + "#" + candidate.reps(),
            candidate,
            (a, b) -> BEST_FIRST.compare(a, b) <= 0 ? a : b);
      }
    }

    recordRepository.deleteByUserAndMovement(userId, movementId);
    if (best.isEmpty()) {
      return;
    }
    List<MovementRecord> records = new ArrayList<>();
    for (Candidate c : best.values()) {
      records.add(
          MovementRecord.builder()
              .userId(userId)
              .movement(movement)
              .variantKey(c.variantKey())
              .reps(c.reps())
              .weightKg(c.weightKg())
              .date(c.score().getDate())
              .score(c.score())
              .division(c.score().getDivision())
              .ageGroup(c.score().getAgeGroup())
              .build());
    }
    recordRepository.saveAll(records);
  }

  /**
   * Rebuilds every record from the scores, once, when the table is empty and scores may feed it
   * (first start after V18, or a restored database).
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
   *
   * @param movementId Only this movement, or null for every movement.
   * @return The records.
   */
  @Transactional(readOnly = true)
  public List<MovementRecordsResponse> getMyRecords(@Nullable Long movementId) {
    Long userId = securityService.getCurrentUserId();
    List<MovementRecord> records =
        movementId == null
            ? recordRepository.findByUserId(userId)
            : recordRepository.findByUserIdAndMovementId(userId, movementId);

    Map<Long, Movement> movements = new HashMap<>();
    Map<Long, Map<String, List<MovementRecord>>> grouped = new HashMap<>();
    for (MovementRecord r : records) {
      movements.putIfAbsent(r.getMovement().getId(), r.getMovement());
      grouped
          .computeIfAbsent(r.getMovement().getId(), id -> new TreeMap<>())
          .computeIfAbsent(r.getVariantKey(), k -> new ArrayList<>())
          .add(r);
    }

    return movements.values().stream()
        .sorted(Comparator.comparing(Movement::getName, String.CASE_INSENSITIVE_ORDER))
        .map(m -> new MovementRecordsResponse(m.getId(), m.getName(), variants(grouped.get(m.getId()))))
        .toList();
  }

  /** Variants in key order: the base version (empty key) sorts first. */
  private static List<VariantRecordsResponse> variants(Map<String, List<MovementRecord>> byKey) {
    List<VariantRecordsResponse> list = new ArrayList<>();
    byKey.forEach(
        (key, records) -> {
          List<MovementRecord> sorted =
              records.stream().sorted(Comparator.comparing(MovementRecord::getReps)).toList();
          Double estimate =
              sorted.stream()
                  .map(r -> LiftVariant.epley(r.getWeightKg(), r.getReps()))
                  .filter(Objects::nonNull)
                  .max(Double::compare)
                  .orElse(null);
          list.add(
              new VariantRecordsResponse(
                  key,
                  LiftVariant.BASE.equals(key),
                  LiftVariant.equipmentOf(key),
                  LiftVariant.techniquesOf(key),
                  estimate,
                  sorted.stream().map(MovementRecordService::entry).toList()));
        });
    return list;
  }

  private static RecordEntryResponse entry(MovementRecord r) {
    Wod wod = r.getScore().getWod();
    return new RecordEntryResponse(
        r.getReps(), r.getWeightKg(), r.getDate(), r.getScore().getId(), wod.getId(), wod.getTitle());
  }

  /**
   * The canonical lifts of the catalog (D51): public "N RM" tests of a base-version movement, one
   * per movement and N (the oldest when duplicated), by movement name then N.
   *
   * @return The lifts.
   */
  @Transactional(readOnly = true)
  public List<CanonicalLiftResponse> getCanonicalLifts() {
    Map<String, CanonicalLiftResponse> lifts = new LinkedHashMap<>();
    for (Wod wod : wodRepository.findPublicRepMaxTests()) {
      LiftVariant.of(wod)
          .filter(lift -> LiftVariant.BASE.equals(lift.variantKey()))
          .ifPresent(
              lift ->
                  lifts.putIfAbsent(
                      lift.movement().getId() + "#" + wod.getRepMax(),
                      new CanonicalLiftResponse(
                          wod.getId(),
                          lift.movement().getId(),
                          lift.movement().getName(),
                          wod.getRepMax())));
    }
    return lifts.values().stream()
        .sorted(
            Comparator.comparing(CanonicalLiftResponse::movementName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CanonicalLiftResponse::repMax))
        .toList();
  }

  /**
   * The records board of a box (D56): the base-version records of a movement of its current usable
   * members (Antares), wherever they were set, heaviest first for each number of reps.
   *
   * @param gymId The box (the caller is a member, checked by the controller).
   * @param movementId The movement.
   * @return The board lines.
   * @throws apex.stellar.aldebaran.exception.AntaresUnavailableException if Antares cannot list
   *     the members (503).
   */
  @Transactional(readOnly = true)
  public List<BoardRecordResponse> getGymBoard(Long gymId, Long movementId) {
    Set<Long> members = new HashSet<>(antaresClient.getGymMemberIds(gymId));
    if (members.isEmpty()) {
      return List.of();
    }
    return recordRepository.findBaseRecords(movementId, members).stream()
        .map(
            r ->
                new BoardRecordResponse(
                    r.getUserId(),
                    r.getReps(),
                    r.getWeightKg(),
                    r.getDate(),
                    r.getDivision(),
                    r.getAgeGroup()))
        .toList();
  }
}
