package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Session settings of a gym (read and written as is).
 *
 * @param defaultVisibility Visibility policy of new sessions.
 * @param timeZone IANA time zone of the gym (e.g. Europe/Paris).
 */
public record GymSessionSettingsDto(
    @NotNull(message = "{validation.session.visibility.required}")
        VisibilityPolicy defaultVisibility,
    @NotBlank(message = "{validation.session.timezone.required}")
        @Size(max = 50, message = "{validation.session.timezone.required}")
        String timeZone) {}
