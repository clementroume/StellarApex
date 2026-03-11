package apex.stellar.aldebaran.dto;

/** DTO for score ranking and percentile analysis. */
public record ScoreComparisonResponse(Long rank, Long totalScores, Double percentile) {}
