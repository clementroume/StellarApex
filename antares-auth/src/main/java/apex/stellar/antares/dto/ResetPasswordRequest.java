package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * New password chosen through a mailed reset link.
 *
 * @param token The single-use token from the link.
 * @param newPassword The new password (min 8 characters).
 * @param confirmationPassword The same password again.
 */
public record ResetPasswordRequest(
    @Schema(description = "Token from the reset link")
        @NotBlank(message = "{validation.token.required}")
        @Size(max = 100, message = "{validation.token.required}")
        String token,
    @Schema(description = "New password", example = "NewStrongPass456!")
        @NotBlank(message = "{validation.newPassword.required}")
        @Size(min = 8, max = 128, message = "{validation.password.size}")
        String newPassword,
    @Schema(description = "Confirmation of the new password", example = "NewStrongPass456!")
        @NotBlank(message = "{validation.confirmationPassword.required}")
        String confirmationPassword) {

  /** Masks secrets in logs. */
  @Override
  public String toString() {
    return "ResetPasswordRequest[***]";
  }
}
