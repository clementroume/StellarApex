package com.antares.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for handling new user registration requests.
 *
 * <p>This record is used as the request body for the registration endpoint and includes validation
 * annotations to ensure data integrity.
 *
 * @param firstName The user's first name, which must not be blank.
 * @param lastName The user's last name, which must not be blank.
 * @param email The user's email address, which must be a valid format and not blank.
 * @param password The user's plain-text password, which must be at least 8 characters long.
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
