package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The support session behind the current request, as shown in {@link UserResponse}.
 *
 * @param id The session ID.
 * @param actor The admin acting through the session.
 * @param reason The audited reason.
 * @param expiresAt Expiry instant.
 */
@Schema(description = "Active support session (an admin is acting as this user)")
public record SupportSessionInfo(
    @Schema(description = "Support session ID", example = "7") Long id,
    @Schema(description = "Acting admin") SupportActor actor,
    @Schema(description = "Audited reason") String reason,
    @Schema(description = "Expiry instant", example = "2026-10-25T10:30:00Z") Instant expiresAt) {}
