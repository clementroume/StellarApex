package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumeration of physical equipment used in CrossFit and functional training.
 *
 * <p>This enum categorizes the various implements required to perform specific movements. It acts
 * as a filter for workout search and inventory management, linked directly to the core {@link
 * Modality}.
 */
@Getter
@RequiredArgsConstructor
public enum Equipment {

  // --- Weightlifting & Strength ---
  BARBELL("Barbell", Modality.WEIGHTLIFTING),
  PLATES("Plates", Modality.WEIGHTLIFTING),
  DUMBBELL("Dumbbell", Modality.WEIGHTLIFTING),
  KETTLEBELL("Kettlebell", Modality.WEIGHTLIFTING),
  MEDICINE_BALL("Medicine Ball", Modality.WEIGHTLIFTING),
  SANDBAG("Sandbag", Modality.WEIGHTLIFTING),
  SLAM_BALL("Slam Ball", Modality.WEIGHTLIFTING),

  // --- Gymnastics & Bodyweight Rig ---
  PULL_UP_BAR("Pull-up Bar", Modality.GYMNASTICS),
  RINGS("Gymnastic Rings", Modality.GYMNASTICS),
  PARALLETTES("Parallettes", Modality.GYMNASTICS),
  ROPE("Climbing Rope", Modality.GYMNASTICS),
  ABMAT("AbMat", Modality.GYMNASTICS),
  BOX("Box", Modality.GYMNASTICS),

  // --- Monostructural / Cardio Machines ---
  ROWER("Rower", Modality.MONOSTRUCTURAL),
  ASSAULT_BIKE("Assault Bike", Modality.MONOSTRUCTURAL),
  ECHO_BIKE("Echo Bike", Modality.MONOSTRUCTURAL),
  SKI_ERG("SkiErg", Modality.MONOSTRUCTURAL),
  BIKE_ERG("BikeErg", Modality.MONOSTRUCTURAL),
  GHD("GHD Machine", Modality.MONOSTRUCTURAL),
  JUMP_ROPE("Jump Rope", Modality.MONOSTRUCTURAL),

  // --- Strongman & Specialized ---
  SLED("Sled", Modality.STRONGMAN),
  YOKE("Yoke", Modality.STRONGMAN),
  BATTLE_ROPE("Battle Rope", Modality.STRONGMAN),

  // --- Other ---
  /** Represents movements performed with bodyweight only, classified under Gymnastics. */
  NONE("None (Bodyweight)", Modality.GYMNASTICS);

  /** A human-readable name suitable for UI display. */
  private final String displayName;

  /** The primary training modality associated with this equipment. */
  private final Modality modality;
}
