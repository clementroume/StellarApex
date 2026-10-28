package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmation of an e-mail address through a mailed link.
 *
 * @param token The single-use token from the link.
 */
public record VerifyEmailRequest(
    @Schema(description = "Token from the verification link")
        @NotBlank(message = "{validation.token.required}")
        @Size(max = 100, message = "{validation.token.required}")
        String token) {

  /** Masks the token in logs. */
  @Override
  public String toString() {
    return "VerifyEmailRequest[***]";
  }
}
