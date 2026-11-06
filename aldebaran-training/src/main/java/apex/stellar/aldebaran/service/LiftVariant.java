package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
import apex.stellar.aldebaran.model.enums.Equipment;
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
 * separated by {@code ;}; the implied equipment is removed (the barbell when the movement allows
 * it, else its only equipment), so "Deadlift" and "Deadlift [barbell]" are the same record. The
 * empty key is the base version.
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
    return Optional.of(
        new Lift(movement, key(movement, line.getEquipment(), line.getTechniques())));
  }

  /**
   * Variant key of a WOD line.
   *
   * @param movement The movement (its allowed equipment decides the implied one).
   * @param equipment The line's equipment.
   * @param techniques The line's techniques.
   * @return The key, {@link #BASE} for the base version.
   */
  public static String key(
      Movement movement, @Nullable Collection<String> equipment, @Nullable Collection<String> techniques) {
    String implied = impliedEquipment(movement);
    Set<String> eq = new TreeSet<>(equipment == null ? Set.of() : equipment);
    if (implied != null) {
      eq.remove(implied);
    }
    Set<String> tech = new TreeSet<>(techniques == null ? Set.of() : techniques);
    if (eq.isEmpty() && tech.isEmpty()) {
      return BASE;
    }
    return String.join(",", eq) + ";" + String.join(",", tech);
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

  private static @Nullable String impliedEquipment(Movement movement) {
    Set<Equipment> allowed = movement.getEquipment();
    if (allowed == null || allowed.isEmpty()) {
      return null;
    }
    if (allowed.contains(Equipment.BARBELL)) {
      return Equipment.BARBELL.name();
    }
    return allowed.size() == 1 ? allowed.iterator().next().name() : null;
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
