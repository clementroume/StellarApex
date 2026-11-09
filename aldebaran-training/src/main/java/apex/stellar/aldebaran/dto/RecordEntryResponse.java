package apex.stellar.aldebaran.dto;

import java.time.LocalDate;

/**
 * One record of an athlete: the best load for a number of reps.
 *
 * @param reps The number of reps (N of the N RM).
 * @param weightKg The load, in kg.
 * @param previousWeightKg The best load before that day (the record it beat), or null.
 * @param date The day of the score that set it.
 * @param scoreId The score that set it.
 * @param wodId The WOD of that score.
 * @param wodTitle The title of that WOD.
 * @param inheritedFrom Where the record comes from when another variant proves it, or null.
 */
public record RecordEntryResponse(
    int reps,
    double weightKg,
    Double previousWeightKg,
    LocalDate date, Long scoreId, Long wodId, String wodTitle,
    RecordSourceResponse inheritedFrom) {}
