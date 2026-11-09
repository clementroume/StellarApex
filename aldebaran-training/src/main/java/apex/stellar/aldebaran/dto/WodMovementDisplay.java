package apex.stellar.aldebaran.dto;

import java.util.Set;

/**
 * How a WOD line reads: the most precise usual name contained in the line, then what remains. The
 * catalog decides it, Sirius only translates the remaining words.
 *
 * @param usualNameEn Usual name in English, or null when none applies.
 * @param usualNameFr Usual name in French, null when identical or none applies.
 * @param remainingEquipment Equipment the name does not say (implied equipment excluded).
 * @param remainingTechniques Techniques the name does not say.
 * @param implementCount Dumbbells or kettlebells used, or null when not applicable or free.
 * @param variant Whether the line is more than the movement's base version.
 * @param percentBaseKey Variant key of the record a percentage of RM refers to.
 */
public record WodMovementDisplay(
    String usualNameEn,
    String usualNameFr,
    Set<String> remainingEquipment,
    Set<String> remainingTechniques,
    Integer implementCount,
    boolean variant,
    String percentBaseKey) {}
