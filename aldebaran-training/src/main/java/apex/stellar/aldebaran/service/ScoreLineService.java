package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ScoreLineRequest;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreSetRequest;
import apex.stellar.aldebaran.exception.InvalidScoreException;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.ScoreLine;
import apex.stellar.aldebaran.model.entities.ScoreSet;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.MovementRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What a score says line by line (lot S): its strength sets, each on its line of the WOD (D143),
 * and the lines done otherwise than prescribed (D142). Builds and checks them from a request,
 * derives the load of a strength score from the sets (D145), and copies them onto a teammate's
 * score. Rows are updated in place (one set per score and index, one line per score and WOD line).
 */
@Service
@RequiredArgsConstructor
public class ScoreLineService {

  private static final Set<ScoreType> SET_TYPES =
      Set.of(ScoreType.WEIGHT, ScoreType.LOAD, ScoreType.NONE);

  private final MovementService movementService;
  private final MovementRepository movementRepository;

  /**
   * Replaces the sets of a strength score and derives its performance from them: the heaviest
   * complete set for a WEIGHT score, the sum of the heaviest complete set of each line (or
   * complex) for a LOAD score (D145: CrossFit Total = best squat + best press + best deadlift).
   *
   * @param score The score.
   * @param request The request.
   * @param wod The WOD of the score, with its lines.
   * @throws InvalidScoreException if the WOD is not scored by load (or NONE), a load has no unit,
   *     or a set names no line of the WOD while the WOD has several.
   */
  public void applySets(Score score, ScoreRequest request, Wod wod) {
    if (request.sets() == null || request.sets().isEmpty()) {
      replaceSets(score, List.of());
      return;
    }
    if (!SET_TYPES.contains(wod.getScoreType())) {
      throw new InvalidScoreException("error.score.sets.type");
    }
    Unit unit = request.weightUnit();
    boolean anyWeight = request.sets().stream().anyMatch(set -> set.weight() != null);
    if (anyWeight && unit == null) {
      throw new InvalidScoreException("error.score.sets.unit.required");
    }
    Map<Object, WodMovement> groups = groups(wod);
    List<ScoreSet> wanted = new ArrayList<>();
    for (ScoreSetRequest set : request.sets()) {
      wanted.add(
          ScoreSet.builder()
              .wodMovement(lineOfSet(set.wodMovementId(), wod, groups))
              .reps(set.reps())
              .weightKg(set.weight() != null ? unit.toBase(set.weight()) : null)
              .completed(set.completed() == null || set.completed())
              .build());
    }
    replaceSets(score, wanted);
    if (unit != null) {
      score.setWeightDisplayUnit(unit);
    }
    derivePerformance(score, wod.getScoreType());
  }

  /**
   * The load of a strength score from its sets: the heaviest complete set (WEIGHT), or the sum of
   * the heaviest complete set of each line (LOAD). Without a complete loaded set, the entered value
   * stays.
   */
  static void derivePerformance(Score score, ScoreType type) {
    Map<Long, Double> bestByLine = new LinkedHashMap<>();
    for (ScoreSet set : score.getSets()) {
      if (set.isCompleted() && set.getReps() != null && set.getReps() > 0
          && set.getWeightKg() != null) {
        bestByLine.merge(set.getWodMovement().getId(), set.getWeightKg(), Math::max);
      }
    }
    if (bestByLine.isEmpty()) {
      return;
    }
    if (type == ScoreType.WEIGHT) {
      score.setMaxWeightKg(bestByLine.values().stream().mapToDouble(Double::doubleValue).max()
          .orElseThrow());
    } else if (type == ScoreType.LOAD) {
      score.setTotalLoadKg(bestByLine.values().stream().mapToDouble(Double::doubleValue).sum());
    }
  }

