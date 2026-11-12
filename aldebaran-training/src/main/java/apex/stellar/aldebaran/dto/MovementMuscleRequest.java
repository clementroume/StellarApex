package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import jakarta.validation.constraints.NotNull;

/** Nested DTO for linking a muscle to a movement during creation/update. */
public record MovementMuscleRequest(
    // --- Relationships ---
    @NotNull(message = "{validation.muscle.id.required}") Long muscleId,
    @NotNull(message = "{validation.muscle.role.required}") MuscleRole role) {}
