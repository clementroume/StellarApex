package apex.stellar.aldebaran.dto;

import java.util.List;

/** DTO representing reference data for muscles, encompassing groups and roles. */
public record MuscleReferenceData(
    // --- Muscle Groups ---
    List<String> muscleGroups,
    // --- Muscle Roles ---
    List<String> muscleRoles) {}
