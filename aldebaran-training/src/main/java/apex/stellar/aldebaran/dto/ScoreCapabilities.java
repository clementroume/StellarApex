package apex.stellar.aldebaran.dto;

/**
 * Rights of the current user on a score, computed by the backend so that clients do not duplicate
 * authorization rules.
 *
 * @param canModify Whether the user may update or delete the score (its athlete while it is not
 *     verified, a staff member holding SCORE_VERIFY in the score's gym, or an admin).
 * @param canVerify Whether the user may verify the score or withdraw its verification (staff
 *     holding SCORE_VERIFY in the gym of a competition WOD, never on their own score, or an
 *     admin).
 */
public record ScoreCapabilities(boolean canModify, boolean canVerify) {}
