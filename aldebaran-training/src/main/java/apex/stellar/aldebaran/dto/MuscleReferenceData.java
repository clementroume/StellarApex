package apex.stellar.aldebaran.dto;

import java.util.List;

/**
 * DTO representing reference data for muscles, encompassing groups and roles.
 *
 * <p>Encapsulates data related to muscles, including categorization into various muscle groups and
 * functional roles.
 */
public record MuscleReferenceData(List<String> muscleGroups, List<String> muscleRoles) {}
