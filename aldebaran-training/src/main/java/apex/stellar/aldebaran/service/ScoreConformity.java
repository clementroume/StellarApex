package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.ScoreLine;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.model.enums.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * How a score compares with the prescription, line by line (D140, D141, D142): each line done is
 * equal, harder or lighter than the line it is compared with. A score with every line equal is
 * as prescribed (RX); with no line lighter and one harder, Rx+; otherwise scaled down. The same
 * comparison against the official Scaled version tells whether a Scaled score follows it. Pure
 * functions.
 */
public final class ScoreConformity {

  private ScoreConformity() {}

  /** Tolerance on numbers compared (floating conversions). */
  private static final double EPSILON = 0.01;

  /** How a line done compares with a line prescribed. */
  public enum Direction {
    EQUAL,
    HARDER,
    LIGHTER
  }

  /**
   * The context of a comparison.
   *
   * @param division The athlete's division (women's values for WOMEN, both for none).
   * @param athleteWeightUnit The unit the athlete loads in (equivalences, D149).
   * @param exact Whether the WOD keeps the exact conversion (competitions).
   * @param countedMaxLines Whether the reps of "max" lines are counts, not changes (D147).
   */
  public record Context(
      @Nullable Division division,
      @Nullable Unit athleteWeightUnit,
      boolean exact,
      boolean countedMaxLines) {}

  /**
   * The result of a score.
   *
   * @param asPrescribed Every line equal to the RX line.
   * @param rxPlus No line lighter and one harder (the Rx+ badge).
   * @param official Every line equal to the official Scaled version (false without a version).
   */
  public record Result(boolean asPrescribed, boolean rxPlus, boolean official) {}

  /**
   * Compares a score's lines with the WOD's RX lines and its official Scaled version.
   *
   * @param rxLines The WOD's lines.
   * @param done The score's changed lines.
   * @param context The context.
   * @return The result.
   */
  public static Result of(List<WodMovement> rxLines, List<ScoreLine> done, Context context) {
    List<Direction> toRx = new ArrayList<>();
    boolean hasVersion = false;
    boolean official = true;
    for (WodMovement rx : rxLines) {
      WodMovement line = LineVersions.done(rx, LineVersions.lineOf(done, rx));
      toRx.add(compare(line, rx, context));
      WodMovement scaled = LineVersions.scaled(rx);
      hasVersion |= scaled != rx;
      official &= compare(line, scaled, context) == Direction.EQUAL;
    }
    boolean asPrescribed = toRx.stream().allMatch(d -> d == Direction.EQUAL);
    boolean rxPlus =
        toRx.stream().noneMatch(d -> d == Direction.LIGHTER)
            && toRx.stream().anyMatch(d -> d == Direction.HARDER);
    return new Result(asPrescribed, rxPlus, hasVersion && official);
  }

  /**
   * How a line done compares with a line prescribed: the variant, then each quantity. Any lighter
   * part makes the line lighter; otherwise any harder part makes it harder.
   *
   * @param done The line done.
   * @param prescribed The line prescribed (RX or official Scaled).
   * @param context The context.
   * @return The direction.
   */
  static Direction compare(WodMovement done, WodMovement prescribed, Context context) {
    if (done == prescribed) {
      return Direction.EQUAL;
    }
    List<Direction> parts = new ArrayList<>();
    parts.add(variant(done, prescribed));
    parts.add(reps(done, prescribed, context));
    parts.add(load(done, prescribed, context));
    parts.add(
        number(
            meters(done.getDistance(), done.getDistanceUnit()),
            meters(prescribed.getDistance(), prescribed.getDistanceUnit())));
    parts.add(calories(done, prescribed, context.division()));
    parts.add(height(done, prescribed, context.division()));
    parts.add(
        number(
            done.getDurationSeconds() == null ? null : (double) done.getDurationSeconds(),
            prescribed.getDurationSeconds() == null
                ? null
                : (double) prescribed.getDurationSeconds()));
    if (parts.contains(Direction.LIGHTER)) {
      return Direction.LIGHTER;
    }
    return parts.contains(Direction.HARDER) ? Direction.HARDER : Direction.EQUAL;
  }

  /**
   * The variant: the same one, one that counts for it (the "counts for" rule of the catalog: a
   * Chest-to-bar Pull-up for a Pull-up) = harder, any other = lighter.
   */
  static Direction variant(WodMovement done, WodMovement prescribed) {
    boolean sameMovement =
        done.getMovement() == prescribed.getMovement()
            || (done.getMovement() != null
                && prescribed.getMovement() != null
                && Objects.equals(done.getMovement().getId(), prescribed.getMovement().getId()));
    if (!sameMovement) {
      return Direction.LIGHTER;
    }
    if (!done.getEquipment().equals(prescribed.getEquipment())) {
      return Direction.LIGHTER;
    }
    if (done.getTechniques().equals(prescribed.getTechniques())) {
      return Direction.EQUAL;
    }
    return VariantDominance.countsFor(techniques(done), techniques(prescribed))
        ? Direction.HARDER
        : Direction.LIGHTER;
  }

  private static Set<Technique> techniques(WodMovement line) {
    return line.getTechniques().stream()
        .map(Technique::valueOf)
        .collect(Collectors.toSet());
  }

