package apex.stellar.aldebaran.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO representing reference data used to organize and group movements.
 *
 * <p>Encapsulates mappings for categorizing related elements into groups.
 */
public record MovementReferenceData(
    // --- Movement Reference Date ---
    Map<String, List<String>> categoryGroups,
    Map<String, List<String>> equipmentGroups,
    Map<String, List<String>> techniqueGroups) {}
