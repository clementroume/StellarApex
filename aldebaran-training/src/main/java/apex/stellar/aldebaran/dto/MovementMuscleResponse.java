package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;

/** DTO representing the biomechanical relationship between a movement and a muscle. */
public record MovementMuscleResponse(
    // --- Relationships ---
    MuscleResponse muscle, MuscleRole role, Double impactFactor) {}
