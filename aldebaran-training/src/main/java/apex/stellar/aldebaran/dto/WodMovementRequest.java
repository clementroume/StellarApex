package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Unit;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for specifying a single movement within a WOD creation or update request.
 *
 * <p>Represents the "ingredient" line in a workout recipe, defining the exercise and its specific
 * prescription (Rx) for this workflow.
 */
public record WodMovementRequest(
    @Schema(
            description = "ID of the movement to include (Business Key or UUID)",
            example = "WL-SQ-001",
            requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.movement.id.required}")
        String movementId,
    @Schema(
            description = "Sequence order in the WOD (1-based)",
            example = "1",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "{validation.order.required}")
        @Min(value = 1, message = "{validation.order.positive}")
        Integer orderIndex,
    @Schema(
            description = "Rep scheme string (e.g., '21-15-9', '5x5')",
            example = "21-15-9",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 50, message = "{validation.wod.repsScheme.size}")
        String repsScheme,

    // --- Prescription Data ---

    @Schema(
            description = "Prescribed weight value (if applicable)",
            example = "60.0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @DecimalMin(value = "0.0", message = "{validation.value.positive}")
        Double weight,
    @Schema(
            description = "Unit for the prescribed weight",
            example = "KG",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Unit weightUnit,
    @Schema(
            description = "Target duration in seconds (for static holds or cardio)",
            example = "60",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.value.positive}")
        Integer durationSeconds,
    @Schema(
            description = "Preferred unit for displaying duration",
            example = "SECONDS",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Unit durationDisplayUnit,
    @Schema(
            description = "Target distance value (if applicable)",
            example = "400.0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @DecimalMin(value = "0.0", message = "{validation.value.positive}")
        Double distance,
    @Schema(
            description = "Unit for the prescribed distance",
            example = "METERS",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Unit distanceUnit,
    @Schema(
            description = "Target energy expenditure in calories",
            example = "20",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Min(value = 0, message = "{validation.value.positive}")
        Integer calories,

    // --- Instructions ---

    @Schema(
            description = "Specific notes for this movement in this WOD",
            example = "Touch and go",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.wod.notes.size}")
        String notes,
    @Schema(
            description = "Suggested scaling options displayed to the athlete",
            example = "Use PVC pipe",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 4000, message = "{validation.wod.scaling.size}")
        String scalingOptions) {}
