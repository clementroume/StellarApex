package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.ScoreSet;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.TeamMode;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement.TeamSplit;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Measure;
import apex.stellar.aldebaran.model.enums.TimeDomain;
import apex.stellar.aldebaran.model.enums.UncountedReason;
import apex.stellar.aldebaran.service.LiftVariant.Lift;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Adds an athlete's workouts to the totals of a week (lot D). Pure: the catalog comes with the
 * WODs, the records through {@link NearRecord}.
 *
 * <ul>
 *   <li>A line worth {@code s} equivalent sets gives its agonists {@code s} and its synergists
 *       {@code s / 2} in movement, its stabilizers {@code s} in holding (roles adjusted by the
 *       line's techniques, D20, D87); {@code s} goes to the line's modality (D127) and the volume.
 *   <li>Strength (For Load, a WOD scored by load, or a score with sets, D122 / D124): completed sets,
 *       warm-up sets (under half the best one) left out, a set of 1 to 5 reps worth half; one share
 *       per base movement, a complex under its name. No minutes.
 *   <li>Metcons: {@link MetconVolume}, a team line split by the team (D128), minutes per time domain
 *       (D86) outside Skill and Accessory blocks.
 * </ul>
 */
public final class BalanceCalculator {

  /** Version of these rules: a stored week of another version is rebuilt (D120). */
  public static final int RULES_VERSION = 1;

  /** A set at or above this share of the 1RM is near the record (D124). */
  public static final double NEAR_RECORD = 0.8;

  /** A set lighter than this share of the best set of its score is a warm-up (D82). */
  static final double WARM_UP = 0.5;

  /** A set of at most this many reps is worth half a set (D82). */
  static final int SHORT_SET_REPS = 5;

  private static final Set<ScoreType> LOAD_SCORES = EnumSet.of(ScoreType.WEIGHT, ScoreType.LOAD);
  private static final Set<Kind> NO_MINUTES = EnumSet.of(Kind.SKILL, Kind.ACCESSORY);

  /** Whether a load lifted on a day was near the athlete's record of that day. */
  @FunctionalInterface
  public interface NearRecord {
    /** Never near: no record known. */
    NearRecord NEVER = (movement, variantKey, date, loadKg) -> false;

    /**
     * Tests a load.
     *
     * @param movement The movement lifted.
     * @param variantKey Its variant key.
     * @param date The day of the set (its records included).
     * @param loadKg The load.
     * @return True at or above {@link #NEAR_RECORD} of the 1RM.
     */
    boolean test(Movement movement, String variantKey, LocalDate date, double loadKg);
  }

  private BalanceCalculator() {}

  /**
   * Whether a line's text is one of the forms the balance reads (D121): {@code 21}, {@code 21-15-9},
   * {@code max}, {@code 1 min}, {@code 3-6-9-...}, {@code 5 x 5}. A blank text reads the line's
   * measures instead, so it is not judged here.
   *
   * @param text The text of the line ({@code repsScheme}).
   * @return False only for a written text that cannot be read.
   */
  public static boolean readableText(@Nullable String text) {
    if (text == null || text.isBlank()) {
      return true;
    }
    return LinePrescription.of(WodMovement.builder().repsScheme(text).build(), null).readable();
  }

  /**
   * Why a line of a WOD counts nothing in the balance (D121 rule 5, D126), for the admin control. A
   * strength WOD counts its sets: only an unreadable text is reported there.
   *
   * @param wod The WOD (a Death by line without text climbs its ladder).
   * @param line The line, with its movement and usual names.
   * @return The reason, or null when the line counts.
   */
  public static @Nullable UncountedReason uncounted(Wod wod, WodMovement line) {
    LinePrescription p = MetconVolume.prescription(wod, line, null);
    if (!p.readable()) {
      return UncountedReason.UNREADABLE;
    }
    if (isStrength(wod, null)) {
      return null; // strength counts sets, never the value of one set
    }
    return p.measure().of(CatalogRules.perSet(line)) == null ? UncountedReason.NO_SET_VALUE : null;
  }

  /**
   * The vector of some workouts: sets per muscle (moving, holding), per modality, metcon seconds
   * per time domain, strength sets per lift. Contract shared with lot D' (session order): it does
   * not care whether the workouts were done (a score) or prescribed (no score), and it is linear,
   * so the vector of a window of days is the {@link #sum} of its sessions' vectors.
   *
   * @param workouts The workouts.
   * @param nearRecord The athlete's records, or {@link NearRecord#NEVER} for a prescription.
   * @return Their vector.
   */
  public static WeekTotals vectorOf(Collection<Workout> workouts, NearRecord nearRecord) {
    WeekTotals totals = new WeekTotals();
    workouts.forEach(workout -> add(workout, nearRecord, totals));
    return totals;
  }

  /**
   * The sum of vectors (sessions of a window, weeks of a month).
   *
   * @param vectors The vectors.
   * @return Their sum.
   */
  public static WeekTotals sum(Collection<WeekTotals> vectors) {
    WeekTotals sum = new WeekTotals();
    vectors.forEach(sum::addAll);
    return sum;
  }

  /**
   * Adds one workout to a week.
   *
   * @param workout The workout.
   * @param nearRecord The records of the athlete.
   * @param totals The week.
   */
  public static void add(Workout workout, NearRecord nearRecord, WeekTotals totals) {
    Wod wod = workout.wod();
    if (wod.getMovements().isEmpty()) {
      return;
    }
    if (isStrength(wod, workout.score())) {
      addStrength(workout, nearRecord, totals);
    } else {
      addMetcon(workout, totals);
    }
  }

  /**
   * Whether a workout counts as strength: For Load, a WOD scored by load, or a score with sets
   * (sets come before the format: an EMOM with sets is strength, D122, D124).
   *
   * @param wod The WOD.
   * @param score The score, or null.
   * @return True for strength.
   */
  static boolean isStrength(Wod wod, @Nullable Score score) {
    return wod.getWodType() == WodType.FOR_LOAD
        || LOAD_SCORES.contains(wod.getScoreType())
        || (score != null && score.getSets() != null && !score.getSets().isEmpty());
  }

  // -------------------------------------------------------------------------
  // Metcons
  // -------------------------------------------------------------------------

  private static void addMetcon(Workout workout, WeekTotals totals) {
    Wod wod = workout.wod();
    double divisor = teamDivisor(workout);
    Map<WodMovement, Double> sets = MetconVolume.sets(wod, workout.score(), workout.division());
    sets.forEach(
        (line, s) -> {
          double share = line.getTeamSplit() == TeamSplit.TOTAL || line.getTeamSplit() == null ? s / divisor : s;
          addLine(line, share, totals);
        });
    if (workout.blockKind() == null || !NO_MINUTES.contains(workout.blockKind())) {
      int seconds = MetconVolume.workSeconds(wod, workout.score(), MetconVolume.rounds(wod, workout.score()));
      if (seconds > 0) {
        totals.addSeconds(domain(wod, seconds), seconds);
      }
    }
  }

  /**
   * The share of a team line: the smaller of the prescribed size and the members (a mirror copies a
   * partner, a short team does it all, D128).
   */
  private static double teamDivisor(Workout workout) {
    Integer size = workout.wod().getTeamSize();
    if (size == null || size < 2) {
      return 1;
    }
    int members = workout.teamMembers() > 0 ? workout.teamMembers() : size;
    return Math.max(1, Math.min(size, members));
  }

  private static TimeDomain domain(Wod wod, int seconds) {
    boolean intervals =
        wod.getWodType() == WodType.EMOM
            || wod.getWodType() == WodType.TABATA
            || wod.getRestSeconds() != null
            || (wod.getTeamSize() != null && wod.getTeamMode() == TeamMode.YOU_GO_I_GO);
    return intervals ? TimeDomain.INTERVALS : TimeDomain.ofContinuous(seconds);
  }

  // -------------------------------------------------------------------------
  // Strength
  // -------------------------------------------------------------------------

  /** One strength set: its reps (null if unknown) and its load (null if unknown). */
  private record StrengthSet(@Nullable Integer reps, @Nullable Double loadKg) {
    double worth() {
      return reps != null && reps <= SHORT_SET_REPS ? 0.5 : 1;
    }
  }

  private static void addStrength(Workout workout, NearRecord nearRecord, WeekTotals totals) {
    Wod wod = workout.wod();
    Score score = workout.score();
    List<WodMovement> lines =
        wod.getMovements().stream().sorted(Comparator.comparing(WodMovement::getOrderIndex)).toList();
    Map<Object, List<WodMovement>> groups = new LinkedHashMap<>();
    for (WodMovement line : lines) {
      Object key = line.getComplexGroup() != null ? "complex:" + line.getComplexGroup() : line;
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
    }
    List<StrengthSet> entered = entered(score);
    if (groups.size() == 1 && !entered.isEmpty()) {
      // One movement or one complex: the athlete's sets (D122 rules 2 and 3)
      List<WodMovement> group = groups.values().iterator().next();
      Integer roundReps = group.size() > 1 ? roundReps(group, workout) : null;
      for (StrengthSet set : entered) {
        StrengthSet counted = roundReps != null ? new StrengthSet(roundReps, set.loadKg()) : set;
        boolean near = group.size() == 1 && near(wod, workout.date(), set.loadKg(), nearRecord);
        addGroup(group, counted.worth(), near, totals);
      }
      return;
    }
    // No sets, or several lines: the prescription (D122 rule 4), the score's load for one line
    for (List<WodMovement> group : groups.values()) {
      List<StrengthSet> prescribed = prescribedSets(group, workout);
      boolean near = groups.size() == 1 && group.size() == 1 && nearWithoutSets(wod, group.getFirst(), workout, nearRecord);
      for (StrengthSet set : prescribed) {
        addGroup(group, set.worth(), near, totals);
      }
    }
  }

  /** Completed sets of a score, warm-up sets left out (D82). */
  private static List<StrengthSet> entered(@Nullable Score score) {
    if (score == null || score.getSets() == null) {
      return List.of();
    }
    List<ScoreSet> done = score.getSets().stream().filter(ScoreSet::isCompleted).toList();
    double best =
        done.stream()
            .map(ScoreSet::getWeightKg)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0);
    return done.stream()
        .filter(s -> s.getWeightKg() == null || best <= 0 || s.getWeightKg() >= WARM_UP * best)
        .map(s -> new StrengthSet(s.getReps(), s.getWeightKg()))
        .toList();
  }

  /** Reps of one round of a complex: the sum of its movements' prescribed reps. */
  private static @Nullable Integer roundReps(List<WodMovement> group, Workout workout) {
    int total = 0;
    for (WodMovement line : group) {
      LinePrescription p = LinePrescription.of(line, workout.division());
      if (!p.readable() || p.measure() != Measure.REPS) {
        return null;
      }
      total += (int) p.valueAt(0);
    }
    return total;
  }

  /** Prescribed sets of a movement or a complex: {@code sets} × reps, a scheme, {@code N x M}. */
  private static List<StrengthSet> prescribedSets(List<WodMovement> group, Workout workout) {
    WodMovement first = group.getFirst();
    List<StrengthSet> sets = new ArrayList<>();
    if (group.size() > 1) {
      Integer reps = roundReps(group, workout);
      int count = first.getSets() != null && first.getSets() > 0 ? first.getSets() : 1;
      for (int i = 0; i < count; i++) {
        sets.add(new StrengthSet(reps, null));
      }
      return sets;
    }
    LinePrescription p = LinePrescription.of(first, workout.division());
    List<Double> values = p.readable() && p.measure() == Measure.REPS ? p.values() : List.of();
    if (values.isEmpty() && workout.wod().getRepMax() != null) {
      values = List.of((double) workout.wod().getRepMax());
    }
    if (values.isEmpty()) {
      return sets;
    }
    int repeat = first.getSets() != null && first.getSets() > 0 && values.size() == 1 ? first.getSets() : 1;
    for (int r = 0; r < repeat; r++) {
      for (double value : values) {
        sets.add(new StrengthSet((int) value, null));
      }
    }
    return sets;
  }

  /** A movement's sets, or a complex's rounds: each muscle at its strongest role in the complex. */
  private static void addGroup(List<WodMovement> group, double sets, boolean near, WeekTotals totals) {
    if (group.size() == 1) {
      WodMovement line = group.getFirst();
      addLine(line, sets, totals);
      totals.addLift(line.getMovement().getName(), line.getMovement().getId(), sets, near);
      return;
    }
    Map<Muscle, MuscleRole> roles = new HashMap<>();
    Modality modality = null;
    for (WodMovement line : group) {
      CatalogRules.effectiveRoles(line.getMovement(), line.getTechniques(), line.getEquipment())
          .forEach((muscle, role) -> roles.merge(muscle, role, BalanceCalculator::strongest));
      Modality own = CatalogRules.modality(line);
      if (modality == null || own == Modality.WEIGHTLIFTING) {
        modality = own;
      }
    }
    addRoles(roles, sets, totals);
    totals.addSets(modality, sets);
    String label =
        group.stream().map(CatalogRules::listName).collect(Collectors.joining(" + "));
    totals.addLift(label.length() > 200 ? label.substring(0, 200) : label, null, sets, false);
  }

  private static MuscleRole strongest(MuscleRole a, MuscleRole b) {
    return a.ordinal() <= b.ordinal() ? a : b;
  }

  /** A set of the single movement lifted, against the record of its day. */
  private static boolean near(Wod wod, LocalDate date, @Nullable Double loadKg, NearRecord nearRecord) {
    if (loadKg == null || loadKg <= 0) {
      return false;
    }
    Optional<Lift> lift = LiftVariant.of(wod);
    return lift.isPresent() && nearRecord.test(lift.get().movement(), lift.get().variantKey(), date, loadKg);
  }

  /** Without sets: the score's load as one set, else the prescribed percentage (D124 rule 5). */
  private static boolean nearWithoutSets(
      Wod wod, WodMovement line, Workout workout, NearRecord nearRecord) {
    Score score = workout.score();
    if (score != null && score.getMaxWeightKg() != null) {
      return near(wod, workout.date(), score.getMaxWeightKg(), nearRecord);
    }
    if (line.getPercentOfRm() != null) {
      int rmReps = line.getRmReps() != null ? line.getRmReps() : 1;
      double ofOneRm = line.getPercentOfRm() / 100.0 / (rmReps > 1 ? 1 + rmReps / 30.0 : 1);
      return ofOneRm >= NEAR_RECORD;
    }
    return false;
  }

  // -------------------------------------------------------------------------
  // Muscles
  // -------------------------------------------------------------------------

  private static void addLine(WodMovement line, double sets, WeekTotals totals) {
    if (sets <= 0) {
      return;
    }
    addRoles(
        CatalogRules.effectiveRoles(line.getMovement(), line.getTechniques(), line.getEquipment()),
        sets,
        totals);
    totals.addSets(CatalogRules.modality(line), sets);
  }

  private static void addRoles(Map<Muscle, MuscleRole> roles, double sets, WeekTotals totals) {
    roles.forEach(
        (muscle, role) -> {
          switch (role) {
            case AGONIST -> totals.addMuscle(muscle.getId(), sets, 0);
            case SYNERGIST -> totals.addMuscle(muscle.getId(), sets / 2, 0);
            case STABILIZER -> totals.addMuscle(muscle.getId(), 0, sets);
          }
        });
  }
}
