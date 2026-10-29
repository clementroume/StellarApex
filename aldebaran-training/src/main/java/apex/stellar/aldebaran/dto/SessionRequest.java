package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for creating or replacing a session. Blocks are ordered as listed.
 *
 * @param type WOD session (a day) or specialty (a week).
 * @param discipline Discipline of the session (CROSSFIT for a WOD session).
 * @param date Day of a WOD session; any day of the week of a specialty (stored as its Monday).
 * @param gymId Gym of the session, or null for a personal session. Ignored on update.
 * @param visibilityPolicy Read rule of the content, or null for the gym's default.
 * @param contentVisibleAt Local date-time (gym's time zone) the content opens, for ADVANCE and
 *     DAY_OF.
 */
public record SessionRequest(
    @NotNull(message = "{validation.session.type.required}") SessionType type,
    @NotNull(message = "{validation.session.discipline.required}") Discipline discipline,
    @NotNull(message = "{validation.date.required}") LocalDate date,
    @Size(max = 100, message = "{validation.session.title.size}") String title,
    @Size(max = 4000, message = "{validation.wod.description.size}") String descriptionEn,
    @Size(max = 4000, message = "{validation.wod.description.size}") String descriptionFr,
    Long gymId,
    VisibilityPolicy visibilityPolicy,
    LocalDateTime contentVisibleAt,
    @NotEmpty(message = "{validation.session.blocks.empty}")
        @Size(max = 20, message = "{validation.session.blocks.size}")
        @Valid
        List<SessionBlockRequest> blocks) {}
