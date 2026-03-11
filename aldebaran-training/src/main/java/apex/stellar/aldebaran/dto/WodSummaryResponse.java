package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.time.LocalDateTime;

/**
 * Lightweight DTO for listing WODs without heavy details.
 *
 * <p>Used for list views, autocomplete dropdowns, and embedding minimal WOD context within other
 * objects (like Scores). Matches the fields provided by {@link WodSummary}.
 */
public record WodSummaryResponse(
    Long id,
    String title,
    WodType wodType,
    ScoreType scoreType,
    String repScheme,
    Integer timeCapSeconds,
    LocalDateTime createdAt) {}