  /** Reps: the numbers of the scheme compared one by one; a count on a "max" line is not a change. */
  static Direction reps(WodMovement done, WodMovement prescribed, Context context) {
    String a = normalize(done.getRepsScheme());
    String b = normalize(prescribed.getRepsScheme());
    if (a.equals(b)) {
      return Direction.EQUAL;
    }
    if (context.countedMaxLines() && "max".equals(b) && a.matches("\\d+")) {
      return Direction.EQUAL;
    }
    List<Integer> x = numbers(a);
    List<Integer> y = numbers(b);
    if (x.isEmpty() || y.isEmpty() || x.size() != y.size()) {
      return Direction.LIGHTER;
    }
    boolean less = false;
    boolean more = false;
    for (int i = 0; i < x.size(); i++) {
      less |= x.get(i) < y.get(i);
      more |= x.get(i) > y.get(i);
    }
    if (less) {
      return Direction.LIGHTER;
    }
    return more ? Direction.HARDER : Direction.EQUAL;
  }

  /**
   * The load: within the range of the prescribed one for the athlete's unit (exact value and box
   * equivalence, D149) = equal. Without a division, men's and women's loads both count.
   */
  static Direction load(WodMovement done, WodMovement prescribed, Context context) {
    Double doneKg = kg(done, context.division());
    if (prescribed.getWeight() == null && prescribed.getWeightFemale() == null) {
      // A percentage of the RM: the athlete's own load, never a change; bodyweight: a load added
      if (prescribed.getPercentOfRm() != null) {
        return Direction.EQUAL;
      }
      return doneKg == null || doneKg <= EPSILON ? Direction.EQUAL : Direction.HARDER;
    }
    if (doneKg == null) {
      return Direction.EQUAL;
    }
    double[] men = range(prescribed.getWeight(), prescribed, context);
    double[] women = range(prescribed.getWeightFemale(), prescribed, context);
    double[] lower;
    double[] upper;
    if (context.division() == Division.WOMEN) {
      lower = women != null ? women : men;
      upper = lower;
    } else if (context.division() == Division.MEN) {
      lower = men != null ? men : women;
      upper = lower;
    } else {
      lower = women != null ? women : men;
      upper = men != null ? men : women;
    }
    if (doneKg < lower[0] - EPSILON) {
      return Direction.LIGHTER;
    }
    if (doneKg > upper[1] + EPSILON) {
      return Direction.HARDER;
    }
    return Direction.EQUAL;
  }

  private static double @Nullable [] range(
      @Nullable Double value, WodMovement prescribed, Context context) {
    if (value == null) {
      return null;
    }
    Unit unit = prescribed.getWeightUnit() != null ? prescribed.getWeightUnit() : Unit.KG;
    return UnitEquivalence.rxRangeKg(
        value,
        unit,
        context.athleteWeightUnit(),
        UnitEquivalence.implementOf(prescribed),
        context.exact());
  }

  /** The load of a line for a division, in kg. */
  private static @Nullable Double kg(WodMovement line, @Nullable Division division) {
    Double value =
        division == Division.WOMEN && line.getWeightFemale() != null
            ? line.getWeightFemale()
            : line.getWeight();
    if (value == null) {
      return null;
    }
    Unit unit = line.getWeightUnit() != null ? line.getWeightUnit() : Unit.KG;
    return unit.toBase(value);
  }

  private static Direction calories(
      WodMovement done, WodMovement prescribed, @Nullable Division division) {
    return number(caloriesOf(done, division), caloriesOf(prescribed, division));
  }

  private static @Nullable Double caloriesOf(WodMovement line, @Nullable Division division) {
    Integer value =
        division == Division.WOMEN && line.getCaloriesFemale() != null
            ? line.getCaloriesFemale()
            : line.getCalories();
    return value == null ? null : value.doubleValue();
  }

  private static Direction height(
      WodMovement done, WodMovement prescribed, @Nullable Division division) {
    return number(heightOf(done, division), heightOf(prescribed, division));
  }

  private static @Nullable Double heightOf(WodMovement line, @Nullable Division division) {
    Double value =
        division == Division.WOMEN && line.getHeightFemale() != null
            ? line.getHeightFemale()
            : line.getHeight();
    if (value == null || line.getHeightUnit() == null) {
      return value;
    }
    // In meters; 24 in and 60 cm are equal within the margin of number()
    return line.getHeightUnit().toBase(value);
  }

  private static @Nullable Double meters(@Nullable Double value, @Nullable Unit unit) {
    return value == null ? null : (unit != null ? unit : Unit.METERS).toBase(value);
  }

  /** Two quantities: equal within a 2 % margin, else lighter or harder; a missing one is equal. */
  private static Direction number(@Nullable Double done, @Nullable Double prescribed) {
    if (done == null || prescribed == null) {
      return Direction.EQUAL;
    }
    double margin = Math.max(EPSILON, Math.abs(prescribed) * 0.02);
    if (done < prescribed - margin) {
      return Direction.LIGHTER;
    }
    return done > prescribed + margin ? Direction.HARDER : Direction.EQUAL;
  }

  private static String normalize(@Nullable String scheme) {
    return scheme == null ? "" : scheme.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
  }

  private static List<Integer> numbers(String scheme) {
    if (!scheme.matches("\\d+(-\\d+)*")) {
      return List.of();
    }
    List<Integer> values = new ArrayList<>();
    for (String part : scheme.split("-")) {
      values.add(Integer.parseInt(part));
    }
    return values;
  }
}
