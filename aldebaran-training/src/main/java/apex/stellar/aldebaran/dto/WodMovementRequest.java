package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** DTO for specifying a single movement within a WOD creation or update request. */
public record WodMovementRequest(
    // ---  Identification ---
    @NotNull(message = "{validation.movement.id.required}") Long movementId,
    @NotNull(message = "{validation.order.required}")
        @Min(value = 1, message = "{validation.order.positive}")
        Integer orderIndex,

    // --- Prescription ---
    @Size(max = 50, message = "{validation.wod.repsScheme.size}") String repsScheme,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weight,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weightFemale,
    Unit weightUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer durationSeconds,
    Unit durationDisplayUnit,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double distance,
    Unit distanceUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer calories,
    @Min(value = 0, message = "{validation.value.positive}") Integer caloriesFemale,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double height,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double heightFemale,
    Unit heightUnit,

    // --- Characteristics ---
    Set<String> equipment,
    Set<String> techniques,

    // --- Instructions ---
    @Size(max = 4000, message = "{validation.wod.notes.size}") String notesEn,
    @Size(max = 4000, message = "{validation.wod.notes.size}") String notesFr,
    @Size(max = 4000, message = "{validation.wod.scaling.size}") String scalingOptionsEn,
    @Size(max = 4000, message = "{validation.wod.scaling.size}") String scalingOptionsFr,

    // --- Sets (strength / skill work), optional ---
    @Min(value = 1, message = "{validation.wod.sets.min}") Integer sets) {

  /** Movement prescribed without a number of sets. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr,
      String scalingOptionsEn,
      String scalingOptionsFr) {
    this(
        movementId,
        orderIndex,
        repsScheme,
        weight,
        weightFemale,
        weightUnit,
        durationSeconds,
        durationDisplayUnit,
        distance,
        distanceUnit,
        calories,
        caloriesFemale,
        height,
        heightFemale,
        heightUnit,
        equipment,
        techniques,
        notesEn,
        notesFr,
        scalingOptionsEn,
        scalingOptionsFr,
        null);
  }
}
