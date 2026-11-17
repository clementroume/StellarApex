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
 * A line of the WOD done otherwise than prescribed (D142): the variant done and the quantities that
 * changed (null = as prescribed). The load is in the score's weight unit. On a "max" line of a WOD
 * with several, {@code repsScheme} holds the reps counted (D147).
 */
public record ScoreLineRequest(
    @NotNull(message = "{validation.score.line.required}") Long wodMovementId,
    @NotNull(message = "{validation.movement.id.required}") Long movementId,
    Set<String> equipment,
    Set<String> techniques,
    @Size(max = 50, message = "{validation.wod.repsScheme.size}") String repsScheme,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double weight,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double distance,
    @UnitOf(UnitField.DISTANCE) Unit distanceUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer calories,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double height,
    @UnitOf(UnitField.HEIGHT) Unit heightUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer durationSeconds) {}
