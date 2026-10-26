package apex.stellar.aldebaran.dto;

/**
 * Rights of the current user on a score, computed by the backend so that clients do not duplicate
 * authorization rules.
 *
 * @param canModify Whether the user may update or delete the score (its athlete, a staff member
 *     holding SCORE_VERIFY in the WOD's gym, or an admin).
 */
public record ScoreCapabilities(boolean canModify) {}
