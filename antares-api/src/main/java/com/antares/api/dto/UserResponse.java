package com.antares.api.dto;

import com.antares.api.model.Role;
import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for returning public user information.
 *
 * <p>This record provides a secure, public-facing representation of a User entity, intentionally
 * omitting sensitive data like the password hash. It includes all necessary information for the
 * client to build the user interface and manage state.
 *
 * @param id The user's unique identifier.
 * @param firstName The user's first name.
 * @param lastName The user's last name.
 * @param email The user's email address.
 * @param role The user's assigned role (e.g., ROLE_ATHLETE).
 * @param enabled Flag indicating if the user's account is active.
 * @param locale The user's preferred language (e.g., "en", "fr").
 * @param theme The user's preferred visual theme (e.g., "light", "dark").
 * @param createdAt The timestamp when the user was created.
 * @param updatedAt The timestamp of the last update to the user's record.
 */
public record UserResponse(
    Long id,
    String firstName,
    String lastName,
    String email,
    Role role,
    Boolean enabled,
    String locale,
    String theme,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
