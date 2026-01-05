package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DTO for creating or updating a Movement in the catalog.
 *
 * <p>This object encapsulates all static data required to define an exercise, including its
 * anatomical impact and coaching content.
 */
public record MovementRequest(
    @Schema(
            description = "Official display name of the movement",
            example = "Back Squat",
            requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.movement.name.required}")
        @Size(max = 50, message = "{validation.movement.name.size}")
        String name,
    @Schema(
            description = "Short abbreviation or code",
            example = "BS",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 20, message = "{validation.movement.abbreviation.size}")
        String nameAbbreviation,
    @Schema(
            description = "Primary functional category",
            example = "SQUAT",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.movement.category.required}")
        Category category,
    @Schema(
            description = "List of required equipment (can be empty if bodyweight only)",
            example = "[\"BARBELL\", \"PLATES\"]",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @NotNull
        Set<Equipment> equipment,
    @Schema(
            description = "List of applicable technique variations",
            example = "[\"STRICT\", \"TEMPO\"]",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @NotNull
        Set<Technique> techniques,
    @Schema(
            description = "Configuration of targeted muscles and their activation roles",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Valid
        List<MovementMuscleRequest> muscles,
    @Schema(
            description = "Indicates if the athlete's body weight contributes to the load",
            example = "true",
            requiredMode = RequiredMode.REQUIRED)
        boolean involvesBodyweight,
    @Schema(
            description = "Factor of bodyweight to include in tonnage calculation (0.0 to 1.0)",
            example = "1.0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @DecimalMin(value = "0.0", message = "{validation.movement.bodyweightFactor.min}")
        @DecimalMax(value = "1.0", message = "{validation.movement.bodyweightFactor.max}")
        Double bodyweightFactor,

    // --- Content & Media ---

    @Schema(
            description = "Detailed technical description in English",
            example = "The back squat is a lower body exercise...",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.movement.description.size}")
        String descriptionEn,
    @Schema(
            description = "Description technique détaillée en Français",
            example = "Le squat arrière est un exercice...",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.movement.description.size}")
        String descriptionFr,
    @Schema(
            description = "Short coaching cues in English",
            example = "Chest up, knees out",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.movement.description.size}")
        String coachingCuesEn,
    @Schema(
            description = "Conseils de coaching courts en Français",
            example = "Torse bombé, genoux vers l'extérieur",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.movement.description.size}")
        String coachingCuesFr,
    @Schema(
            description = "URL to a demonstration video",
            example = "https://videos.stellar.apex/back-squat.mp4",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 512, message = "{validation.url.size}")
        String videoUrl,
    @Schema(
            description = "URL to an anatomical diagram or image",
            example = "https://images.stellar.apex/back-squat.png",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 512, message = "{validation.url.size}")
        String imageUrl) {

  /**
   * Constructs a MovementRequest object with default values for equipment, techniques, and muscles
   * if null values are provided.
   *
   * @param equipment the set of equipment associated with the movement; defaults to an empty set if
   *     null.
   * @param techniques the set of techniques applicable to the movement; defaults to an empty set if
   *     null.
   * @param muscles the list of muscles involved in the movement; defaults to an empty list if null.
   */
  public MovementRequest {
    equipment = equipment != null ? equipment : new HashSet<>();
    techniques = techniques != null ? techniques : new HashSet<>();
    muscles = muscles != null ? muscles : new ArrayList<>();
  }
}
