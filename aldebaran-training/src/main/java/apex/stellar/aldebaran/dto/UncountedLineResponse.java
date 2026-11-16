package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.UncountedReason;
import org.jspecify.annotations.Nullable;

/**
 * A WOD line that counts nothing in the weekly balance (D121 rule 5, D126): admin control.
 *
 * @param wodId The WOD.
 * @param wodTitle Its title.
 * @param isPublic Whether it is a public WOD (else a gym, personal or session WOD).
 * @param standalone False for a WOD owned by a session block.
 * @param orderIndex Position of the line in the WOD.
 * @param movementName The line's movement.
 * @param repsScheme The text of the line, or null.
 * @param reason Why it counts nothing.
 */
public record UncountedLineResponse(
    Long wodId,
    String wodTitle,
    boolean isPublic,
    boolean standalone,
    int orderIndex,
    String movementName,
    @Nullable String repsScheme,
    UncountedReason reason) {}
