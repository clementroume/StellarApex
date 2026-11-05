package apex.stellar.aldebaran.dto;

/**
 * Per-user flags of a session, computed per request.
 *
 * @param canModify The user may edit the session (fields, blocks, visibility); blocks carry their
 *     own {@link BlockCapabilities}.
 * @param canDelete The user may delete the session: false once someone logged it (journals point
 *     to a dated session). The date and the type are then locked too.
 * @param contentVisible The user reads the blocks' content.
 * @param canLog The user may log the session ("Séance faite") now.
 */
public record SessionCapabilities(
    boolean canModify, boolean canDelete, boolean contentVisible, boolean canLog) {}
