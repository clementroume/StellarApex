package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;

/**
 * Lightweight DTO for listing movements without heavy details (anatomy, descriptions).
 *
 * <p>Used for autocomplete dropdowns and search results to optimize bandwidth.
 */
public record MovementSummaryResponse(
    Long id, String name, String nameAbbreviation, Category category, String imageUrl) {}
