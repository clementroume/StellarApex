package apex.stellar.antares.dto;

import apex.stellar.antares.model.PlatformRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight summary of a User identity.
 *
 * <p>Used within {@link MembershipResponse} to identify the member without exposing private details
 * (like other gym memberships or UI preferences).
 */
public record UserSummary(
    @Schema(description = "User ID", example = "1") Long id,
    @Schema(description = "First Name", example = "John") String firstName,
    @Schema(description = "Last Name", example = "Doe") String lastName,
    @Schema(description = "Email address", example = "john.doe@example.com") String email,
    @Schema(description = "Global Role", example = "USER") PlatformRole platformRole) {}
