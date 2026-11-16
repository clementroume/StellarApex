package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.model.enums.UnitField;
import apex.stellar.aldebaran.validation.UnitOf;
import apex.stellar.aldebaran.validation.ValidScoreRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** DTO for logging a new workout performance. */
@ValidScoreRequest
public record ScoreRequest(

    // --- Identification ---
    Long userId,
    @NotNull(message = "{validation.date.required}")
        @PastOrPresent(message = "{validation.date.future}")
        LocalDate date,
    @NotNull(message = "{validation.wodId.required}") Long wodId,

    // --- Time Metrics ---
    @Min(value = 0, message = "{validation.value.positive}") Integer timeMinutes,
    @Min(value = 0, message = "{validation.value.positive}") Integer timeSeconds,

    // --- Volume Metrics ---
    @Min(value = 0, message = "{validation.value.positive}") Integer rounds,
    @Min(value = 0, message = "{validation.value.positive}") Integer reps,

    // --- Load Metrics ---
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double maxWeight,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double totalLoad,
    @UnitOf(UnitField.MASS) Unit weightUnit,

    // --- Distance Metrics ---
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double totalDistance,
    @UnitOf(UnitField.DISTANCE) Unit distanceUnit,
    // --- Calories ---
    @Min(value = 0, message = "{validation.value.positive}") Integer totalCalories,

    // --- Performance Context: RX = no change to the prescription, SCALED = changes per line ---
    @NotNull(message = "{validation.scaling.required}") ScalingLevel scaling,
    boolean timeCapped,
    // Optional official tiebreak time (elapsed seconds); ignored when the WOD has no tiebreak
    @Min(value = 1, message = "{validation.value.positive}") Integer tiebreakSeconds,

    // --- Comment ---
    @Size(max = 4000, message = "{validation.score.comment.size}") String userComment,

    // --- Gym where it was performed (public or personal WOD only; a gym WOD uses its gym) ---
    Long gymId,

    // --- Session: journal of the session the score belongs to (optional for a session WOD:
    // the athlete's journal is found or created) ---
    Long sessionLogId,

    // --- Session block the score is logged for (optional: derived from the WOD when it is held
    // by one block of the session; needed when two blocks borrow the same WOD) ---
    Long sessionBlockId,

    // --- Strength sets, set by set, each on its line (WEIGHT / LOAD / NONE scores), in weightUnit
    @Valid @Size(max = 50, message = "{validation.score.sets.size}") List<ScoreSetRequest> sets,

    // --- Lines done otherwise than prescribed (SCALED), or the reps of "max" lines (D142, D147)
    @Valid @Size(max = 50, message = "{validation.score.lines.size}") List<ScoreLineRequest> lines,

    // --- Competition WOD: submit the score to the judges ---
    boolean verificationRequested,

    // --- Team WOD: the other members of the team (same box), who get the same score ---
    @Size(max = 5, message = "{validation.score.team.size}") List<Long> teammateIds) {

  /** A score without changed lines. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public ScoreRequest(
      Long userId, LocalDate date, Long wodId, Integer timeMinutes, Integer timeSeconds,
      Integer rounds, Integer reps, Double maxWeight, Double totalLoad, Unit weightUnit,
      Double totalDistance, Unit distanceUnit, Integer totalCalories, ScalingLevel scaling,
      boolean timeCapped, Integer tiebreakSeconds, String userComment, Long gymId,
      Long sessionLogId, List<ScoreSetRequest> sets, boolean verificationRequested,
      List<Long> teammateIds) {
    this(
        userId, date, wodId, timeMinutes, timeSeconds, rounds, reps, maxWeight, totalLoad,
        weightUnit, totalDistance, distanceUnit, totalCalories, scaling, timeCapped,
        tiebreakSeconds, userComment, gymId, sessionLogId, null, sets, null,
        verificationRequested, teammateIds);
  }

  /** An individual score without changed lines. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public ScoreRequest(
      Long userId, LocalDate date, Long wodId, Integer timeMinutes, Integer timeSeconds,
      Integer rounds, Integer reps, Double maxWeight, Double totalLoad, Unit weightUnit,
      Double totalDistance, Unit distanceUnit, Integer totalCalories, ScalingLevel scaling,
      boolean timeCapped, Integer tiebreakSeconds, String userComment, Long gymId,
      Long sessionLogId, List<ScoreSetRequest> sets, boolean verificationRequested) {
    this(
        userId, date, wodId, timeMinutes, timeSeconds, rounds, reps, maxWeight, totalLoad,
        weightUnit, totalDistance, distanceUnit, totalCalories, scaling, timeCapped,
        tiebreakSeconds, userComment, gymId, sessionLogId, sets, verificationRequested, null);
  }

  /** An individual score without sets nor changed lines, outside any session. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public ScoreRequest(
      Long userId, LocalDate date, Long wodId, Integer timeMinutes, Integer timeSeconds,
      Integer rounds, Integer reps, Double maxWeight, Double totalLoad, Unit weightUnit,
      Double totalDistance, Unit distanceUnit, Integer totalCalories, ScalingLevel scaling,
      boolean timeCapped, Integer tiebreakSeconds, String userComment, Long gymId) {
    this(
        userId, date, wodId, timeMinutes, timeSeconds, rounds, reps, maxWeight, totalLoad,
        weightUnit, totalDistance, distanceUnit, totalCalories, scaling, timeCapped,
        tiebreakSeconds, userComment, gymId, null, null, false);
  }
}
