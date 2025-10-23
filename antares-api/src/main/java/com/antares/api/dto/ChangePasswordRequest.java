package com.antares.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for handling a user's password change request.
 *
 * @param currentPassword The user's current password for verification.
 * @param newPassword The desired new password.
 * @param confirmationPassword A confirmation of the new password to prevent typos.
 */
public record ChangePasswordRequest(
    @NotBlank(message = "{validation.currentPassword.required}") String currentPassword,
    @NotBlank(message = "{validation.newPassword.required}")
        @Size(min = 8, message = "{validation.password.size}")
        String newPassword,
    @NotBlank(message = "{validation.confirmationPassword.required}")
        String confirmationPassword) {}
