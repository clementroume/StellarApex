package apex.stellar.aldebaran.dto;

/**
 * Per-user flags of a session, computed per request.
 *
 * @param canModify The user may edit, delete the session and change its visibility.
 * @param locked Someone logged the session: blocks are frozen (visibility stays editable).
 * @param contentVisible The user reads the blocks' content.
 * @param canLog The user may log the session ("Séance faite") now.
 */
public record SessionCapabilities(
    boolean canModify, boolean locked, boolean contentVisible, boolean canLog) {}
