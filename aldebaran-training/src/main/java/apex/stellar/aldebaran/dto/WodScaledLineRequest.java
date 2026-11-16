package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.model.enums.UnitField;
import apex.stellar.aldebaran.validation.UnitOf;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * A line in the WOD's official Scaled version (D141, D154): the variant and the quantities in that
 * version, a value left null being the RX one; units of origin, like the RX line.
 */
public record WodScaledLineRequest(
    @NotNull(message = "{validation.movement.id.required}") Long movementId,
    Set<String> equipment,
    Set<String> techniques,
    @Size(max = 50, message = "{validation.wod.repsScheme.size}") String repsScheme,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weight,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weightFemale,
    @UnitOf(UnitField.MASS) Unit weightUnit,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double distance,
    @UnitOf(UnitField.DISTANCE) Unit distanceUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer calories,
    @Min(value = 0, message = "{validation.value.positive}") Integer caloriesFemale,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double height,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double heightFemale,
    @UnitOf(UnitField.HEIGHT) Unit heightUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer durationSeconds) {}
