package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Unit;
import java.util.Set;

/**
 * A line in the WOD's official Scaled version, with every value of that version filled in (the RX
 * one where the version keeps it) and how it reads.
 */
public record WodScaledLineResponse(
    MovementSummaryResponse movement,
    Set<String> equipment,
    Set<String> techniques,
    String repsScheme,
    Double weight,
    Double weightFemale,
    Unit weightUnit,
    Double distance,
    Unit distanceUnit,
    Integer calories,
    Integer caloriesFemale,
    Double height,
    Double heightFemale,
    Unit heightUnit,
    Integer durationSeconds,
    WodMovementDisplay display,
    LineEquivalents equivalents) {}
