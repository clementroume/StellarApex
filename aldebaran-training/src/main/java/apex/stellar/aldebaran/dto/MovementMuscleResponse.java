package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;

/**
 * DTO representing the biomechanical relationship between a movement and a muscle.
 *
 * <p>This is a nested DTO typically found within a {@link MovementResponse}.
 */
public record MovementMuscleResponse(
    // --- Relationships ---
    MuscleResponse muscle, MuscleRole role, Double impactFactor) {}
