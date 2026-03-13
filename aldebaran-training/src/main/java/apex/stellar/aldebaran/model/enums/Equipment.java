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
  BARBELL(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  PLATES(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  DUMBBELL(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  KETTLEBELL(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  TRAP_BAR(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  AXLE_BAR(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  MEDICINE_BALL(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  D_BALL(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  SANDBAG(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  SLAM_BALL(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  WEIGHT(EquipmentCategory.WEIGHTLIFTING_STRENGTH),
  BENCH(EquipmentCategory.WEIGHTLIFTING_STRENGTH),

  // --- Gymnastics & Bodyweight Rig ---
  PULL_UP_BAR(EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  RINGS(EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  PARALLETTES(EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  PARALLEL_BARS(EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  MATADOR(EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  ROPE(EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  ABMAT(EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  BOX(EquipmentCategory.GYMNASTICS_BODYWEIGHT),
  ELASTIC_BAND(EquipmentCategory.GYMNASTICS_BODYWEIGHT),

  // --- Monostructural / Cardio Machines ---
  ROWER(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  ASSAULT_BIKE(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  ECHO_BIKE(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  SKI_ERG(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  BIKE_ERG(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  CURVED_TREADMILL(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  TREADMILL(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  GHD(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),
  JUMP_ROPE(EquipmentCategory.MONOSTRUCTURAL_CARDIO_MACHINES),

  // --- Strongman & Odd Objects---
  SLED(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  SLED_AND_ROPE(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  HARNESS(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  YOKE(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  BATTLE_ROPE(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  HANDLES(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  TIRE(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  ATLAS_STONE(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  KEG(EquipmentCategory.STRONGMAN_ODD_OBJECT),
  LOG(EquipmentCategory.STRONGMAN_ODD_OBJECT);

  private final EquipmentCategory category;

  /**
   * Represents various categories of equipment used in training, based on their primary function or
   * usage.
   */
  public enum EquipmentCategory {
    WEIGHTLIFTING_STRENGTH,
    GYMNASTICS_BODYWEIGHT,
    MONOSTRUCTURAL_CARDIO_MACHINES,
    STRONGMAN_ODD_OBJECT
  }
}
