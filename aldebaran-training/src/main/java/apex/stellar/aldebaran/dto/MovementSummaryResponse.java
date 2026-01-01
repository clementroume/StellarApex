package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight DTO for listing movements without heavy details (anatomy, descriptions).
 *
 * <p>Used for autocomplete dropdowns and search results to optimize bandwidth.
 */
public record MovementSummaryResponse(
    @Schema(description = "Business ID", example = "WL-SQ-001") String id,
    @Schema(description = "Display name", example = "Back Squat") String name,
    @Schema(description = "Short abbreviation", example = "BS") String nameAbbreviation,
    @Schema(description = "Functional category", example = "DEADLIFT") Category category,
    @Schema(description = "Thumbnail URL") String imageUrl) {}
