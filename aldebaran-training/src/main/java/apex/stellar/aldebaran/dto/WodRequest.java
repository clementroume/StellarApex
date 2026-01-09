package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO for creating or updating a WOD (Workout of the Day).
 *
 * <p>Defines the structure ("The Recipe") of the workout.
 */
public record WodRequest(
    @Schema(
            description = "Title of the workout",
            example = "Murph",
            requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.wod.title.required}")
        @Size(max = 100, message = "{validation.wod.title.size}")
        String title,
    @Schema(
            description = "Structural type of the workout",
            example = "FOR_TIME",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.wod.type.required}")
        WodType wodType,
    @Schema(
            description = "Primary scoring metric",
            example = "TIME",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.wod.scoreType.required}")
        ScoreType scoreType,
    @Schema(
            description = "Full description/whiteboard",
            example = "Run 1 mile, 100 pull-ups...",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.wod.description.size}")
        String description,
    @Schema(
            description = "Coach notes or stimulus info",
            example = "Pace yourself on the run.",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.wod.notes.size}")
        String notes,
    @Schema(
            description = "The User ID of the author.",
            example = "1",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Long authorId,
    @Schema(
            description = "The Gym ID this WOD belongs to. Null for global WODs.",
            example = "101",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Long gymId,
    @Schema(
            description = "Is this WOD visible to everyone?",
            defaultValue = "false",
            example = "true",
            requiredMode = RequiredMode.REQUIRED)
        boolean isPublic,

    // --- Time & Structure ---

    @Schema(
            description = "Time cap in seconds",
            example = "3600",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.wod.timeCap.min}")
        Integer timeCapSeconds,
    @Schema(
            description = "Interval in seconds (for EMOM)",
            example = "60",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.wod.emom.min}")
        Integer emomInterval,
    @Schema(
            description = "Number of rounds (for EMOM)",
            example = "20",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.wod.emom.min}")
        Integer emomRounds,
    @Schema(
            description = "Summary rep scheme",
            example = "1 mile, 100-200-300, 1 mile",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 100, message = "{validation.wod.repScheme.size}")
        String repScheme,

    // --- Components ---

    @Schema(
            description = "List of movements making up the WOD",
            requiredMode = RequiredMode.REQUIRED)
        @NotEmpty(message = "{validation.wod.movements.empty}")
        @Valid
        List<WodMovementRequest> movements) {}
