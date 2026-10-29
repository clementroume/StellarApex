package apex.stellar.aldebaran.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One set of a strength score, in the weight unit of the score.
 *
 * @param reps Reps done.
 * @param weight Load lifted, or null (bodyweight).
 * @param completed False for a failed or partial set (never the performance); true when omitted.
 */
public record ScoreSetRequest(
    @NotNull(message = "{validation.score.set.reps.required}")
        @Min(value = 0, message = "{validation.value.positive}")
        Integer reps,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weight,
    Boolean completed) {}
