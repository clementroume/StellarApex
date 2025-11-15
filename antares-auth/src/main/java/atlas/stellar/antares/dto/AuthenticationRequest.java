package atlas.stellar.antares.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object (DTO) for handling user authentication (login) requests.
 *
 * <p>This record is used as the request body for the login endpoint. It includes validation
 * annotations to ensure data is well-formed before processing. Validation messages are sourced from
 * {@code messages.properties}.
 *
 * @param email The user's email address.
 * @param password The user's plain-text password.
 */
public record AuthenticationRequest(
    @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        String email,
    @NotBlank(message = "{validation.password.required}") String password) {}
