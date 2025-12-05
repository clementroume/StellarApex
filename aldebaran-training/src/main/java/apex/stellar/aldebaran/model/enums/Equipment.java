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
  BARBELL("Barbell"),
  PLATES("Plates"),
  DUMBBELL("Dumbbell"),
  KETTLEBELL("Kettlebell"),
  MEDICINE_BALL("Medicine Ball"),
  SANDBAG("Sandbag"),
  SLAM_BALL("Slam Ball"),

  // --- Gymnastics & Bodyweight Rig ---
  PULL_UP_BAR("Pull-up Bar"),
  RINGS("Gymnastic Rings"),
  PARALLETTES("Parallettes"),
  ROPE("Climbing Rope"),
  ABMAT("AbMat"),
  BOX("Box"),

  // --- Monostructural / Cardio Machines ---
  ROWER("Rower"),
  ASSAULT_BIKE("Assault Bike"),
  ECHO_BIKE("Echo Bike"),
  SKI_ERG("SkiErg"),
  BIKE_ERG("BikeErg"),
  GHD("GHD Machine"),
  JUMP_ROPE("Jump Rope"),

  // --- Strongman & Odd Objects---
  SLED("Sled"),
  YOKE("Yoke"),
  BATTLE_ROPE("Battle Rope"),

  // --- None / Bodyweight ---
  NONE("None (Bodyweight)");

  /** A human-readable name suitable for UI display. */
  private final String displayName;
}
