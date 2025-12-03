package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for handling a user's profile update request.
 *
 * <p>This record is used as the request body for the profile update endpoint. Validation messages
 * are sourced from {@code messages.properties}.
 *
 * @param firstName The user's updated first name.
 * @param lastName The user's updated last name.
 * @param email The user's updated email address.
 */
public record ProfileUpdateRequest(
    @Schema(description = "Updated first name", example = "Jane")
        @NotBlank(message = "{validation.firstName.required}")
        @Size(max = 50, message = "{validation.firstName.size}")
        String firstName,
    @Schema(description = "Updated last name", example = "Doe")
        @NotBlank(message = "{validation.lastName.required}")
        @Size(max = 50, message = "{validation.lastName.size}")
        String lastName,
    @Schema(description = "Updated email address", example = "jane.doe@stellar.apex")
        @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        @Size(max = 255, message = "{validation.email.size}")
        String email) {}
