package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the fundamental training modalities in functional fitness.
 *
 * <p>This enumeration serves as the top-level categorization for both {@link MovementFamily} and
 * {@link Equipment}. It ensures consistency across the domain when analyzing workout composition
 * (e.g., "50% Weightlifting, 30% Gymnastics").
 */
@Getter
@RequiredArgsConstructor
public enum Modality {

  /**
   * Movements involving external loads (barbells, dumbbells, kettlebells) to generate force.
   * Focuses on absolute strength and power.
   */
  WEIGHTLIFTING("Weightlifting"),

  /**
   * Movements involving moving one's own body weight through space or on apparatus. Focuses on
   * relative strength, coordination, balance, and body control. (Includes Bodyweight movements).
   */
  GYMNASTICS("Gymnastics"),

  /**
   * Cyclical, repetitive movements designed primarily to tax the aerobic and anaerobic energy
   * systems. Often referred to as "Cardio" or "MetCon".
   */
  MONOSTRUCTURAL("Monostructural"),

  /**
   * Functional movements often involving "odd objects" (sandbags, stones, sleds) or awkward loads,
   * focusing on real-world application of strength.
   */
  STRONGMAN("Strongman");

  /** A human-readable name suitable for UI display. */
  private final String displayName;

  /**
   * Checks if this modality typically involves tracking tonnage (Load * Reps).
   *
   * @return true for Weightlifting and Strongman.
   */
  public boolean isLoadBearing() {
    return this == WEIGHTLIFTING || this == STRONGMAN;
  }
}
