package apex.stellar.aldebaran.dto;

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
    String scalingOptionsEn,
    String scalingOptionsFr) {}
