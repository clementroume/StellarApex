package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * DTO for changing the visibility of a session (allowed even once the session is locked).
 *
 * @param visibilityPolicy Read rule of the content.
 * @param contentVisibleAt Local date-time (gym's time zone) the content opens, for ADVANCE and
 *     DAY_OF.
 */
public record SessionVisibilityRequest(
    @NotNull(message = "{validation.session.visibility.required}")
        VisibilityPolicy visibilityPolicy,
    LocalDateTime contentVisibleAt) {}
