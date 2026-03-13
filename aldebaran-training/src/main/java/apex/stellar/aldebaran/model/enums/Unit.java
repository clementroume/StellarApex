package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Standard measurement units used in training (Mass & Distance). */
@Getter
@RequiredArgsConstructor
public enum Unit {
  // --- Mass ---
  KG(UnitType.MASS, 1.0),
  LBS(UnitType.MASS, 0.45359237),
  // --- Time ---
  SECONDS(UnitType.TIME, 1.0),
  MINUTES(UnitType.TIME, 60.0),
  // --- Distance ---
  METERS(UnitType.DISTANCE, 1.0),
  KILOMETERS(UnitType.DISTANCE, 1000.0),
  FEET(UnitType.DISTANCE, 0.3048),
  YARDS(UnitType.DISTANCE, 0.9144),
  MILES(UnitType.DISTANCE, 1609.344);

  private final UnitType type;

  private final double toBaseFactor;

  /**
   * Converts a value from this unit to the System Reference (Kg or Meters).
   *
   * @param value The value in the current unit.
   * @return The equivalent value in Kg (if Mass) or Meters (if Distance).
   */
  public double toBase(double value) {
    return value * toBaseFactor;
  }

  /**
   * Converts a value from the System Reference (Kg or Meters) to this unit.
   *
   * @param baseValue The value in Kg or Meters.
   * @return The equivalent value in this unit.
   */
  public double fromBase(double baseValue) {
    return baseValue / toBaseFactor;
  }

  /** Unit Type Inner Enum. */
  public enum UnitType {
    MASS,
    TIME,
    DISTANCE
  }
}
