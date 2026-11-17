package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.ComponentTechnique;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
import apex.stellar.aldebaran.model.entities.WodMovement.TeamSplit;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.model.enums.UnitField;
import apex.stellar.aldebaran.validation.UnitOf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** DTO for specifying a single movement within a WOD creation or update request. */
public record WodMovementRequest(
    // ---  Identification ---
    @NotNull(message = "{validation.movement.id.required}") Long movementId,
    @NotNull(message = "{validation.order.required}")
        @Min(value = 1, message = "{validation.order.positive}")
        Integer orderIndex,

    // --- Prescription ---
    @Size(max = 50, message = "{validation.wod.repsScheme.size}") String repsScheme,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weight,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weightFemale,
    @UnitOf(UnitField.MASS) Unit weightUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer durationSeconds,
    @UnitOf(UnitField.TIME) Unit durationDisplayUnit,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double distance,
    @UnitOf(UnitField.DISTANCE) Unit distanceUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer calories,
    @Min(value = 0, message = "{validation.value.positive}") Integer caloriesFemale,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double height,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double heightFemale,
    @UnitOf(UnitField.HEIGHT) Unit heightUnit,

    // --- Characteristics ---
    Set<String> equipment,
    Set<String> techniques,

    // --- Instructions ---
    @Size(max = 4000, message = "{validation.wod.notes.size}") String notesEn,
    @Size(max = 4000, message = "{validation.wod.notes.size}") String notesFr,

    // --- Sets (strength / skill work), optional ---
    @Min(value = 1, message = "{validation.wod.sets.min}") Integer sets,

    // --- Part of the WOD (buy-in, rounds, buy-out); null = MAIN ---
    Segment segment,

    // --- Group of a weightlifting complex, or null ---
    @Min(value = 1, message = "{validation.wod.rounds.min}") Integer complexGroup,

    // --- Team WOD: in total (default), by each member, or together ---
    TeamSplit teamSplit,

    // --- Load as a percentage of the athlete's RM (base version of the movement), optional ---
    @DecimalMin(value = "0.0", inclusive = false, message = "{validation.wod.percent.range}")
        @DecimalMax(value = "150.0", message = "{validation.wod.percent.range}")
        Double percentOfRm,
    @Min(value = 1, message = "{validation.wod.rmReps.range}")
        @Max(value = 50, message = "{validation.wod.rmReps.range}")
        Integer rmReps,

    // --- Techniques of one component of a chained line (receptions), optional ---
    Set<ComponentTechnique> componentTechniques,

    // --- Length of the course (shuttle run: between the two lines), optional ---
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double length,
    @UnitOf(UnitField.LENGTH) Unit lengthUnit,

    // --- This line in the WOD's official Scaled version, or null (kept RX) ---
    @Valid WodScaledLineRequest scaled) {

  /** Line without an official Scaled version. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr,
      Integer sets,
      Segment segment,
      Integer complexGroup,
      TeamSplit teamSplit,
      Double percentOfRm,
      Integer rmReps,
      Set<ComponentTechnique> componentTechniques,
      Double length,
      Unit lengthUnit) {
    this(
        movementId, orderIndex, repsScheme, weight, weightFemale, weightUnit, durationSeconds,
        durationDisplayUnit, distance, distanceUnit, calories, caloriesFemale, height, heightFemale,
        heightUnit, equipment, techniques, notesEn, notesFr, sets, segment, complexGroup, teamSplit,
        percentOfRm, rmReps, componentTechniques, length, lengthUnit, null);
  }

  /** Line without a course length. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr,
      Integer sets,
      Segment segment,
      Integer complexGroup,
      TeamSplit teamSplit,
      Double percentOfRm,
      Integer rmReps,
      Set<ComponentTechnique> componentTechniques) {
    this(
        movementId, orderIndex, repsScheme, weight, weightFemale, weightUnit, durationSeconds,
        durationDisplayUnit, distance, distanceUnit, calories, caloriesFemale, height, heightFemale,
        heightUnit, equipment, techniques, notesEn, notesFr,
        sets, segment, complexGroup, teamSplit, percentOfRm, rmReps, componentTechniques, null, null,
        null);
  }

  /** Line without per-component techniques. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr,
      Integer sets,
      Segment segment,
      Integer complexGroup,
      TeamSplit teamSplit,
      Double percentOfRm,
      Integer rmReps) {
    this(
        movementId, orderIndex, repsScheme, weight, weightFemale, weightUnit, durationSeconds,
        durationDisplayUnit, distance, distanceUnit, calories, caloriesFemale, height, heightFemale,
        heightUnit, equipment, techniques, notesEn, notesFr,
        sets, segment, complexGroup, teamSplit, percentOfRm, rmReps, null);
  }

  /** Defaults the segment to the rounds and the team split to the total. */
  public WodMovementRequest {
    if (componentTechniques == null) {
      componentTechniques = Set.of();
    }
    if (segment == null) {
      segment = Segment.MAIN;
    }
    if (teamSplit == null) {
      teamSplit = TeamSplit.TOTAL;
    }
  }

  /** Line without a percentage load. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr,
      Integer sets,
      Segment segment,
      Integer complexGroup,
      TeamSplit teamSplit) {
    this(
        movementId, orderIndex, repsScheme, weight, weightFemale, weightUnit, durationSeconds,
        durationDisplayUnit, distance, distanceUnit, calories, caloriesFemale, height, heightFemale,
        heightUnit, equipment, techniques, notesEn, notesFr,
        sets, segment, complexGroup, teamSplit, null, null);
  }

  /** Line of an individual WOD, possibly in a complex. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr,
      Integer sets,
      Segment segment,
      Integer complexGroup) {
    this(
        movementId, orderIndex, repsScheme, weight, weightFemale, weightUnit, durationSeconds,
        durationDisplayUnit, distance, distanceUnit, calories, caloriesFemale, height, heightFemale,
        heightUnit, equipment, techniques, notesEn, notesFr,
        sets, segment, complexGroup, TeamSplit.TOTAL);
  }

  /** Line outside any complex. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr,
      Integer sets,
      Segment segment) {
    this(
        movementId, orderIndex, repsScheme, weight, weightFemale, weightUnit, durationSeconds,
        durationDisplayUnit, distance, distanceUnit, calories, caloriesFemale, height, heightFemale,
        heightUnit, equipment, techniques, notesEn, notesFr,
        sets, segment, null);
  }

  /** Movement of the rounds, with optional sets. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr,
      Integer sets) {
    this(
        movementId, orderIndex, repsScheme, weight, weightFemale, weightUnit, durationSeconds,
        durationDisplayUnit, distance, distanceUnit, calories, caloriesFemale, height, heightFemale,
        heightUnit, equipment, techniques, notesEn, notesFr,
        sets, Segment.MAIN);
  }

  /** Movement prescribed without a number of sets. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public WodMovementRequest(
      Long movementId,
      Integer orderIndex,
      String repsScheme,
      Double weight,
      Double weightFemale,
      Unit weightUnit,
      Integer durationSeconds,
      Unit durationDisplayUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer caloriesFemale,
      Double height,
      Double heightFemale,
      Unit heightUnit,
      Set<String> equipment,
      Set<String> techniques,
      String notesEn,
      String notesFr) {
    this(
        movementId,
        orderIndex,
        repsScheme,
        weight,
        weightFemale,
        weightUnit,
        durationSeconds,
        durationDisplayUnit,
        distance,
        distanceUnit,
        calories,
        caloriesFemale,
        height,
        heightFemale,
        heightUnit,
        equipment,
        techniques,
        notesEn,
        notesFr,
        null);
  }
}
