package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;

/**
 * DTO representing anatomical muscle reference data.
 *
 * <p>This object is used to expose the static anatomy catalog to clients, featuring localized names
 * and descriptions.
 */
public record MuscleResponse(

    // --- Identification ---
    Long id,
    String medicalName,

    // --- Characteristics ---
    MuscleGroup muscleGroup,

    // --- Internationalized Content ---
    String commonNameEn,
    String commonNameFr,
    String descriptionEn,
    String descriptionFr,

    // --- Media ---
    String imageUrl) {}
