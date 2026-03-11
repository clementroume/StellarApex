package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** DTO representing the full details of a WOD, including its ordered movements. */
public record WodResponse(
    // --- Identification ---
    Long id,
    String title,
    WodType wodType,
    ScoreType scoreType,

    // --- Authorship and Visibility ---
    Long authorId,
    Long gymId,
    boolean isPublic,

    // --- Description and Notes ---
    String description,
    String notes,

    // --- Structure ---
    Integer timeCapSeconds,
    Integer emomInterval,
    Integer emomRounds,
    String repScheme,
    Set<Modality> modalities,
    List<WodMovementResponse> movements,

    // --- Audit ---
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
