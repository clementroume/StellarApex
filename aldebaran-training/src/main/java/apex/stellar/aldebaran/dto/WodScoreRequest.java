package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO for logging a new workout performance.
 *
 * <p>Adapts time input to allow users to enter data naturally (e.g., "1 min 30" or "90 sec"). The
 * API will infer the preferred display format based on which fields are populated.
 */
public record WodScoreRequest(
    @Schema(
            description = "ID of the performed WOD",
            example = "101",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.wodId.required}")
        Long wodId,
    @Schema(
            description = "Date of the workout",
            example = "2023-10-25",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.date.required}")
        @PastOrPresent(message = "{validation.date.future}")
        LocalDate date,

    // --- Time Metrics (Flexible Input) ---

    @Schema(
            description = "Minutes part of the time (e.g., 1 for '1:30')",
            example = "1",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.value.positive}")
        Integer timeMinutes,
    @Schema(
            description = "Seconds part or Total seconds (e.g., 30 for '1:30', or 90 for '90s')",
            example = "30",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.value.positive}")
        Integer timeSeconds,

    // --- Other Metrics ---

    @Schema(
            description = "Rounds completed (AMRAP)",
            example = "10",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.value.positive}")
        Integer rounds,
    @Schema(
            description = "Additional reps (AMRAP/Chipper)",
            example = "15",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.value.positive}")
        Integer reps,
    @Schema(
            description = "Heaviest weight lifted",
            example = "100.0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @DecimalMin(value = "0.0", message = "{validation.value.positive}")
        Double maxWeight,
    @Schema(
            description = "Total tonnage lifted",
            example = "5000.0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @DecimalMin(value = "0.0", message = "{validation.value.positive}")
        Double totalLoad,
    @Schema(
            description = "Unit for weight values",
            example = "KG",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Unit weightUnit,
    @Schema(
            description = "Total distance covered",
            example = "5000.0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @DecimalMin(value = "0.0", message = "{validation.value.positive}")
        Double totalDistance,
    @Schema(
            description = "Unit for distance values",
            example = "METERS",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Unit distanceUnit,
    @Schema(
            description = "Total calories burned/completed",
            example = "50",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.value.positive}")
        Integer totalCalories,

    // --- Metadata ---

    @Schema(
            description = "Scaling level used",
            example = "RX",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.scaling.required}")
        ScalingLevel scaling,
    @Schema(
            description = "Was the workout stopped by the time cap?",
            example = "false",
            requiredMode = RequiredMode.REQUIRED)
        boolean timeCapped,
    @Schema(
            description = "Details on how the workout was scaled",
            example = "Used bands for pull-ups",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.score.scalingNotes.size}")
        String scalingNotes,
    @Schema(
            description = "Personal comments or feelings",
            example = "Felt strong today!",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.score.comment.size}")
        String userComment) {}
