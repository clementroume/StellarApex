package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating or updating an anatomical muscle entry.
 *
 * <p>Ensures data integrity before reaching the persistence layer.
 */
public record MuscleRequest(
    @Schema(
            description = "Medical/Latin name (must be unique)",
            example = "Pectoralis Major",
            requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.muscle.medicalName.required}")
        @Size(max = 100, message = "{validation.muscle.medicalName.size}")
        String medicalName,
    @Schema(
            description = "Common name in English",
            example = "Chest",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 100, message = "{validation.muscle.commonName.size}")
        String commonNameEn,
    @Schema(
            description = "Common name in French",
            example = "Pectoraux",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 100, message = "{validation.muscle.commonName.size}")
        String commonNameFr,
    @Schema(
            description = "Description in English",
            example = "Major muscle of the chest...",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 2000, message = "{validation.muscle.description.size}")
        String descriptionEn,
    @Schema(
            description = "Description in French",
            example = "Muscle principal de la poitrine...",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 2000, message = "{validation.muscle.description.size}")
        String descriptionFr,
    @Schema(
            description = "Major anatomical muscle group",
            example = "CHEST",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.muscle.group.required}")
        MuscleGroup muscleGroup) {}
