package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "Current password for verification", example = "OldPass123!")
        @NotBlank(message = "{validation.currentPassword.required}")
        String currentPassword,
    @Schema(description = "New desired password", example = "NewStrongPass456!")
        @NotBlank(message = "{validation.newPassword.required}")
        @Size(min = 8, message = "{validation.password.size}")
        String newPassword,
    @Schema(description = "Confirmation of the new password", example = "NewStrongPass456!")
        @NotBlank(message = "{validation.confirmationPassword.required}")
        String confirmationPassword) {}
