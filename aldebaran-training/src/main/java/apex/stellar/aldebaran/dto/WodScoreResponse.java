package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import java.time.LocalDate;

/**
 * DTO representing a logged score.
 *
 * <p>Includes pre-calculated display fields (like split minutes/seconds) to facilitate UI
 * formatting (e.g. "1:30" instead of "90s") and denormalized values based on user preference.
 */
public record WodScoreResponse(
    Long id,
    Long userId,
    LocalDate date,
    WodSummaryResponse wodSummary,

    // --- Metrics ---
    Integer timeSeconds,
    Integer timeMinutesPart,
    Integer timeSecondsPart,
    Unit timeDisplayUnit,
    Integer rounds,
    Integer reps,
    Double maxWeight,
    Double totalLoad,
    Unit weightUnit,
    Double totalDistance,
    Unit distanceUnit,
    Integer totalCalories,

    // --- Status ---
    ScalingLevel scaling,
    boolean personalRecord,
    boolean timeCapped,
    String userComment,
    String scalingNotes) {}
