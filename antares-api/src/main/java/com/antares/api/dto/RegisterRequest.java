package com.antares.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for handling new user registration requests.
 *
 * <p>This record is used as the request body for the registration endpoint and includes validation
 * annotations. Validation messages are sourced from {@code messages.properties}.
 *
 * @param firstName The user's first name.
 * @param lastName The user's last name.
 * @param email The user's email address.
 * @param password The user's plain-text password (min 8 characters).
 */
public record RegisterRequest(
    @NotBlank(message = "{validation.firstName.required}")
        @Size(max = 50, message = "{validation.firstName.size}")
        String firstName,
    @NotBlank(message = "{validation.lastName.required}")
        @Size(max = 50, message = "{validation.lastName.size}")
        String lastName,
    @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        @Size(max = 255, message = "{validation.email.size}")
        String email,
    @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, message = "{validation.password.size}")
        String password) {}
