package apex.stellar.aldebaran.dto;

/**
 * One set of a strength score.
 *
 * @param reps Reps done.
 * @param weight Load lifted, in the score's weight unit, or null.
 * @param completed False for a failed or partial set.
 * @param wodMovementId The line of the WOD the set was done on (a complex: its first line).
 */
public record ScoreSetResponse(Integer reps, Double weight, boolean completed, Long wodMovementId) {}
