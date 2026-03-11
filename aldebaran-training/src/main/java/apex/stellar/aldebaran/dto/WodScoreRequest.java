package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.validation.ValidScoreRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO for logging a new workout performance.
 *
 * <p>Adapts time input to allow users to enter data naturally (e.g., "1 min 30" or "90 sec"). The
 * API will infer the preferred display format based on which fields are populated.
 */
@ValidScoreRequest
public record WodScoreRequest(
    Long userId,
    @NotNull(message = "{validation.wodId.required}") Long wodId,
    @NotNull(message = "{validation.date.required}")
        @PastOrPresent(message = "{validation.date.future}")
        LocalDate date,

    // --- Metrics ---
    @Min(value = 0, message = "{validation.value.positive}") Integer timeMinutes,
    @Min(value = 0, message = "{validation.value.positive}") Integer timeSeconds,
    @Min(value = 0, message = "{validation.value.positive}") Integer rounds,
    @Min(value = 0, message = "{validation.value.positive}") Integer reps,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double maxWeight,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double totalLoad,
    Unit weightUnit,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double totalDistance,
    Unit distanceUnit,
    @Min(value = 0, message = "{validation.value.positive}") Integer totalCalories,

    // --- Metadata ---
    @NotNull(message = "{validation.scaling.required}") ScalingLevel scaling,
    boolean timeCapped,
    @Size(max = 4000, message = "{validation.score.scalingNotes.size}") String scalingNotes,
    @Size(max = 4000, message = "{validation.score.comment.size}") String userComment) {}
