package apex.stellar.aldebaran.dto;

import java.util.List;

/**
 * A movement for a chosen set of techniques and equipment: what the movement page shows.
 *
 * @param usualNameEn Most precise usual name of the variant, or null.
 * @param usualNameFr Its French name, null when identical or none.
 * @param descriptionEn Description of that usual name, else of the movement.
 * @param descriptionFr French description of that usual name, else of the movement.
 * @param videoUrl Video of that usual name, else of the movement.
 * @param implementCount Dumbbells or kettlebells used, or null.
 * @param variantKey Variant key, to read the matching records.
 * @param muscles Muscles and roles of the variant.
 */
public record MovementVariantResponse(
    String usualNameEn,
    String usualNameFr,
    String descriptionEn,
    String descriptionFr,
    String videoUrl,
    Integer implementCount,
    String variantKey,
    List<VariantMuscleResponse> muscles) {}
