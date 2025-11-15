package atlas.stellar.antares.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Data Transfer Object (DTO) for handling a user's preference update request.
 *
 * <p>This record is used as the request body for the preference update endpoint. Validation
 * messages are sourced from {@code messages.properties}.
 *
 * @param locale The user's preferred locale (e.g., "en" or "en-US").
 * @param theme The user's preferred theme ("light" or "dark").
 */
public record PreferencesUpdateRequest(
    @NotBlank(message = "{validation.locale.required}")
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "{validation.locale.pattern}")
        String locale,
    @NotBlank(message = "{validation.theme.required}")
        @Pattern(regexp = "^(light|dark)$", message = "{validation.theme.pattern}")
        String theme) {}
