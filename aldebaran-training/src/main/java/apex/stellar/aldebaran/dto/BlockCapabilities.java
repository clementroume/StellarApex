package apex.stellar.aldebaran.dto;

/**
 * Per-user flags of a session block, computed per request.
 *
 * @param canModify The user may change the block's kind and WOD (false once frozen).
 * @param canDelete The user may remove the block (false once frozen or checked in a journal).
 * @param frozen A journal of the session holds scores of this block: kind, WOD and removal are
 *     refused (the other fields stay editable).
 */
public record BlockCapabilities(boolean canModify, boolean canDelete, boolean frozen) {}
