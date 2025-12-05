package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Categorizes movements into functional patterns.
 *
 * <p>The hierarchy is defined as: <strong>Movement -> Family -> Modality</strong>. Example: "Squat
 * Clean" -> "Clean" -> Weightlifting.
 *
 * <p>This structure enables program analysis (e.g. detecting biases), performance tracking, and
 * filtering based on the unified {@link Modality}.
 */
@Getter
@RequiredArgsConstructor
public enum Category {

  // --- Weightlifting (WL) ---
  DEADLIFT("Deadlift", Modality.WEIGHTLIFTING, "DL"),
  SQUAT("Squat", Modality.WEIGHTLIFTING, "SQ"),
  PRESS_AND_JERK("Press & Jerk", Modality.WEIGHTLIFTING, "PJ"),
  CLEAN("Clean", Modality.WEIGHTLIFTING, "CL"),
  SNATCH("Snatch", Modality.WEIGHTLIFTING, "SN"),
  COMPLEXES("Complexes", Modality.WEIGHTLIFTING, "CX"),
  LUNGES("Lunges", Modality.WEIGHTLIFTING, "LG"),
  SWING("Swing", Modality.WEIGHTLIFTING, "SW"),
  OTHER_LIFTS("Other Lifts", Modality.WEIGHTLIFTING, "OL"),

  // --- Gymnastics (GY) ---
  PULLING("Pulling (Gym)", Modality.GYMNASTICS, "PU"),
  PUSHING("Pushing (Gym)", Modality.GYMNASTICS, "PS"),
  INVERTED("Inverted", Modality.GYMNASTICS, "IV"),
  CORE("Core", Modality.GYMNASTICS, "CO"),
  LOCOMOTION_AND_BODY_CONTROL("Locomotion & Body Control", Modality.GYMNASTICS, "LB"),

  // --- Monostructural (Metabolic Conditioning) ---
  CARDIO("Cardio (Run/Swim)", Modality.MONOSTRUCTURAL, "CA"),
  CARDIO_MACHINES("Cardio Machines", Modality.MONOSTRUCTURAL, "CM"),
  BURPEES("Burpees", Modality.MONOSTRUCTURAL, "BP"),
  JUMPING("Jumping", Modality.MONOSTRUCTURAL, "JP"),

  // --- Strongman (Odd Objects) ---
  THROWS_AND_SLAMS("Throws & Slams", Modality.STRONGMAN, "TS"),
  CARRY("Carry", Modality.STRONGMAN, "CY"),
  STRONGMAN_LIFTS("Strongman Lifts", Modality.STRONGMAN, "SL"),
  SLED("Sled", Modality.STRONGMAN, "SD");

  /** A human-readable name suitable for UI display. */
  private final String displayName;

  /** The overarching training modality this family belongs to. */
  private final Modality modality;

  /** A short code used for generating semantic IDs (e.g., "SQ" in "WL-SQ-001"). */
  private final String code;

  /**
   * Builds the semantic prefix used for movement business IDs. Example: "WL-SQ" for Weightlifting
   * Squats.
   */
  public String semanticIdPrefix() {
    return modality.getCode() + "-" + code;
  }

  // ==================================================================================
  // INNER ENUM: MODALITY
  // ==================================================================================

  /** Represents the high-level training modality of a movement family. */
  @Getter
  @RequiredArgsConstructor
  public enum Modality {
    /** Moving external loads (barbells, dumbbells) to generate force. */
    WEIGHTLIFTING("Weightlifting", "WL"),

    /** Moving body weight through space or on apparatus. */
    GYMNASTICS("Gymnastics", "GY"),

    /** Cyclical movements designed to tax energy systems (Cardio). */
    MONOSTRUCTURAL("Monostructural", "MO"),

    /** Functional movements often involving odd objects. */
    STRONGMAN("Strongman", "SM");

    private final String displayName;
    private final String code;

    /** Checks if this modality typically involves tracking tonnage (Load * Reps). */
    public boolean isLoadBearing() {
      return this == WEIGHTLIFTING || this == STRONGMAN;
    }
  }
}
