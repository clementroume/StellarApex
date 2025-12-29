package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight DTO for listing WODs without heavy details.
 *
 * <p>Used for list views, autocomplete dropdowns, and embedding minimal WOD context within other
 * objects (like Scores).
 */
public record WodSummaryResponse(
    @Schema(description = "Unique WOD ID", example = "101") Long id,
    @Schema(description = "Title of the workout", example = "Murph") String title,
    @Schema(description = "Structural type", example = "FOR_TIME") WodType wodType,
    @Schema(description = "Primary scoring metric", example = "TIME") ScoreType scoreType) {}
