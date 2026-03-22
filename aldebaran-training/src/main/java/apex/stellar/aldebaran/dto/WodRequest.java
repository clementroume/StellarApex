package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** DTO for creating or updating a WOD (Workout of the Day). */
public record WodRequest(

    // --- Identification ---
    @NotBlank(message = "{validation.wod.title.required}")
        @Size(max = 100, message = "{validation.wod.title.size}")
        String title,
    @NotNull(message = "{validation.wod.type.required}") WodType wodType,
    @NotNull(message = "{validation.wod.scoreType.required}") ScoreType scoreType,

    // --- Authorship and Visibility ---
    Long authorId,
    Long gymId,
    boolean isPublic,

    // --- Description and Notes ---
    @Size(max = 4000, message = "{validation.wod.description.size}") String description,
    @Size(max = 4000, message = "{validation.wod.notes.size}") String notes,

    // --- Structure and Prescription ---
    @Min(value = 0, message = "{validation.wod.timeCap.min}") Integer timeCapSeconds,
    @Min(value = 0, message = "{validation.wod.emom.min}") Integer emomInterval,
    @Min(value = 0, message = "{validation.wod.emom.min}") Integer emomRounds,
    @Size(max = 100, message = "{validation.wod.repScheme.size}") String repScheme,
    @NotEmpty(message = "{validation.wod.movements.empty}") @Valid
        List<WodMovementRequest> movements) {}
