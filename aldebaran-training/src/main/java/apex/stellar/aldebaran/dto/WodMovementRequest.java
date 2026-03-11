package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.validation.constraints.DecimalMin;
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
    Unit weightUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer durationSeconds,
    Unit durationDisplayUnit,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double distance,
    Unit distanceUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer calories,

    // --- Characteristics ---
    Set<String> equipment,
    Set<String> techniques,

    // --- Instructions ---
    @Size(max = 4000, message = "{validation.wod.notes.size}") String notes,
    @Size(max = 4000, message = "{validation.wod.scaling.size}") String scalingOptions) {}
