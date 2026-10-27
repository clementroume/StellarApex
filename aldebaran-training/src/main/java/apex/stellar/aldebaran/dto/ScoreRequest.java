package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.validation.ValidScoreRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** DTO for logging a new workout performance. */
@ValidScoreRequest
public record ScoreRequest(

    // --- Identification ---
    Long userId,
    @NotNull(message = "{validation.date.required}")
        @PastOrPresent(message = "{validation.date.future}")
        LocalDate date,
    @NotNull(message = "{validation.wodId.required}") Long wodId,

    // --- Time Metrics ---
    @Min(value = 0, message = "{validation.value.positive}") Integer timeMinutes,
    @Min(value = 0, message = "{validation.value.positive}") Integer timeSeconds,

    // --- Volume Metrics ---
    @Min(value = 0, message = "{validation.value.positive}") Integer rounds,
    @Min(value = 0, message = "{validation.value.positive}") Integer reps,

    // --- Load Metrics ---
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double maxWeight,
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double totalLoad,
    Unit weightUnit,

    // --- Distance Metrics ---
    @DecimalMin(value = "0.0", message = "{validation.value.positive}") Double totalDistance,
    Unit distanceUnit,
    // --- Calories ---
    @Min(value = 0, message = "{validation.value.positive}") Integer totalCalories,

    // --- Performance Context ---
    @NotNull(message = "{validation.scaling.required}") ScalingLevel scaling,
    boolean timeCapped,
    // Optional official tiebreak time (elapsed seconds); ignored when the WOD has no tiebreak
    @Min(value = 1, message = "{validation.value.positive}") Integer tiebreakSeconds,

    // --- Comments and Scaling notes---
    @Size(max = 4000, message = "{validation.score.comment.size}") String userComment,
    @Size(max = 4000, message = "{validation.score.scalingNotes.size}") String scalingNotes,

    // --- Gym where it was performed (public or personal WOD only; a gym WOD uses its gym) ---
    Long gymId) {}
