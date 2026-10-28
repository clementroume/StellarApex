package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request of a password-reset e-mail.
 *
 * @param email The account address.
 */
public record ForgotPasswordRequest(
    @Schema(description = "Account email address", example = "john.doe@stellar.apex")
        @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        @Size(max = 255, message = "{validation.email.size}")
        String email) {}
