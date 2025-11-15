package atlas.stellar.antares.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for handling a user's password change request. Validation messages are
 * sourced from {@code messages.properties}.
 *
 * @param currentPassword The user's current password for verification.
 * @param newPassword The desired new password (min 8 characters).
 * @param confirmationPassword A confirmation of the new password.
 */
public record ChangePasswordRequest(
    @NotBlank(message = "{validation.currentPassword.required}") String currentPassword,
    @NotBlank(message = "{validation.newPassword.required}")
        @Size(min = 8, message = "{validation.password.size}")
        String newPassword,
    @NotBlank(message = "{validation.confirmationPassword.required}")
        String confirmationPassword) {}
