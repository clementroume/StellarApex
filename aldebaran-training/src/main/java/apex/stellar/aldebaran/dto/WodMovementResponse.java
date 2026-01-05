package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Unit;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Nested DTO for WodResponse.
 *
 * <p>Contains the prescription details linked to the full Movement definition.
 */
public record WodMovementResponse(
    @Schema(description = "Unique ID of this WOD component", example = "505") Long id,
    @Schema(description = "Sequence order (1-based)", example = "1") Integer orderIndex,
    @Schema(description = "Specific rep scheme for this movement", example = "21")
        String repsScheme,
    @Schema(description = "Prescribed weight", example = "60.0") Double weight,
    @Schema(description = "Unit for the weight", example = "KG") Unit weightUnit,
    @Schema(description = "Duration in seconds (static holds/cardio)", example = "60")
        Integer durationSeconds,
    @Schema(description = "Preferred display unit for duration", example = "SECONDS")
        Unit durationDisplayUnit,
    @Schema(description = "Distance value", example = "400.0") Double distance,
    @Schema(description = "Unit for distance", example = "METERS") Unit distanceUnit,
    @Schema(description = "Calories target", example = "20") Integer calories,
    @Schema(description = "Movement specific notes", example = "Butterfly allowed") String notes,
    @Schema(description = "Scaling options text", example = "Use bands") String scalingOptions,
    @Schema(description = "The standard movement definition details") MovementResponse movement) {}
