package apex.stellar.aldebaran.dto;

import java.util.List;
import java.util.Map;

/** DTO containing structural reference data for WOD creation forms. */
public record WodReferenceData(
    List<String> wodTypes, List<String> scoreTypes, Map<String, List<String>> unitGroups) {}
