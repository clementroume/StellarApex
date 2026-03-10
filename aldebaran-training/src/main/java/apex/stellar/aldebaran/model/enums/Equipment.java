package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumeration of physical equipment used in CrossFit and functional training.
 *
 * <p>This enum categorizes the various implements required to perform specific movements. It acts
 * as a filter for workout search (e.g., "Show me workouts requiring only a Kettlebell") and helps
 * in inventory management for Affiliates.
 */
@Getter
@RequiredArgsConstructor
public enum Equipment {

  // --- Weightlifting & Strength ---
  BARBELL("Barbell", EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  PLATES("Plates", EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  DUMBBELL("Dumbbell", EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  KETTLEBELL("Kettlebell", EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  MEDICINE_BALL("Medicine Ball", EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  SANDBAG("Sandbag", EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  SLAM_BALL("Slam Ball", EquipmentCategory.WEIGHTLIFTING_STRENGTH),

  // --- Gymnastics & Bodyweight Rig ---
  PULL_UP_BAR("Pull-up Bar", EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  RINGS("Gymnastic Rings", EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  PARALLETTES("Parallettes", EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  ROPE("Climbing Rope", EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  ABMAT("AbMat", EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  BOX("Box", EquipmentCategory.GYMNASTICS_BODYWEIGHT),

  // --- Monostructural / Cardio Machines ---
  ROWER("Rower", EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  ASSAULT_BIKE("Assault Bike", EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  ECHO_BIKE("Echo Bike", EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  SKI_ERG("SkiErg", EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  BIKE_ERG("BikeErg", EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  GHD("GHD Machine", EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  JUMP_ROPE("Jump Rope", EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),

  // --- Strongman & Odd Objects---
  SLED("Sled", EquipmentCategory.STRONGMAN_ODD_OBJECT),
  YOKE("Yoke", EquipmentCategory.STRONGMAN_ODD_OBJECT),
  BATTLE_ROPE("Battle Rope", EquipmentCategory.STRONGMAN_ODD_OBJECT),

  // --- None / Bodyweight ---
  NONE("None (Bodyweight)", EquipmentCategory.NONE_BODYWEIGHT);

  private final String displayName;
  private final EquipmentCategory category;

  /**
   * Represents various categories of equipment used in training, based on their primary function or
   * usage.
   *
   * <ul>
   *   <li><strong>WEIGHTLIFTING_STRENGTH</strong>: Equipment used for weightlifting or strength
   *       training.
   *   <li><strong>GYMNASTICS_BODYWEIGHT</strong>: Equipment related to gymnastics or bodyweight
   *       exercises.
   *   <li><strong>MONOSTRUCTURAL_CARDIO_MACHINES</strong>: Cardio machines used for monostructural
   *       conditioning.
   *   <li><strong>STRONGMAN_ODD_OBJECT</strong>: Specialized or unconventional equipment used in
   *       strongman training.
   *   <li><strong>NONE_BODYWEIGHT</strong>: Represents bodyweight-based exercises or no additional
   *       equipment.
   * </ul>
   */
  public enum EquipmentCategory {
    WEIGHTLIFTING_STRENGTH,
    GYMNASTICS_BODYWEIGHT,
    MONOSTRUCTURAL_CARDIO_MACHINES,
    STRONGMAN_ODD_OBJECT,
    NONE_BODYWEIGHT
  }
}
