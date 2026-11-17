package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.enums.AgeGroup;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Unit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    // Division of the athlete when the score was logged (null: not chosen)
    Division division,
    // Age group of the athlete in the year of the score (null: no birth year)
    AgeGroup ageGroup,

    // Scaled score following the WOD's official Scaled version (ranked in Scaled)
    boolean scaledOfficial,
    // Rx+ badge: Scaled, every line at least RX and one harder (never ranked)
    boolean rxPlus,

    // --- Comment ---
    String userComment,

    // --- Audit ---
    LocalDate loggedAt,
    // User who logged the score (the athlete, or staff / admin logging it for them)
    Long loggedBy,

    // --- Verification (competition WODs), null while not verified ---
    Long verifiedBy,
    LocalDateTime verifiedAt,
    // Submitted to the judges of a competition WOD
    boolean verificationRequested,

    // --- Session: journal the score belongs to, and strength sets (display unit) ---
    Long sessionLogId,
    List<ScoreSetResponse> sets,

    // --- Lines done otherwise than prescribed, or the reps of "max" lines ---
    List<ScoreLineResponse> lines,
    // --- Reps of one round as done (scaled reps included), or null when not readable ---
    Integer roundReps,

    // --- Team WOD: team of the score and its members (same performance), or null ---
    Long teamId,
    List<Long> teamMemberIds,

    // --- Per-user rights (computed per request) ---
    ScoreCapabilities capabilities) {

  /**
   * Returns a copy of this response for a given viewer: with their capabilities, and without the
   * athlete's comment when the viewer may not see them.
   *
   * @param capabilities The rights of the viewer on this score.
   * @param showNotes Whether the viewer may read the comment.
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
        division,
        ageGroup,
        scaledOfficial,
        rxPlus,
        showNotes ? userComment : null,
        loggedAt,
        loggedBy,
        verifiedBy,
        verifiedAt,
        verificationRequested,
        sessionLogId,
        sets,
        lines,
        roundReps,
        teamId,
        teamMemberIds,
        capabilities);
  }
}
