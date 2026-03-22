package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import java.time.LocalDateTime;

/** Lightweight DTO for listing WODs without heavy details. */
public record WodSummaryResponse(
    Long id,
    String title,
    WodType wodType,
    ScoreType scoreType,
    String repScheme,
    Integer timeCapSeconds,
    LocalDateTime createdAt) {}
