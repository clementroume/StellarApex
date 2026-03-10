package apex.stellar.aldebaran.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Represents reference data used to organize and group movements for a workout program. This record
 * encapsulates mappings for categorizing related elements into groups.
 */
public record MovementReferenceData(
    @Schema(description = "Defines the grouping of movements by higher-level workout categories.")
        Map<String, List<String>> categoryGroups,
    @Schema(description = "Defines the grouping of movements based on the equipment required.")
        Map<String, List<String>> equipmentGroups,
    @Schema(description = " Defines the grouping of movements by specific techniques.")
        Map<String, List<String>> techniqueGroups) {}
