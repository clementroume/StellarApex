package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ImplementSizes;
import apex.stellar.aldebaran.dto.LineEquivalents;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Unit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Equivalences between the metric and imperial systems (D149): a prescription stays in its unit
 * of origin, and its value in the other system is the one boxes use, not the exact conversion
 * (nobody loads 61 kg for 135 lb, everyone loads 60 kg). Competitions keep the exact conversion
 * rounded to the unit (the official Open tables: 135 lb / 61 kg). The same values decide whether a
 * load done counts as RX (D140). Pure functions, one fixed table per implement.
 */
public final class UnitEquivalence {

  private UnitEquivalence() {}

  /** Official competitions and box competitions keep the exact conversion. */
  private static final Set<WodCategory> OFFICIAL =
      EnumSet.of(
          WodCategory.OPEN, WodCategory.REGIONALS, WodCategory.QUARTERFINALS, WodCategory.SEMIFINALS, WodCategory.GAMES);

  /** What a load is lifted with: decides the table. */
  enum Implement {
    BARBELL,
    KETTLEBELL,
    DUMBBELL,
    MEDBALL
  }

  // Pounds -> kilograms, as boxes load them
  private static final Map<Double, Double> BARBELL_LB_KG =
      Map.ofEntries(
          Map.entry(20.0, 10.0), Map.entry(35.0, 15.0), Map.entry(45.0, 20.0), Map.entry(55.0, 25.0), Map.entry(65.0, 30.0),
          Map.entry(75.0, 35.0), Map.entry(95.0, 40.0), Map.entry(115.0, 50.0),
          Map.entry(125.0, 55.0), Map.entry(135.0, 60.0), Map.entry(155.0, 70.0),
          Map.entry(165.0, 75.0), Map.entry(185.0, 85.0), Map.entry(205.0, 92.5),
          Map.entry(225.0, 100.0), Map.entry(245.0, 110.0), Map.entry(275.0, 125.0),
          Map.entry(315.0, 140.0), Map.entry(365.0, 165.0), Map.entry(405.0, 180.0));
  // Kilograms -> pounds, as boxes load them
  private static final Map<Double, Double> BARBELL_KG_LB =
      Map.ofEntries(
          Map.entry(10.0, 20.0), Map.entry(15.0, 35.0), Map.entry(20.0, 45.0), Map.entry(25.0, 55.0), Map.entry(30.0, 65.0),
          Map.entry(35.0, 75.0), Map.entry(40.0, 95.0), Map.entry(50.0, 115.0),
          Map.entry(55.0, 125.0), Map.entry(60.0, 135.0), Map.entry(70.0, 155.0),
          Map.entry(75.0, 165.0), Map.entry(85.0, 185.0), Map.entry(100.0, 225.0),
          Map.entry(110.0, 245.0), Map.entry(125.0, 275.0), Map.entry(140.0, 315.0),
          Map.entry(180.0, 405.0));
  // Implements come in fixed sizes (D155, Rogue's range, the one boxes and the Open use): a load
  // in the other system is the nearest real size
  /** Kettlebells, kg and lb side by side. */
  static final List<Double> KETTLEBELL_KG =
      List.of(4.0, 6.0, 8.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0, 36.0, 40.0, 44.0, 48.0, 56.0,
          68.0, 80.0, 92.0);
  static final List<Double> KETTLEBELL_LB =
      List.of(9.0, 13.0, 18.0, 26.0, 35.0, 44.0, 53.0, 62.0, 70.0, 80.0, 88.0, 97.0, 106.0, 124.0,
          150.0, 176.0, 203.0);
  /** Dumbbells: 2.5 to 60 kg by 2.5; 2.5 to 30 lb by 2.5, then to 125 lb by 5. */
  static final List<Double> DUMBBELL_KG = steps(2.5, 60, 2.5);
  static final List<Double> DUMBBELL_LB = join(steps(2.5, 30, 2.5), steps(35, 125, 5));
  /** Medballs (wall balls): pounds, their kg rounded to the kilogram. */
  static final List<Double> MEDBALL_LB =
      List.of(4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 25.0, 30.0);
  static final List<Double> MEDBALL_KG =
      MEDBALL_LB.stream().map(lb -> (double) Math.round(Unit.LBS.toBase(lb))).distinct().toList();

  /** Plyo boxes: three sizes only, 20", 24" and 30" (51, 61 and 76 cm). */
  static final List<Double> BOX_IN = List.of(20.0, 24.0, 30.0);
  static final List<Double> BOX_CM = List.of(51.0, 61.0, 76.0);

  /**
   * Whether a WOD keeps the exact conversion: an official competition or a box competition.
   *
   * @param wod The WOD.
   * @return True for a competition.
   */
  public static boolean exact(Wod wod) {
    return wod != null && (wod.isCompetition() || OFFICIAL.contains(wod.getCategory()));
  }

  /**
   * The implement a line's load is lifted with.
   *
   * @param line The line.
   * @return The implement, a barbell by default.
   */
  static Implement implementOf(WodMovement line) {
    Set<String> equipment = line.getEquipment();
    if (equipment.contains("KETTLEBELL")) {
      return Implement.KETTLEBELL;
    }
    if (equipment.contains("DUMBBELL")) {
      return Implement.DUMBBELL;
    }
    if (equipment.contains("MEDICINE_BALL")) {
      return Implement.MEDBALL;
    }
    return Implement.BARBELL;
  }

