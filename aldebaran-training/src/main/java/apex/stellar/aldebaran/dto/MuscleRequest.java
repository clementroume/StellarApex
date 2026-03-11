package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating or updating an anatomical muscle entry.
 *
 * <p>Ensures data integrity before reaching the persistence layer.
 */
public record MuscleRequest(

    // --- Identification ---
    @NotBlank(message = "{validation.muscle.medicalName.required}")
        @Size(max = 100, message = "{validation.muscle.medicalName.size}")
        String medicalName,

    // --- Characteristics ---
    @NotNull(message = "{validation.muscle.group.required}") MuscleGroup muscleGroup,

    // --- Internationalized Content ---
    @Size(max = 100, message = "{validation.muscle.commonName.size}") String commonNameEn,
    @Size(max = 100, message = "{validation.muscle.commonName.size}") String commonNameFr,
    @Size(max = 2000, message = "{validation.muscle.description.size}") String descriptionEn,
    @Size(max = 2000, message = "{validation.muscle.description.size}") String descriptionFr,

    // --- Media ---
    @Size(max = 255, message = "{validation.muscle.image_url.size}") String imageUrl) {}
