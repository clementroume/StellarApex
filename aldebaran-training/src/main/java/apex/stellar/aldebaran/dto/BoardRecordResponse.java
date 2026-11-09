package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.AgeGroup;
import apex.stellar.aldebaran.model.enums.Division;
import java.time.LocalDate;

/**
 * One line of a box records board (D56): a member's base-version record. Names come from Antares
 * ({@code POST /antares/users/display-names}).
 *
 * @param userId The member.
 * @param reps N of the N RM.
 * @param weightKg The load, in kg.
 * @param date The day it was set.
 * @param division The division of the score that set it, or null.
 * @param ageGroup The age group of the score that set it, or null.
 * @param inheritedFrom Where the record comes from when another variant proves it, or null.
 */
public record BoardRecordResponse(
    Long userId,
    int reps,
    double weightKg,
    LocalDate date,
    Division division,
    AgeGroup ageGroup,
    RecordSourceResponse inheritedFrom) {}
