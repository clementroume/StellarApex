package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Nested DTO for linking a muscle to a movement during creation/update. */
public record MovementMuscleRequest(
    // --- Relationships ---
    @NotNull(message = "{validation.muscle.id.required}") Long muscleId,
    @NotNull(message = "{validation.muscle.role.required}") MuscleRole role,
    @DecimalMin(value = "0.0", message = "{validation.muscle.impactFactor.min}")
        @DecimalMax(value = "1.0", message = "{validation.muscle.impactFactor.max}")
        Double impactFactor) {

  /** Compact constructor that applies default values for the impact factor if null. */
  public MovementMuscleRequest {
    if (impactFactor == null) {
      impactFactor = 1.0;
    }
  }
}
