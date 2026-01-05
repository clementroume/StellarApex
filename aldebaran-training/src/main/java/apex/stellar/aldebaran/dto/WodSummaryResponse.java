package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Lightweight DTO for listing WODs without heavy details.
 *
 * <p>Used for list views, autocomplete dropdowns, and embedding minimal WOD context within other
 * objects (like Scores). Matches the fields provided by {@link
 * apex.stellar.aldebaran.repository.projection.WodSummary}.
 *
 * @param id The unique identifier of the WOD.
 * @param title The title of the workout.
 * @param wodType The structural type (e.g., AMRAP, FOR_TIME).
 * @param scoreType The scoring metric (e.g., ROUNDS, TIME).
 * @param repScheme A summary string of the repetition scheme (e.g., "21-15-9").
 * @param timeCapSeconds The time cap in seconds, if applicable.
 * @param createdAt The creation timestamp, useful for sorting or display.
 */
public record WodSummaryResponse(
    @Schema(description = "Unique WOD ID", example = "101") Long id,
    @Schema(description = "Title of the workout", example = "Murph") String title,
    @Schema(description = "Structural type", example = "FOR_TIME") WodType wodType,
    @Schema(description = "Primary scoring metric", example = "TIME") ScoreType scoreType,
    @Schema(description = "Summary rep scheme", example = "21-15-9") String repScheme,
    @Schema(description = "Time cap in seconds", example = "3600") Integer timeCapSeconds,
    @Schema(description = "Creation timestamp") LocalDateTime createdAt) {}
