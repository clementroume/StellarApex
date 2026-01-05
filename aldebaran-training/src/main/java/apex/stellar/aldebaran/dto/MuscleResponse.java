package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing anatomical muscle reference data.
 *
 * <p>This object is used to expose the static anatomy catalog to clients, featuring localized names
 * and descriptions.
 *
 * @param id The unique database identifier.
 * @param medicalName The Latin/Medical unique name (Business Key).
 * @param commonNameEn The colloquial name in English.
 * @param commonNameFr The colloquial name in French.
 * @param descriptionEn A brief anatomical description in English.
 * @param descriptionFr A brief anatomical description in French.
 * @param muscleGroup The major body part categorization.
 */
public record MuscleResponse(
    @Schema(description = "Unique database ID", example = "42") Long id,
    @Schema(description = "Medical/Latin name (Business Key)", example = "Pectoralis Major")
        String medicalName,
    @Schema(description = "Common name in English", example = "Chest") String commonNameEn,
    @Schema(description = "Common name in French", example = "Pectoraux") String commonNameFr,
    @Schema(description = "Description in English") String descriptionEn,
    @Schema(description = "Description in French") String descriptionFr,
    @Schema(description = "Major anatomical muscle group", example = "CHEST")
        MuscleGroup muscleGroup) {}
