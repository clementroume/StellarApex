package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO for logging a new workout performance.
 *
 * <p>Captures the athlete's result. Conditional validation (e.g. enforcing 'time' for a 'For Time'
 * workout) is handled by the business logic, not this DTO.
 */
public record WodScoreRequest(
    @Schema(description = "ID of the performed WOD", example = "101")
        @NotNull(message = "{validation.wodId.required}")
        Long wodId,
    @Schema(description = "Date of the workout")
        @NotNull(message = "{validation.date.required}")
        @PastOrPresent(message = "{validation.date.future}")
        LocalDate date,

    // --- Metrics (Optional based on WOD Type) ---

    @Schema(description = "Total time in seconds", example = "420")
        @Min(value = 0, message = "{validation.value.positive}")
        Integer timeSeconds,
    @Schema(description = "User preference for time display", example = "SECONDS")
        Unit timeDisplayUnit,
    @Schema(description = "Rounds completed (AMRAP)", example = "10")
        @Min(value = 0, message = "{validation.value.positive}")
        Integer rounds,
    @Schema(description = "Additional reps (AMRAP/Chipper)", example = "15")
        @Min(value = 0, message = "{validation.value.positive}")
        Integer reps,
    @Schema(description = "Heaviest weight lifted", example = "100.0")
        @DecimalMin(value = "0.0", message = "{validation.value.positive}")
        Double maxWeight,
    @Schema(description = "Total tonnage lifted", example = "5000.0")
        @DecimalMin(value = "0.0", message = "{validation.value.positive}")
        Double totalLoad,
    @Schema(description = "Unit for weight values", example = "KG") Unit weightUnit,
    @Schema(description = "Total distance covered", example = "5000.0")
        @DecimalMin(value = "0.0", message = "{validation.value.positive}")
        Double totalDistance,
    @Schema(description = "Unit for distance values", example = "METERS") Unit distanceUnit,
    @Schema(description = "Total calories burned/completed", example = "50")
        @Min(value = 0, message = "{validation.value.positive}")
        Integer totalCalories,

    // --- Metadata ---

    @Schema(description = "Scaling level used", example = "RX")
        @NotNull(message = "{validation.scaling.required}")
        ScalingLevel scaling,
    @Schema(description = "Was the workout stopped by the time cap?") boolean timeCapped,
    @Schema(description = "Details on how the workout was scaled")
        @Size(max = 4000, message = "{validation.score.scalingNotes.size}")
        String scalingNotes,
    @Schema(description = "Personal comments or feelings")
        @Size(max = 4000, message = "{validation.score.comment.size}")
        String userComment) {}
