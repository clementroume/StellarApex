package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing the biomechanical relationship between a movement and a muscle.
 *
 * <p>This is a nested DTO typically found within a {@link MovementResponse}.
 */
public record MovementMuscleResponse(
    @Schema(description = "The target muscle details") MuscleResponse muscle,
    @Schema(description = "Biomechanical role", example = "AGONIST") MuscleRole role,
    @Schema(description = "Activation coefficient (0.0 - 1.0)", example = "1.0")
        Double impactFactor) {}
