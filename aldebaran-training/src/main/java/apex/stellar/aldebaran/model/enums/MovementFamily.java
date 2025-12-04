package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Categorizes movements into functional patterns.
 *
 * <p>The hierarchy is defined as: <strong>Movement -> Family -> {@link Modality}</strong>. Example:
 * "Fran Thruster" -> "Squat" / "Press" -> Weightlifting.
 *
 * <p>This structure enables program analysis (e.g. detecting biases), performance tracking, and
 * filtering based on the unified {@link Modality}.
 */
@Getter
@RequiredArgsConstructor
public enum MovementFamily {

  // --- Weightlifting (External Load) ---
  DEADLIFT("Deadlift", Modality.WEIGHTLIFTING),
  SQUAT("Squat", Modality.WEIGHTLIFTING),
  PRESS_AND_JERK("Press & Jerk", Modality.WEIGHTLIFTING),
  CLEAN("Clean", Modality.WEIGHTLIFTING),
  SNATCH("Snatch", Modality.WEIGHTLIFTING),
  COMPLEXES("Complexes", Modality.WEIGHTLIFTING),
  LUNGES("Lunges", Modality.WEIGHTLIFTING),
  SWING("Swing", Modality.WEIGHTLIFTING),
  OTHER_LIFTS("Other Lifts", Modality.WEIGHTLIFTING),

  // --- Gymnastics (Bodyweight) ---
  PULLING("Pulling (Gym)", Modality.GYMNASTICS),
  PUSHING("Pushing (Gym)", Modality.GYMNASTICS),
  INVERTED("Inverted", Modality.GYMNASTICS),
  CORE("Core", Modality.GYMNASTICS),
  LOCOMOTION_AND_BODY_CONTROL("Locomotion & Control", Modality.GYMNASTICS),

  // --- Monostructural (Metabolic Conditioning) ---
  CARDIO("Cardio (Run/Swim)", Modality.MONOSTRUCTURAL),
  CARDIO_MACHINES("Cardio Machines", Modality.MONOSTRUCTURAL),
  BURPEES("Burpees", Modality.MONOSTRUCTURAL),
  JUMPING("Jumping", Modality.MONOSTRUCTURAL),

  // --- Strongman (Odd Objects) ---
  THROWS_AND_SLAMS("Throws & Slams", Modality.STRONGMAN),
  CARRY("Carry", Modality.STRONGMAN),
  STRONGMAN_LIFTS("Strongman Lifts", Modality.STRONGMAN),
  SLED("Sled", Modality.STRONGMAN);

  /** A human-readable name suitable for UI display. */
  private final String displayName;

  /** The overarching training modality this family belongs to. */
  private final Modality modality;
}
