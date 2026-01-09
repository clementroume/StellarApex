package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * DTO representing the full details of a WOD, including its ordered movements.
 *
 * <p>This is the read-only view sent to the client. No validation annotations are needed as the
 * data comes from the trusted backend.
 */
public record WodResponse(
    @Schema(description = "Unique WOD ID", example = "101") Long id,
    @Schema(description = "Title of the workout", example = "Murph") String title,
    @Schema(description = "Structural type", example = "FOR_TIME") WodType wodType,
    @Schema(description = "Scoring metric", example = "TIME") ScoreType scoreType,
    @Schema(description = "ID of the author", example = "42") Long authorId,
    @Schema(description = "ID of the gym this WOD belongs to", example = "101") Long gymId,
    @Schema(description = "Visibility status") boolean isPublic,
    @Schema(description = "Full description content") String description,
    @Schema(description = "Coach notes") String notes,

    // --- Structure ---

    @Schema(description = "Time cap in seconds (if any)", example = "3600") Integer timeCapSeconds,
    @Schema(description = "EMOM interval in seconds", example = "60") Integer emomInterval,
    @Schema(description = "Number of rounds for EMOM", example = "10") Integer emomRounds,
    @Schema(description = "Summary string of the rep scheme", example = "21-15-9") String repScheme,
    @Schema(
            description = "Derived training modalities (tags)",
            example = "[\"GYMNASTICS\", \"CARDIO\"]")
        Set<Modality> modalities,
    @Schema(description = "Ordered list of movements to perform")
        List<WodMovementResponse> movements,

    // --- Audit ---

    @Schema(description = "Creation timestamp") LocalDateTime createdAt,
    @Schema(description = "Last update timestamp") LocalDateTime updatedAt) {}
