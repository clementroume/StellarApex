package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "Title of the workout", example = "Murph")
        @NotBlank(message = "{validation.wod.title.required}")
        @Size(max = 100, message = "{validation.wod.title.size}")
        String title,
    @Schema(description = "Structural type of the workout", example = "FOR_TIME")
        @NotNull(message = "{validation.wod.type.required}")
        WodType wodType,
    @Schema(description = "Primary scoring metric", example = "TIME")
        @NotNull(message = "{validation.wod.scoreType.required}")
        ScoreType scoreType,
    @Schema(description = "Full description/whiteboard", example = "Run 1 mile, 100 pull-ups...")
        @Size(max = 4000, message = "{validation.wod.description.size}")
        String description,
    @Schema(description = "Coach notes or stimulus info")
        @Size(max = 4000, message = "{validation.wod.notes.size}")
        String notes,
    @Schema(description = "Is this WOD visible to everyone?", defaultValue = "false")
        boolean isPublic,

    // --- Time & Structure ---

    @Schema(description = "Time cap in seconds", example = "3600")
        @Min(value = 0, message = "{validation.wod.timeCap.min}")
        Integer timeCapSeconds,
    @Schema(description = "Interval in seconds (for EMOM)", example = "60")
        @Min(value = 0, message = "{validation.wod.emom.min}")
        Integer emomInterval,
    @Schema(description = "Number of rounds (for EMOM)", example = "20")
        @Min(value = 0, message = "{validation.wod.emom.min}")
        Integer emomRounds,
    @Schema(description = "Summary rep scheme", example = "1 mile, 100-200-300, 1 mile")
        @Size(max = 100, message = "{validation.wod.repScheme.size}")
        String repScheme,

    // --- Components ---

    @Schema(description = "List of movements making up the WOD")
        @NotEmpty(message = "{validation.wod.movements.empty}")
        @Valid
        List<WodMovementRequest> movements) {}
