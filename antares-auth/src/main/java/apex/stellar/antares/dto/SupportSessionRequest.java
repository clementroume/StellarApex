package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to start a support session as another user.
 *
 * @param userId The user to act as (must not be a platform ADMIN).
 * @param reason Why the session is needed, kept in the audit trail.
 */
public record SupportSessionRequest(
    @Schema(description = "User to act as", example = "42")
        @NotNull(message = "{validation.support.userId.required}")
        Long userId,
    @Schema(description = "Reason, kept for audit", example = "Ticket #123: WOD not visible")
        @NotBlank(message = "{validation.support.reason.required}")
        @Size(max = 255, message = "{validation.support.reason.size}")
        String reason) {}
