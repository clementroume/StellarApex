package apex.stellar.aldebaran.dto;

import java.time.LocalDate;

/**
 * One step of a record's progression: the day a new best load was set.
 *
 * @param date The day.
 * @param weightKg The new best load, in kg.
 */
public record RecordPointResponse(LocalDate date, double weightKg) {}
