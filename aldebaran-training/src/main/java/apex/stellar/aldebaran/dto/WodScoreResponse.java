package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * DTO representing a logged score.
 *
 * <p>Includes pre-calculated display fields (like split minutes/seconds) to facilitate UI
 * formatting (e.g. "1:30" instead of "90s") and denormalized values based on user preference.
 */
public record WodScoreResponse(
    @Schema(description = "Unique Score ID", example = "888") Long id,
    @Schema(description = "ID of the athlete", example = "user-123") String userId,
    @Schema(description = "Date of performance") LocalDate date,
    @Schema(description = "Summary of the WOD definition") WodSummaryResponse wodSummary,

    // --- Metrics ---

    @Schema(description = "Total time in seconds", example = "90") Integer timeSeconds,
    @Schema(description = "Minutes part for MM:SS display (e.g., 1 for 90s)", example = "1")
        Integer timeMinutesPart,
    @Schema(description = "Seconds part for MM:SS display (e.g., 30 for 90s)", example = "30")
        Integer timeSecondsPart,
    @Schema(description = "User preference for time display", example = "SECONDS")
        Unit timeDisplayUnit,
    @Schema(description = "Rounds completed (for AMRAP)", example = "10") Integer rounds,
    @Schema(description = "Additional reps (for AMRAP/Chipper)", example = "15") Integer reps,
    @Schema(description = "Heaviest weight lifted", example = "100.0") Double maxWeight,
    @Schema(description = "Total tonnage lifted", example = "5000.0") Double totalLoad,
    @Schema(description = "Unit for weight values", example = "KG") Unit weightUnit,
    @Schema(description = "Total distance covered", example = "5000.0") Double totalDistance,
    @Schema(description = "Unit for distance values", example = "METERS") Unit distanceUnit,
    @Schema(description = "Total calories burned", example = "50") Integer totalCalories,

    // --- Status ---

    @Schema(description = "Scaling level (RX, SCALED...)", example = "RX") ScalingLevel scaling,
    @Schema(description = "Is this a Personal Record?", example = "true") boolean personalRecord,
    @Schema(description = "Did the athlete hit the time cap?", example = "false")
        boolean timeCapped,
    @Schema(description = "User personal comments", example = "Tough workout!") String userComment,
    @Schema(description = "Details on scaling modifications", example = "Used bands for pull-ups")
        String scalingNotes) {}
