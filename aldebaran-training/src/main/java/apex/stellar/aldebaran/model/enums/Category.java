package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Categorizes movements into functional patterns.
 *
 * <p>The hierarchy is defined as: <strong>Movement -> Family -> Modality</strong>. Example: "Squat
 * Clean" -> "Clean" -> Weightlifting.
 *
 * <p>This structure enables program analysis (e.g., detecting biases), performance tracking, and
 * filtering based on the unified {@link Modality}.
 */
@Getter
@RequiredArgsConstructor
public enum Category {

  // --- Weightlifting (WL) ---
  DEADLIFT(Modality.WEIGHTLIFTING),
  SQUAT(Modality.WEIGHTLIFTING),
  PRESS_AND_JERK(Modality.WEIGHTLIFTING),
  CLEAN(Modality.WEIGHTLIFTING),
  SNATCH(Modality.WEIGHTLIFTING),
  COMPLEXES(Modality.WEIGHTLIFTING),
  SWING(Modality.WEIGHTLIFTING),
  OTHER_LIFTS(Modality.WEIGHTLIFTING),

  // --- Gymnastics (GY) ---
  PULLING(Modality.GYMNASTICS),
  PUSHING(Modality.GYMNASTICS),
  INVERTED(Modality.GYMNASTICS),
  CORE(Modality.GYMNASTICS),
  LUNGES(Modality.GYMNASTICS),
  LOCOMOTION_AND_BODY_CONTROL(Modality.GYMNASTICS),

  // --- Monostructural (Metabolic Conditioning) ---
  CARDIO(Modality.MONOSTRUCTURAL),
  CARDIO_MACHINES(Modality.MONOSTRUCTURAL),
  BURPEES(Modality.MONOSTRUCTURAL),
  JUMPING(Modality.MONOSTRUCTURAL),

  // --- Strongman (Odd Objects) ---
  THROWS_AND_SLAMS(Modality.STRONGMAN),
  CARRY(Modality.STRONGMAN),
  STRONGMAN_LIFTS(Modality.STRONGMAN),
  SLED(Modality.STRONGMAN);

  private final Modality modality;

  /** Modality Inner Enum. */
  @Getter
  @RequiredArgsConstructor
  public enum Modality {
    WEIGHTLIFTING,
    GYMNASTICS,
    MONOSTRUCTURAL,
    STRONGMAN
  }
}
