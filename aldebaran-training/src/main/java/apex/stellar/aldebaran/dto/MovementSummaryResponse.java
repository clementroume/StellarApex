package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight DTO for listing movements without heavy details (anatomy, descriptions).
 *
 * <p>Used for autocomplete dropdowns and search results to optimize bandwidth.
 */
public record MovementSummaryResponse(
    @Schema(description = "Movement ID", example = "1") Long id,
    @Schema(description = "Display name", example = "Back Squat") String name,
    @Schema(description = "Short abbreviation", example = "BS") String nameAbbreviation,
    @Schema(description = "Functional category", example = "DEADLIFT") Category category,
    @Schema(description = "Thumbnail URL") String imageUrl) {}
