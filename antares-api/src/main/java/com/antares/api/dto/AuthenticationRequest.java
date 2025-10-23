package com.antares.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object (DTO) for handling user authentication requests.
 *
 * <p>This record is used as the request body for the login endpoint. It includes validation
 * annotations to ensure the data is well-formed before it reaches the service layer.
 *
 * @param email The user's email address, which must be a valid format.
 * @param password The user's plain-text password, which must not be empty.
 */
public record AuthenticationRequest(
    @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        String email,
    @NotBlank(message = "{validation.password.required}") String password) {}
