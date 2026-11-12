package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Categorizes movements into functional patterns.
 *
 * <p>The hierarchy is defined as: <strong>Movement -> Family -> Modality</strong>. Example: "Squat
 * Clean" -> "Clean" -> Weightlifting.
 *
 * <p>The three modalities of the CrossFit Level 1 (strongman is a specialty, not a modality). A
 * family gives a line's starting modality; a usual name or a load on the line may change it
 * ({@code CatalogRules.modality}, D127).
 */
@Getter
@RequiredArgsConstructor
public enum Category {

  // --- Weightlifting: an external object moves with the body (L1, Clarkson 2012) ---
  DEADLIFT(Modality.WEIGHTLIFTING),
  SQUAT(Modality.WEIGHTLIFTING),
  PRESS_AND_JERK(Modality.WEIGHTLIFTING),
  CLEAN(Modality.WEIGHTLIFTING),
  SNATCH(Modality.WEIGHTLIFTING),
  COMPLEXES(Modality.WEIGHTLIFTING),
  SWING(Modality.WEIGHTLIFTING),
  OTHER_LIFTS(Modality.WEIGHTLIFTING),
  THROWS_AND_SLAMS(Modality.WEIGHTLIFTING),
  CARRY(Modality.WEIGHTLIFTING),
  STRONGMAN_LIFTS(Modality.WEIGHTLIFTING),
  SLED(Modality.WEIGHTLIFTING),

  // --- Gymnastics: the body alone ---
  PULLING(Modality.GYMNASTICS),
  PUSHING(Modality.GYMNASTICS),
  INVERTED(Modality.GYMNASTICS),
  CORE(Modality.GYMNASTICS),
  LUNGES(Modality.GYMNASTICS),
  LOCOMOTION_AND_BODY_CONTROL(Modality.GYMNASTICS),
  BURPEES(Modality.GYMNASTICS),
  JUMPING(Modality.GYMNASTICS),

  // --- Monostructural: cyclic cardio ---
  CARDIO(Modality.MONOSTRUCTURAL),
  CARDIO_MACHINES(Modality.MONOSTRUCTURAL);

  private final Modality modality;

  /** Modality Inner Enum. */
  @Getter
  @RequiredArgsConstructor
  public enum Modality {
    WEIGHTLIFTING,
    GYMNASTICS,
    MONOSTRUCTURAL
  }
}
