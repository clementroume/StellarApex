package apex.stellar.aldebaran.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** DTO for score ranking and percentile analysis. */
public record ScoreComparisonResponse(
    @Schema(description = "Rank of the score (1 is best)", example = "5") Long rank,
    @Schema(description = "Total number of scores in this category", example = "100")
        Long totalScores,
    @Schema(description = "Percentile (0-100, higher is better)", example = "95.0")
        Double percentile) {}
