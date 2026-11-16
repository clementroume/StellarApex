package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.ComponentTechnique;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
import apex.stellar.aldebaran.model.entities.WodMovement.TeamSplit;
import apex.stellar.aldebaran.model.enums.Unit;
import java.util.Set;

/** Nested DTO for WodResponse. */
public record WodMovementResponse(

    // --- Identification ---
    Long id,
    MovementResponse movement,
    Integer orderIndex,

    // --- Prescription ---
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

    // --- Characteristics ---
    Set<String> equipment,
    Set<String> techniques,

    // --- Instructions ---
    String notesEn,
    String notesFr,

    // --- Sets (strength / skill work), or null ---
    Integer sets,

    // --- Part of the WOD: BUY_IN, MAIN or BUY_OUT ---
    Segment segment,

    // --- Group of a weightlifting complex, or null ---
    Integer complexGroup,

    // --- Team WOD: TOTAL, EACH or TOGETHER ---
    TeamSplit teamSplit,

    // --- Load as a percentage of the athlete's rmReps RM (base version), or null ---
    Double percentOfRm,
    Integer rmReps,

    // --- Techniques of one component of a chained line ---
    Set<ComponentTechnique> componentTechniques,

    // --- Length of the course (shuttle run: between the two lines), or null ---
    Double length,
    Unit lengthUnit,

    // --- How the line reads, decided by the catalog ---
    WodMovementDisplay display,

    // --- This line in the WOD's official Scaled version, or null (kept RX) ---
    WodScaledLineResponse scaled) {}
