package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;

/**
 * A muscle of a movement variant.
 *
 * @param muscleId The muscle.
 * @param medicalName Its medical name.
 * @param commonNameEn Its English name.
 * @param commonNameFr Its French name.
 * @param role Its role in this variant.
 */
public record VariantMuscleResponse(
    Long muscleId, String medicalName, String commonNameEn, String commonNameFr, MuscleRole role) {}
