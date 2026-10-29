package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.SessionBlock.Stimulus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO of one block of a session request. A text block (warm-up, cool down) carries text only; any
 * other block carries either {@code wodId} (borrowed standalone WOD) or {@code wod} (WOD created
 * for the session, or updated in place when the block already owns one).
 *
 * @param id Existing block ID on update (kept, with its journal checks), or null for a new block.
 * @param stimulus Kind of effort, or null to let Aldebaran derive it.
 * @param competition Competition block (physical box only): the block's WOD becomes a competition
 *     WOD of the box (a borrowed WOD is copied).
 */
public record SessionBlockRequest(
    Long id,
    @NotNull(message = "{validation.session.block.kind.required}") Kind kind,
    @Size(max = 30, message = "{validation.session.block.label.size}") String label,
    @Size(max = 80, message = "{validation.session.block.theme.size}") String themeEn,
    @Size(max = 80, message = "{validation.session.block.theme.size}") String themeFr,
    boolean optional,
    Stimulus stimulus,
    @Min(value = 0, message = "{validation.value.positive}") Integer plannedMinutes,
    @Size(max = 4000, message = "{validation.session.block.text.size}") String textEn,
    @Size(max = 4000, message = "{validation.session.block.text.size}") String textFr,
    boolean competition,
    Long wodId,
    @Valid WodRequest wod) {}