  /**
   * A load in the other system.
   *
   * @param value The load.
   * @param unit Its unit (KG or LBS).
   * @param target The unit wanted.
   * @param implement What it is lifted with.
   * @param exact Whether to keep the exact conversion (competitions), rounded to the unit.
   * @return The load in {@code target}, or null for a null value.
   */
  public static @Nullable Double load(
      @Nullable Double value, Unit unit, Unit target, Implement implement, boolean exact) {
    if (value == null || unit == null) {
      return value;
    }
    if (unit == target) {
      return value;
    }
    double converted = target.fromBase(unit.toBase(value));
    if (exact) {
      return (double) Math.round(converted);
    }
    boolean toKg = target == Unit.KG;
    return switch (implement) {
      case KETTLEBELL -> nearest(converted, toKg ? KETTLEBELL_KG : KETTLEBELL_LB);
      case DUMBBELL -> nearest(converted, toKg ? DUMBBELL_KG : DUMBBELL_LB);
      case MEDBALL -> nearest(converted, toKg ? MEDBALL_KG : MEDBALL_LB);
      case BARBELL ->
          (toKg ? BARBELL_LB_KG : BARBELL_KG_LB)
              .getOrDefault(value, toKg ? roundTo(converted, 2.5) : roundTo(converted, 5));
    };
  }

  /**
   * A box or target height in the other system: a plyo box is one of its three sizes (D155), any
   * other height (a wall ball target) the conversion rounded to the centimeter or the inch.
   *
   * @param value The height.
   * @param unit Its unit.
   * @param target CENTIMETERS or INCHES.
   * @param box Whether it is a plyo box.
   * @return The height in {@code target}, or null for a null value.
   */
  public static @Nullable Double height(
      @Nullable Double value, Unit unit, Unit target, boolean box) {
    if (value == null || unit == null) {
      return value;
    }
    if (unit == target) {
      return value;
    }
    double converted = target.fromBase(unit.toBase(value));
    if (box && (target == Unit.CENTIMETERS || target == Unit.INCHES)) {
      return nearest(converted, target == Unit.CENTIMETERS ? BOX_CM : BOX_IN);
    }
    return (double) Math.round(converted);
  }

  /**
   * The range of loads (kg) that count as the prescribed one for an athlete loading in a given
   * unit (D149): from the lower to the higher of the exact value and the equivalence in that unit.
   *
   * @param value The prescribed load.
   * @param unit Its unit.
   * @param athleteUnit The unit the athlete loads in.
   * @param implement What it is lifted with.
   * @param exact Whether the WOD keeps the exact conversion.
   * @return {min, max} in kg.
   */
  public static double[] rxRangeKg(
      double value, Unit unit, Unit athleteUnit, Implement implement, boolean exact) {
    double exactKg = unit.toBase(value);
    Unit shown = athleteUnit != null ? athleteUnit : unit;
    double equivalentKg = shown.toBase(load(value, unit, shown, implement, exact));
    return new double[] {Math.min(exactKg, equivalentKg), Math.max(exactKg, equivalentKg)};
  }

  /**
   * A line's load and height in both systems (D149).
   *
   * @param line The line, with its WOD.
   * @return The equivalents, or null when the line has neither load nor height.
   */
  public static @Nullable LineEquivalents of(WodMovement line) {
    boolean load = line.getWeight() != null || line.getWeightFemale() != null;
    boolean height = line.getHeight() != null || line.getHeightFemale() != null;
    if (!load && !height) {
      return null;
    }
    boolean exact = exact(line.getWod());
    Implement implement = implementOf(line);
    Unit weightUnit = line.getWeightUnit() != null ? line.getWeightUnit() : Unit.KG;
    Unit heightUnit = line.getHeightUnit() != null ? line.getHeightUnit() : Unit.CENTIMETERS;
    boolean box = line.getEquipment().contains("BOX");
    return new LineEquivalents(
        load(line.getWeight(), weightUnit, Unit.KG, implement, exact),
        load(line.getWeightFemale(), weightUnit, Unit.KG, implement, exact),
        load(line.getWeight(), weightUnit, Unit.LBS, implement, exact),
        load(line.getWeightFemale(), weightUnit, Unit.LBS, implement, exact),
        height(line.getHeight(), heightUnit, Unit.CENTIMETERS, box),
        height(line.getHeightFemale(), heightUnit, Unit.CENTIMETERS, box),
        height(line.getHeight(), heightUnit, Unit.INCHES, box),
        height(line.getHeightFemale(), heightUnit, Unit.INCHES, box));
  }

  /** The real size nearest to a value (the lighter one on a tie). */
  private static double nearest(double value, List<Double> sizes) {
    double best = sizes.getFirst();
    for (double size : sizes) {
      if (Math.abs(size - value) < Math.abs(best - value) - 1e-9) {
        best = size;
      }
    }
    return best;
  }

  /**
   * The real sizes of the implements, in both systems (D155): the lists the WOD and score forms
   * offer.
   *
   * @return The sizes.
   */
  public static ImplementSizes sizes() {
    return new ImplementSizes(
        KETTLEBELL_KG, KETTLEBELL_LB, DUMBBELL_KG, DUMBBELL_LB, MEDBALL_KG, MEDBALL_LB, BOX_CM, BOX_IN);
  }

  private static List<Double> steps(double from, double to, double step) {
    List<Double> values = new java.util.ArrayList<>();
    for (double v = from; v <= to + 1e-9; v += step) {
      values.add(Math.round(v * 10) / 10.0);
    }
    return List.copyOf(values);
  }

  private static List<Double> join(List<Double> a, List<Double> b) {
    List<Double> values = new java.util.ArrayList<>(a);
    values.addAll(b);
    return List.copyOf(values);
  }

  private static double roundTo(double value, double step) {
    return Math.round(value / step) * step;
  }
}
