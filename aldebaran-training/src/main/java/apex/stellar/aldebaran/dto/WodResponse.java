package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** DTO representing the full details of a WOD, including its ordered movements. */
public record WodResponse(

    // --- Identification ---
    Long id,
    String title,
    WodType wodType,
    WodCategory category,
    ScoreType scoreType,

    // --- Authorship and Visibility ---
    Long authorId,
    Long gymId,
    boolean isPublic,

    // --- Description and Notes (EN / FR) ---
    String descriptionEn,
    String descriptionFr,
    String notesEn,
    String notesFr,

    // --- Structure ---
    Integer timeCapSeconds,
    Integer emomInterval,
    Integer emomRounds,
    String repScheme,
    Set<Modality> modalities,
    List<WodMovementResponse> movements,

    // --- Audit ---
    LocalDateTime createdAt,
    LocalDateTime updatedAt,

    // --- Per-user rights (computed per request, never cached) ---
    WodCapabilities capabilities) {

  /**
   * Returns a copy of this response carrying the given capabilities.
   *
   * @param capabilities The rights of the current user on this WOD.
   * @return A new response with the capabilities set.
   */
  public WodResponse withCapabilities(WodCapabilities capabilities) {
    return new WodResponse(
        id,
        title,
        wodType,
        category,
        scoreType,
        authorId,
        gymId,
        isPublic,
        descriptionEn,
        descriptionFr,
        notesEn,
        notesFr,
        timeCapSeconds,
        emomInterval,
        emomRounds,
        repScheme,
        modalities,
        movements,
        createdAt,
        updatedAt,
        capabilities);
  }
}
