package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A started support session.
 *
 * @param id The session ID.
 * @param actor The admin acting through the session.
 * @param target The user the admin acts as.
 * @param reason The audited reason.
 * @param startedAt Start instant.
 * @param expiresAt Expiry instant (the session is never refreshed).
 */
public record SupportSessionResponse(
    @Schema(description = "Support session ID", example = "7") Long id,
    @Schema(description = "Acting admin") SupportActor actor,
    @Schema(description = "User the admin acts as") UserSummary target,
    @Schema(description = "Audited reason") String reason,
    @Schema(description = "Start instant", example = "2026-10-25T10:00:00Z") Instant startedAt,
    @Schema(description = "Expiry instant", example = "2026-10-25T10:30:00Z") Instant expiresAt) {}
