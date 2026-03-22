package apex.stellar.aldebaran.dto;

import java.util.List;
import java.util.Map;

/** DTO containing structural reference data for WOD creation forms. */
public record WodReferenceData(
    // --- Wod Types ---
    List<String> wodTypes,
    // --- Score Types ---
    List<String> scoreTypes,
    // --- Units ---
    Map<String, List<String>> unitGroups) {}
