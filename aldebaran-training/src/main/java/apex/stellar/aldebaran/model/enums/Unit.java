package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Standard measurement units used in training (Mass & Distance).
 *
 * <p>This unified enum handles conversion logic to the system's reference units:
 *
 * <ul>
 *   <li>For <strong>MASS</strong>: Reference is <strong>Kilograms (kg)</strong>.
 *   <li>For <strong>DISTANCE</strong>: Reference is <strong>Meters (m)</strong>.
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum Unit {

  // ==================================================================================
  // MASS UNITS (Ref: KG)
  // ==================================================================================

  /** Kilograms (Metric System - Reference for Mass). */
  KG("kg", UnitType.MASS, 1.0),

  /** Pounds (Imperial System). 1 lb = 0.453592 kg. */
  LBS("lbs", UnitType.MASS, 0.45359237),

  // ==================================================================================
  // TIME UNITS (Ref: SEC)
  // ==================================================================================

  /** Seconds (Reference for Time). */
  SECONDS("s", UnitType.TIME, 1.0),

  /** Minutes. 1 min = 60 s. */
  MINUTES("min", UnitType.TIME, 60.0),

  // ==================================================================================
  // DISTANCE UNITS (Ref: METERS)
  // ==================================================================================

  /** Meters (Metric System - Reference for Distance). */
  METERS("m", UnitType.DISTANCE, 1.0),

  /** Kilometers. 1 km = 1000 m. */
  KILOMETERS("km", UnitType.DISTANCE, 1000.0),

  /** Feet. 1 ft = 0.3048 m. */
  FEET("ft", UnitType.DISTANCE, 0.3048),

  /** Yards. 1 yd = 0.9144 m. */
  YARDS("yd", UnitType.DISTANCE, 0.9144),

  /** Miles. 1 mi = 1609.34 m. */
  MILES("mi", UnitType.DISTANCE, 1609.344);

  /** A human-readable symbol. */
  private final String symbol;

  /** The physical dimension of this unit. */
  private final UnitType type;

  /**
   * Multiplier to convert this unit TO the Base Reference (Kg or Meters). <br>
   * Formula: {@code BaseValue = Value * Factor}
   */
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

  // ==================================================================================
  // INNER ENUM: UNIT TYPE
  // ==================================================================================

  /** Defines the physical dimension of the unit. */
  public enum UnitType {
    MASS,
    TIME,
    DISTANCE
  }
}
