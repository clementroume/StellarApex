package apex.stellar.aldebaran.dto;

import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/**
 * DTO of "Séance faite".
 *
 * @param date Day the session was done (today when null), within the session's day or week.
 * @param gymId For a personal session, one of the athlete's gyms (or null); ignored otherwise.
 */
public record SessionLogRequest(
    @PastOrPresent(message = "{validation.date.future}") LocalDate date, Long gymId) {}
