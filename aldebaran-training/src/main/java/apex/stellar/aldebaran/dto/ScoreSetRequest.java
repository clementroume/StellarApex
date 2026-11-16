package apex.stellar.aldebaran.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One set of a strength score, in the weight unit of the score, on one line of the WOD (D143).
 *
 * @param reps Reps done.
 * @param weight Load lifted, or null (bodyweight).
 * @param completed False for a failed or partial set (never the performance); true when omitted.
 * @param wodMovementId The line of the WOD (a complex: its first line); may be omitted when the WOD
 *     has a single movement or a single complex.
 */
public record ScoreSetRequest(
    @NotNull(message = "{validation.score.set.reps.required}")
        @Min(value = 0, message = "{validation.value.positive}")
        Integer reps,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weight,
    Boolean completed,
    Long wodMovementId) {

  /** A set on the WOD's only movement or complex. */
  public ScoreSetRequest(Integer reps, Double weight, Boolean completed) {
    this(reps, weight, completed, null);
  }
}
