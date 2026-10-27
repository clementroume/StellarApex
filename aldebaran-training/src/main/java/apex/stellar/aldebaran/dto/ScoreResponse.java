package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO representing a logged score. */
public record ScoreResponse(

    // --- Identification ---
    Long id,
    Long userId,
    LocalDate date,
    WodSummaryResponse wodSummary,
    Long gymId,

    // --- Time Metrics ---
    Integer timeSeconds,
    Integer timeMinutesPart,
    Integer timeSecondsPart,
    Unit timeDisplayUnit,

    // --- Volume Metrics ---
    Integer rounds,
    Integer reps,

    // --- Load Metrics ---
    Double maxWeight,
    Double totalLoad,
    Unit weightUnit,

    // --- Distance Metrics ---
    Double totalDistance,
    Unit distanceUnit,

    // --- Calories ---
    Integer totalCalories,

    // --- Performance Context ---
    ScalingLevel scaling,
    boolean timeCapped,
    Integer tiebreakSeconds,
    boolean personalRecord,

    // --- Comments and Scaling notes---
    String userComment,
    String scalingNotes,

    // --- Audit ---
    LocalDate loggedAt,
    // User who logged the score (the athlete, or staff / admin logging it for them)
    Long loggedBy,

    // --- Verification (competition WODs), null while not verified ---
    Long verifiedBy,
    LocalDateTime verifiedAt,

    // --- Per-user rights (computed per request) ---
    ScoreCapabilities capabilities) {

  /**
   * Returns a copy of this response for a given viewer: with their capabilities, and without the
   * athlete's comment and scaling notes when the viewer may not see them.
   *
   * @param capabilities The rights of the viewer on this score.
   * @param showNotes Whether the viewer may read the comment and the scaling notes.
   * @return A new response adapted to the viewer.
   */
  public ScoreResponse forViewer(ScoreCapabilities capabilities, boolean showNotes) {
    return new ScoreResponse(
        id,
        userId,
        date,
        wodSummary,
        gymId,
        timeSeconds,
        timeMinutesPart,
        timeSecondsPart,
        timeDisplayUnit,
        rounds,
        reps,
        maxWeight,
        totalLoad,
        weightUnit,
        totalDistance,
        distanceUnit,
        totalCalories,
        scaling,
        timeCapped,
        tiebreakSeconds,
        personalRecord,
        showNotes ? userComment : null,
        showNotes ? scalingNotes : null,
        loggedAt,
        loggedBy,
        verifiedBy,
        verifiedAt,
        capabilities);
  }
}
