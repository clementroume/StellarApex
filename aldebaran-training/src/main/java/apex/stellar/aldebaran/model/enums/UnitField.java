package apex.stellar.aldebaran.model.enums;

import static apex.stellar.aldebaran.model.enums.Unit.CENTIMETERS;
import static apex.stellar.aldebaran.model.enums.Unit.FEET;
import static apex.stellar.aldebaran.model.enums.Unit.INCHES;
import static apex.stellar.aldebaran.model.enums.Unit.KG;
import static apex.stellar.aldebaran.model.enums.Unit.KILOMETERS;
import static apex.stellar.aldebaran.model.enums.Unit.LBS;
import static apex.stellar.aldebaran.model.enums.Unit.METERS;
import static apex.stellar.aldebaran.model.enums.Unit.MILES;
import static apex.stellar.aldebaran.model.enums.Unit.MINUTES;
import static apex.stellar.aldebaran.model.enums.Unit.SECONDS;
import static apex.stellar.aldebaran.model.enums.Unit.YARDS;

import java.util.EnumSet;
import java.util.Set;

/**
 * The kind of value a unit field holds, and the units it accepts (D150). The same lists are the
 * {@code CHECK} constraints of the unit columns (migration V10).
 */
public enum UnitField {
  /** A load or a vest weight. */
  MASS(EnumSet.of(KG, LBS)),
  /** A duration shown in seconds or minutes. */
  TIME(EnumSet.of(SECONDS, MINUTES)),
  /** A distance covered. */
  DISTANCE(EnumSet.of(METERS, KILOMETERS, CENTIMETERS, INCHES, FEET, YARDS, MILES)),
  /** A box or target height. */
  HEIGHT(EnumSet.of(METERS, CENTIMETERS, INCHES, FEET)),
  /** The length of a course (shuttle run). */
  LENGTH(EnumSet.of(METERS, CENTIMETERS, INCHES, FEET, YARDS));

  private final Set<Unit> allowed;

  UnitField(Set<Unit> allowed) {
    this.allowed = allowed;
  }

  /**
   * Whether a unit fits this kind of field.
   *
   * @param unit The unit, or null (no unit: accepted).
   * @return True when the unit is null or accepted.
   */
  public boolean accepts(Unit unit) {
    return unit == null || allowed.contains(unit);
  }
}