  /**
   * Replaces the lines of a score done otherwise than prescribed. Each line must be a line of the
   * WOD, once; its variant is written in canonical form and must be allowed by its movement, like
   * a WOD line. Loads are converted with the score's weight unit, distances and heights to meters.
   *
   * @param score The score (its weight unit already set).
   * @param request The request.
   * @param wod The WOD, with its lines.
   * @throws InvalidScoreException if a line is not a line of the WOD, comes twice, or has a load
   *     without unit.
   */
  public void applyLines(Score score, ScoreRequest request, Wod wod) {
    List<ScoreLine> wanted = new ArrayList<>();
    if (request.lines() != null) {
      Map<Long, WodMovement> lines = new HashMap<>();
      wod.getMovements().forEach(l -> lines.put(l.getId(), l));
      Set<Long> seen = new HashSet<>();
      for (ScoreLineRequest req : request.lines()) {
        WodMovement rx = lines.get(req.wodMovementId());
        if (rx == null || !seen.add(req.wodMovementId())) {
          throw new InvalidScoreException("error.score.line.invalid", req.wodMovementId());
        }
        wanted.add(toLine(req, rx, request.weightUnit()));
      }
    }
    replaceLines(score, wanted);
  }

  private ScoreLine toLine(ScoreLineRequest req, WodMovement rx, Unit weightUnit) {
    if (req.weight() != null && weightUnit == null) {
      throw new InvalidScoreException("error.score.sets.unit.required");
    }
    ScoreLine line =
        ScoreLine.builder()
            .wodMovement(rx)
            .movement(movementRepository.getReferenceById(req.movementId()))
            .equipment(req.equipment() != null ? new HashSet<>(req.equipment()) : new HashSet<>())
            .techniques(
                req.techniques() != null ? new HashSet<>(req.techniques()) : new HashSet<>())
            .repsScheme(blankToNull(req.repsScheme()))
            .weightKg(req.weight() != null ? weightUnit.toBase(req.weight()) : null)
            .distanceMeters(toMeters(req.distance(), req.distanceUnit(), rx.getDistanceUnit()))
            .calories(req.calories())
            .heightMeters(toMeters(req.height(), req.heightUnit(), rx.getHeightUnit()))
            .durationSeconds(req.durationSeconds())
            .build();
    // The variant done is checked like a WOD line, then kept in canonical form
    WodMovement done = LineVersions.done(rx, line);
    if (done != rx) {
      CatalogRules.normalizeAndCheck(
          CatalogRules.Spec.of(movementService.getMovement(req.movementId())), done, List.of());
      line.setEquipment(new HashSet<>(done.getEquipment()));
      line.setTechniques(new HashSet<>(done.getTechniques()));
    }
    return line;
  }

  /**
   * Compares the score with the prescription (D140-D142): an RX score may change nothing; a Scaled
   * score gets the Rx+ badge (no line lighter, one harder) and the official flag (every line as the
   * WOD's official Scaled version). Loads count with the box equivalences of the athlete's unit
   * (D149).
   *
   * @param score The score, its lines, division and weight unit set.
   * @param wod The WOD, with its lines.
   * @throws InvalidScoreException if an RX score changes a line.
   */
  public void applyConformity(Score score, Wod wod) {
    Division division = score.getDivision() == Division.MIXED ? null : score.getDivision();
    ScoreConformity.Result result =
        ScoreConformity.of(
            sortedLines(wod),
            score.getLines(),
            new ScoreConformity.Context(
                division, score.getWeightDisplayUnit(), UnitEquivalence.exact(wod), true));
    boolean scaled = score.getScaling() == ScalingLevel.SCALED;
    if (!scaled && !result.asPrescribed()) {
      throw new InvalidScoreException("error.score.rx.changed");
    }
    score.setRxPlus(scaled && result.rxPlus());
    score.setScaledOfficial(scaled && result.official());
  }

  private static List<WodMovement> sortedLines(Wod wod) {
    return wod.getMovements().stream()
        .sorted(Comparator.comparing(WodMovement::getOrderIndex))
        .toList();
  }

