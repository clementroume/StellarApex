package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Unit;
import java.util.Set;

/**
 * Nested DTO for WodResponse.
 *
 * <p>Contains the prescription details linked to the full Movement definition.
 */
public record WodMovementResponse(
    // --- Identification ---
    Long id,
    MovementResponse movement,
    Integer orderIndex,

    // --- Prescription ---
    String repsScheme,
    Double weight,
    Unit weightUnit,
    Integer durationSeconds,
    Unit durationDisplayUnit,
    Double distance,
    Unit distanceUnit,
    Integer calories,

    // --- Characteristics ---
    Set<String> equipment,
    Set<String> techniques,

    // --- Instructions ---
    String notes,
    String scalingOptions) {}
