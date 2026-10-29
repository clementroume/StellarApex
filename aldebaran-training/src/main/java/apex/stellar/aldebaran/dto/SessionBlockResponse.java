package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.SessionBlock.Stimulus;

/**
 * One block of a session. The frame (kind, themes, optional) is always shown; label, texts and WOD
 * are null while the content is hidden from the user.
 *
 * @param borrowed The block borrows a standalone WOD (its scores join that WOD's leaderboard).
 * @param competition The block's WOD is a competition WOD of the box.
 */
public record SessionBlockResponse(
    Long id,
    Integer orderIndex,
    Kind kind,
    boolean optional,
    String themeEn,
    String themeFr,
    Stimulus stimulus,
    Integer plannedMinutes,
    String label,
    String textEn,
    String textFr,
    WodResponse wod,
    boolean borrowed,
    boolean competition) {}