  /**
   * Copies the sets and the lines of a team score onto another member's score (one shared
   * performance, D146).
   *
   * @param from The score copied.
   * @param to The teammate's score.
   */
  public static void copy(Score from, Score to) {
    replaceSets(
        to,
        from.getSets().stream()
            .map(
                s ->
                    ScoreSet.builder()
                        .wodMovement(s.getWodMovement())
                        .reps(s.getReps())
                        .weightKg(s.getWeightKg())
                        .completed(s.isCompleted())
                        .build())
            .toList());
    replaceLines(
        to,
        from.getLines().stream()
            .map(
                l ->
                    ScoreLine.builder()
                        .wodMovement(l.getWodMovement())
                        .movement(l.getMovement())
                        .equipment(new HashSet<>(l.getEquipment()))
                        .techniques(new HashSet<>(l.getTechniques()))
                        .repsScheme(l.getRepsScheme())
                        .weightKg(l.getWeightKg())
                        .distanceMeters(l.getDistanceMeters())
                        .calories(l.getCalories())
                        .heightMeters(l.getHeightMeters())
                        .durationSeconds(l.getDurationSeconds())
                        .build())
            .toList());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * The groups of a WOD's lines in order: a complex is one group (its first line), any other line
   * its own group.
   */
  private static Map<Object, WodMovement> groups(Wod wod) {
    Map<Object, WodMovement> groups = new LinkedHashMap<>();
    wod.getMovements().stream()
        .sorted(Comparator.comparing(WodMovement::getOrderIndex))
        .forEach(
            l ->
                groups.putIfAbsent(
                    l.getComplexGroup() != null ? "complex:" + l.getComplexGroup() : l.getId(),
                    l));
    return groups;
  }

  /** The line a set belongs to: the named line (a complex: its first line), or the only group. */
  private static WodMovement lineOfSet(Long wodMovementId, Wod wod, Map<Object, WodMovement> groups) {
    if (wodMovementId == null) {
      if (groups.size() != 1) {
        throw new InvalidScoreException("error.score.set.line.required");
      }
      return groups.values().iterator().next();
    }
    WodMovement line =
        wod.getMovements().stream()
            .filter(l -> wodMovementId.equals(l.getId()))
            .findFirst()
            .orElseThrow(() -> new InvalidScoreException("error.score.line.invalid", wodMovementId));
    return line.getComplexGroup() != null ? groups.get("complex:" + line.getComplexGroup()) : line;
  }

  /** Replaces the sets, reusing the rows by position (unique per score and index). */
  private static void replaceSets(Score score, List<ScoreSet> wanted) {
    List<ScoreSet> rows = score.getSets();
    for (int i = 0; i < wanted.size(); i++) {
      ScoreSet w = wanted.get(i);
      if (i < rows.size()) {
        ScoreSet row = rows.get(i);
        row.setWodMovement(w.getWodMovement());
        row.setReps(w.getReps());
        row.setWeightKg(w.getWeightKg());
        row.setCompleted(w.isCompleted());
        row.setSetIndex(i);
      } else {
        w.setScore(score);
        w.setSetIndex(i);
        rows.add(w);
      }
    }
    while (rows.size() > wanted.size()) {
      rows.removeLast();
    }
  }

  /** Replaces the lines, reusing the row of each WOD line (unique per score and line). */
  private static void replaceLines(Score score, List<ScoreLine> wanted) {
    Map<Long, ScoreLine> existing = new HashMap<>();
    score.getLines().forEach(l -> existing.put(l.getWodMovement().getId(), l));
    List<ScoreLine> kept = new ArrayList<>();
    for (ScoreLine w : wanted) {
      ScoreLine row = existing.get(w.getWodMovement().getId());
      if (row == null) {
        w.setScore(score);
        kept.add(w);
        continue;
      }
      row.setMovement(w.getMovement());
      row.getEquipment().clear();
      row.getEquipment().addAll(w.getEquipment());
      row.getTechniques().clear();
      row.getTechniques().addAll(w.getTechniques());
      row.setRepsScheme(w.getRepsScheme());
      row.setWeightKg(w.getWeightKg());
      row.setDistanceMeters(w.getDistanceMeters());
      row.setCalories(w.getCalories());
      row.setHeightMeters(w.getHeightMeters());
      row.setDurationSeconds(w.getDurationSeconds());
      kept.add(row);
    }
    score.getLines().retainAll(kept);
    kept.stream().filter(l -> !score.getLines().contains(l)).forEach(score.getLines()::add);
  }

  private static Double toMeters(Double value, Unit unit, Unit lineUnit) {
    if (value == null) {
      return null;
    }
    Unit u = unit != null ? unit : (lineUnit != null ? lineUnit : Unit.METERS);
    return u.toBase(value);
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }
}
