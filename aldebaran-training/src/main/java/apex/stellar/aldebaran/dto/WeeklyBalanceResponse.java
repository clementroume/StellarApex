package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.TimeDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The current user's weekly balance (lot D): facts only, no alert and no ratio (D89). Sirius draws
 * them without numbers (D88).
 *
 * @param weekStart Monday of the chosen week.
 * @param windowStart Monday of the first week of the sections (the chosen week and the 3 before).
 * @param muscles Sets per muscle worked in the window.
 * @param volume Equivalent sets of the chosen week and the 7 before, oldest first.
 * @param minutes Metcon minutes per time domain in the window.
 * @param lifts Strength sets per lift in the window, the most worked first.
 * @param modalities Equivalent sets per modality in the window.
 */
public record WeeklyBalanceResponse(
    LocalDate weekStart,
    LocalDate windowStart,
    List<MuscleBalance> muscles,
    List<WeekVolume> volume,
    Map<TimeDomain, Integer> minutes,
    List<LiftBalance> lifts,
    Map<Modality, Double> modalities) {

  /**
   * Sets of one muscle.
   *
   * @param muscleId The muscle.
   * @param code Its code (key of the Sirius drawing and 3D model), or null.
   * @param region Its region.
   * @param medicalName Its medical name.
   * @param commonNameEn English common name, or null.
   * @param commonNameFr French common name, or null.
   * @param movingSets Sets in movement (agonist 1, synergist 0.5).
   * @param holdingSets Sets in holding (stabilizer).
   */
  public record MuscleBalance(
      Long muscleId,
      @Nullable String code,
      String region,
      String medicalName,
      @Nullable String commonNameEn,
      @Nullable String commonNameFr,
      double movingSets,
      double holdingSets) {}

  /**
   * Volume of one week.
   *
   * @param weekStart Its Monday.
   * @param sets Its equivalent sets.
   */
  public record WeekVolume(LocalDate weekStart, double sets) {}

  /**
   * Strength sets of one lift.
   *
   * @param label Its name in CrossFit English (a base movement, or a complex).
   * @param movementId Its base movement, null for a complex.
   * @param sets Its sets.
   * @param nearRecord Whether one of them was near the record (80 % of the 1RM of its day).
   */
  public record LiftBalance(String label, @Nullable Long movementId, double sets, boolean nearRecord) {}
}
