package apex.stellar.aldebaran.dto;

import java.util.List;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import java.time.LocalDateTime;

/** Lightweight DTO for listing WODs without heavy details. */
public record WodSummaryResponse(
    Long id,
    String title,
    WodType wodType,
    WodCategory category,
    ScoreType scoreType,
    String repScheme,
    Integer timeCapSeconds,
    Integer repsPerRound,
    LocalDateTime createdAt,
    Long authorId,
    Long gymId,
    boolean isPublic,
    boolean competition,
    // Sub-category of the format, or null
    WodStructure structure,
    // Movement names in order (catalogue preview), filled by the service
    List<String> movementNames) {

  /**
   * Returns a copy of this summary with the names of its movements.
   *
   * @param names Movement names in order.
   * @return A new summary.
   */
  public WodSummaryResponse withMovementNames(List<String> names) {
    return new WodSummaryResponse(
        id, title, wodType, category, scoreType, repScheme, timeCapSeconds, repsPerRound, createdAt,
        authorId, gymId, isPublic, competition, structure, names);
  }
}
