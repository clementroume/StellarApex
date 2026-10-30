package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Unit;
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
    boolean competition,
    // WOD this one was copied from, if any
    Long sourceWodId,
    // false for the WOD of a session block (read and edited through its session)
    boolean standalone,

    // --- Description and Notes (EN / FR) ---
    String descriptionEn,
    String descriptionFr,
    String notesEn,
    String notesFr,

    // --- Official tiebreak (EN / FR), null when the WOD has none ---
    String tiebreakEn,
    String tiebreakFr,

    // --- Structure ---
    Integer timeCapSeconds,
    Integer repsPerRound,
    Integer emomInterval,
    Integer emomRounds,
    String repScheme,
    Integer rounds,
    Integer restSeconds,
    Integer workSeconds,
    Integer ladderStart,
    Integer ladderStep,
    Double vestWeight,
    Double vestWeightFemale,
    Unit vestWeightUnit,
    WodStructure structure,
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
        competition,
        sourceWodId,
        standalone,
        descriptionEn,
        descriptionFr,
        notesEn,
        notesFr,
        tiebreakEn,
        tiebreakFr,
        timeCapSeconds,
        repsPerRound,
        emomInterval,
        emomRounds,
        repScheme,
        rounds,
        restSeconds,
        workSeconds,
        ladderStart,
        ladderStep,
        vestWeight,
        vestWeightFemale,
        vestWeightUnit,
        structure,
        modalities,
        movements,
        createdAt,
        updatedAt,
        capabilities);
  }
}
