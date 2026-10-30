package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** DTO for creating or updating a WOD (Workout of the Day). */
public record WodRequest(

    // --- Identification ---
    @NotBlank(message = "{validation.wod.title.required}")
        @Size(max = 100, message = "{validation.wod.title.size}")
        String title,
    @NotNull(message = "{validation.wod.type.required}") WodType wodType,
    WodCategory category,
    @NotNull(message = "{validation.wod.scoreType.required}") ScoreType scoreType,

    // --- Authorship and Visibility ---
    Long authorId,
    Long gymId,
    boolean isPublic,
    // Competition WOD: gym WOD only, never public
    boolean competition,

    // --- Description and Notes (EN / FR, either may be empty) ---
    @Size(max = 4000, message = "{validation.wod.description.size}") String descriptionEn,
    @Size(max = 4000, message = "{validation.wod.description.size}") String descriptionFr,
    @Size(max = 4000, message = "{validation.wod.notes.size}") String notesEn,
    @Size(max = 4000, message = "{validation.wod.notes.size}") String notesFr,

    // --- Official tiebreak (EN / FR), only for WODs that define one ---
    @Size(max = 1000, message = "{validation.wod.tiebreak.size}") String tiebreakEn,
    @Size(max = 1000, message = "{validation.wod.tiebreak.size}") String tiebreakFr,

    // --- Structure and Prescription ---
    @Min(value = 0, message = "{validation.wod.timeCap.min}") Integer timeCapSeconds,
    @Min(value = 1, message = "{validation.wod.repsPerRound.min}") Integer repsPerRound,
    @Min(value = 0, message = "{validation.wod.emom.min}") Integer emomInterval,
    @Min(value = 0, message = "{validation.wod.emom.min}") Integer emomRounds,
    @Size(max = 100, message = "{validation.wod.repScheme.size}") String repScheme,
    @NotEmpty(message = "{validation.wod.movements.empty}") @Valid
        List<WodMovementRequest> movements,

    // --- Structure of the quick editor (rounds, rest, intervals, ascending ladder, vest) ---
    @Min(value = 1, message = "{validation.wod.rounds.min}") Integer rounds,
    @Min(value = 0, message = "{validation.value.positive}") Integer restSeconds,
    @Min(value = 1, message = "{validation.wod.rounds.min}") Integer workSeconds,
    @Min(value = 1, message = "{validation.wod.rounds.min}") Integer ladderStart,
    @Min(value = 1, message = "{validation.wod.rounds.min}") Integer ladderStep,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double vestWeight,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double vestWeightFemale,
    Unit vestWeightUnit,

    // --- Sub-category of the format (For Time: Rounds, Scheme, Chipper...), optional ---
    WodStructure structure) {

  /** WOD with the quick editor structure, without a sub-category. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodRequest(
      String title,
      WodType wodType,
      WodCategory category,
      ScoreType scoreType,
      Long authorId,
      Long gymId,
      boolean isPublic,
      boolean competition,
      String descriptionEn,
      String descriptionFr,
      String notesEn,
      String notesFr,
      String tiebreakEn,
      String tiebreakFr,
      Integer timeCapSeconds,
      Integer repsPerRound,
      Integer emomInterval,
      Integer emomRounds,
      String repScheme,
      List<WodMovementRequest> movements,
      Integer rounds,
      Integer restSeconds,
      Integer workSeconds,
      Integer ladderStart,
      Integer ladderStep,
      Double vestWeight,
      Double vestWeightFemale,
      Unit vestWeightUnit) {
    this(
        title, wodType, category, scoreType, authorId, gymId, isPublic, competition, descriptionEn,
        descriptionFr, notesEn, notesFr, tiebreakEn, tiebreakFr, timeCapSeconds, repsPerRound,
        emomInterval, emomRounds, repScheme, movements, rounds, restSeconds, workSeconds,
        ladderStart, ladderStep, vestWeight, vestWeightFemale, vestWeightUnit, null);
  }

  /** WOD without the structure fields of the quick editor. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodRequest(
      String title,
      WodType wodType,
      WodCategory category,
      ScoreType scoreType,
      Long authorId,
      Long gymId,
      boolean isPublic,
      boolean competition,
      String descriptionEn,
      String descriptionFr,
      String notesEn,
      String notesFr,
      String tiebreakEn,
      String tiebreakFr,
      Integer timeCapSeconds,
      Integer repsPerRound,
      Integer emomInterval,
      Integer emomRounds,
      String repScheme,
      List<WodMovementRequest> movements) {
    this(
        title, wodType, category, scoreType, authorId, gymId, isPublic, competition, descriptionEn,
        descriptionFr, notesEn, notesFr, tiebreakEn, tiebreakFr, timeCapSeconds, repsPerRound,
        emomInterval, emomRounds, repScheme, movements, null, null, null, null, null, null, null,
        null);
  }

  /**
   * Returns a copy of this request owned by a gym (author resolved by the service), never public.
   *
   * @param gymId The owning gym.
   * @param competitionWod Whether the copy is a competition WOD.
   * @param movementList The movements of the copy.
   * @return The new request.
   */
  public WodRequest ownedBy(Long gymId, boolean competitionWod, List<WodMovementRequest> movementList) {
    return new WodRequest(
        title, wodType, category, scoreType, null, gymId, false, competitionWod, descriptionEn,
        descriptionFr, notesEn, notesFr, tiebreakEn, tiebreakFr, timeCapSeconds, repsPerRound,
        emomInterval, emomRounds, repScheme, movementList, rounds, restSeconds, workSeconds,
        ladderStart, ladderStep, vestWeight, vestWeightFemale, vestWeightUnit, structure);
  }
}
