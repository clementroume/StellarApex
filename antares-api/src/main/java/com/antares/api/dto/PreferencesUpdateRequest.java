package com.antares.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Data Transfer Object (DTO) for handling a user's preferences update request.
 *
 * <p>This record is used as the request body for the preferences update endpoint. It includes
 * validation annotations to ensure that the submitted locale and theme match the expected formats.
 *
 * @param locale The user's preferred locale, which must match a simple format like "en" or "en-US".
 * @param theme The user's preferred theme, which must be either "light" or "dark".
 */
public record PreferencesUpdateRequest(
    @NotBlank(message = "{validation.locale.required}")
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "{validation.locale.pattern}")
        String locale,
    @NotBlank(message = "{validation.theme.required}")
        @Pattern(regexp = "^(light|dark)$", message = "{validation.theme.pattern}")
        String theme) {}
