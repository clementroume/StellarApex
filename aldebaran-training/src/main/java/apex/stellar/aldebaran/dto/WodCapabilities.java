package apex.stellar.aldebaran.dto;

/**
 * Rights of the current user on a WOD, computed by the backend so that clients do not duplicate
 * authorization rules.
 *
 * @param canModify Whether the user may update or delete the WOD.
 * @param locked Whether the WOD is locked because scores have been logged against it (updates and
 *     deletions are then rejected even when {@code canModify} is true).
 */
public record WodCapabilities(boolean canModify, boolean locked) {}
