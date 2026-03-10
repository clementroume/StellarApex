package apex.stellar.aldebaran.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Represents reference data for muscles, encompassing groups and roles.
 *
 * <p>This record is intended to encapsulate data related to muscles, including categorization into
 * various muscle groups and functional roles. It can be used as a foundational structure in
 * applications where muscle-related metadata or classification is required.
 */
public record MuscleReferenceData(
    @Schema(description = "Classification of muscles by their anatomical or functional grouping ")
        List<String> muscleGroups,
    @Schema(description = "The muscle roles defining the function or action of the muscle")
        List<String> muscleRoles) {}
