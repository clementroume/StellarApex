package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * What a WOD lifts, for the per-movement records (D47, D61): its single movement and the variant
 * set of its line. Pure functions, shared by the record rebuild and the canonical lift lookup.
 *
 * <p>Variant key: the line's equipment then its techniques, each sorted and joined by {@code ,},
 * separated by {@code ;}; the equipment the movement implies is removed, so "Deadlift" and
 * "Deadlift [barbell]" are the same record. The empty key is the base version.
 */
public final class LiftVariant {

  /** Score types whose scores may carry a load (sets are only accepted on these). */
  public static final Set<ScoreType> LOAD_TYPES =
      EnumSet.of(ScoreType.WEIGHT, ScoreType.LOAD, ScoreType.NONE);

  /** Key of the base version of a movement. */
  public static final String BASE = "";

  /** Most reps an estimated 1RM is computed from (Epley, D57). */
  public static final int MAX_ESTIMATE_REPS = 10;

  /** The N RM shown on a lift, estimated when the athlete has no record for them (D63). */
  public static final List<Integer> USUAL_REPS = List.of(1, 2, 3, 5, 10);

  private LiftVariant() {}

  /**
   * The lift of a WOD.
   *
   * @param movement The movement lifted.
   * @param variantKey Its variant key.
   */
  public record Lift(Movement movement, String variantKey) {}

  /**
   * The lift of a WOD whose scores feed the records: an individual WOD scored by load (or NONE
   * with sets) with a single movement in its main part.
   *
   * @param wod The WOD, with its movements.
   * @return The lift, or empty when the WOD does not feed records.
   */
  public static Optional<Lift> of(Wod wod) {
    if (wod.getTeamSize() != null || !LOAD_TYPES.contains(wod.getScoreType())) {
      return Optional.empty();
    }
    List<WodMovement> lines = wod.getMovements();
    if (lines == null || lines.size() != 1) {
      return Optional.empty();
    }
    WodMovement line = lines.getFirst();
    if (line.getSegment() != null && line.getSegment() != Segment.MAIN) {
      return Optional.empty();
    }
    Movement movement = line.getMovement();
    return Optional.of(new Lift(movement, key(line)));
  }

  /**
   * Variant key of a WOD line, per-component techniques included ({@code 1:POWER}).
   *
   * @param line The line, with its movement.
   * @return The key, {@link #BASE} for the base version.
   */
  public static String key(WodMovement line) {
    Set<String> techniques = new TreeSet<>(line.getTechniques());
    line.getComponentTechniques()
        .forEach(c -> techniques.add(c.componentOrder() + ":" + c.technique()));
    return key(line.getMovement(), line.getEquipment(), techniques);
  }

  /**
   * Variant key of a movement, equipment and techniques.
   *
   * @param movement The movement (it decides the implied equipment).
   * @param equipment The equipment.
   * @param techniques The techniques.
   * @return The key, {@link #BASE} for the base version.
   */
  public static String key(
      Movement movement,
      @Nullable Collection<String> equipment,
      @Nullable Collection<String> techniques) {
    Set<String> tech = new TreeSet<>(techniques == null ? Set.of() : techniques);
    return compose(
        CatalogRules.visibleEquipment(
            CatalogRules.Spec.of(movement), equipment == null ? Set.of() : equipment, tech),
        tech);
  }

  /**
   * A key from what is left once the implied equipment is removed.
   *
   * @param equipment The visible equipment.
   * @param techniques The techniques.
   * @return The key.
   */
  static String compose(Collection<String> equipment, Collection<String> techniques) {
    if (equipment.isEmpty() && techniques.isEmpty()) {
      return BASE;
    }
    return String.join(",", new TreeSet<>(equipment))
        + ";"
        + String.join(",", new TreeSet<>(techniques));
  }

  /**
   * Equipment of a variant key.
   *
   * @param key A variant key.
   * @return The equipment written on the line (implied one excluded).
   */
  public static Set<String> equipmentOf(String key) {
    return part(key, 0);
  }

  /**
   * Techniques of a variant key.
   *
   * @param key A variant key.
   * @return The techniques.
   */
  public static Set<String> techniquesOf(String key) {
    return part(key, 1);
  }

  /**
   * Epley estimate of the 1RM from a set of 2 to {@value #MAX_ESTIMATE_REPS} reps (D57).
   *
   * @param weightKg The load.
   * @param reps The reps.
   * @return The estimate, or {@code null} out of range (a single is a real 1RM, not an estimate).
   */
  public static @Nullable Double epley(double weightKg, int reps) {
    if (reps < 2 || reps > MAX_ESTIMATE_REPS) {
      return null;
    }
    return weightKg * (1 + reps / 30.0);
  }

  /**
   * Epley inverted: the load an athlete with this 1RM should lift for N reps (display only).
   *
   * @param oneRepMaxKg The 1RM (real or estimated).
   * @param reps N, from 1 to {@value #MAX_ESTIMATE_REPS}.
   * @return The estimated N RM.
   */
  public static double fromOneRepMax(double oneRepMaxKg, int reps) {
    return reps <= 1 ? oneRepMaxKg : oneRepMaxKg / (1 + reps / 30.0);
  }

  private static Set<String> part(String key, int index) {
    if (key == null || key.isEmpty()) {
      return Set.of();
    }
    String[] parts = key.split(";", -1);
    if (index >= parts.length || parts[index].isEmpty()) {
      return Set.of();
    }
    return Arrays.stream(parts[index].split(","))
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
