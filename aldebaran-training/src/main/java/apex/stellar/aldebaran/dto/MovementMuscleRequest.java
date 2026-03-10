package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Nested DTO for linking a muscle to a movement during creation/update.
 *
 * <p>This DTO captures the "how" of muscle activation for a specific exercise.
 */
public record MovementMuscleRequest(
    @Schema(
            description = "Internal ID of the muscle",
            example = "1",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.muscle.id.required}")
        Long muscleId,
    @Schema(
            description = "Biomechanical role",
            example = "AGONIST",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.muscle.role.required}")
        MuscleRole role,
    @Schema(
            description = "Impact/Activation coefficient (0.0 - 1.0)",
            defaultValue = "1.0",
            example = "0.8",
            requiredMode = RequiredMode.NOT_REQUIRED)
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
