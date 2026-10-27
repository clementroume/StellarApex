package apex.stellar.aldebaran.dto;

/**
 * Rights of the current user on a WOD, computed by the backend so that clients do not duplicate
 * authorization rules.
 *
 * @param canModify Whether the user may update or delete the WOD.
 * @param locked Whether the WOD is locked because scores have been logged against it (updates and
 *     deletions are then rejected even when {@code canModify} is true).
 * @param canJudge Whether the user judges this competition WOD: logs scores for the gym's members
 *     and verifies scores (SCORE_VERIFY in its gym, or admin). Always false outside competitions.
 */
public record WodCapabilities(boolean canModify, boolean locked, boolean canJudge) {}
