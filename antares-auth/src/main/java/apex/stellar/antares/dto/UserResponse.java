package apex.stellar.antares.dto;

import apex.stellar.antares.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object (DTO) for returning public user information.
 *
 * <p>This record provides a secure, public-facing representation of a User entity, intentionally
 * omitting sensitive data like the password hash. It includes all information necessary for the
 * client application to build its state.
 *
 * @param id The user's unique identifier.
 * @param firstName The user's first name.
 * @param lastName The user's last name.
 * @param email The user's email address.
 * @param role The user's assigned role (e.g., ROLE_USER, ROLE_ADMIN).
 * @param enabled Flag indicating if the user's account is active.
 * @param locale The user's preferred language (e.g., "en", "fr").
 * @param theme The user's preferred visual theme (e.g., "light", "dark").
 * @param memberships List of gym memberships associated with the user.
 * @param createdAt The timestamp when the user was created.
 * @param updatedAt The timestamp of the last update to the user's record.
 */
public record UserResponse(
    @Schema(description = "The user's unique system identifier.", example = "1") Long id,
    @Schema(description = "The user's first name.", example = "John") String firstName,
    @Schema(description = "The user's last name.", example = "Doe") String lastName,
    @Schema(description = "The user's login email address.", example = "john.doe@stellar.apex")
        String email,
    @Schema(description = "The user's global system role", example = "ROLE_USER") Role role,
    @Schema(description = "Indicates if the account is currently active.", example = "true")
        Boolean enabled,
    @Schema(description = "Preferred language code (ISO 639-1).", example = "en") String locale,
    @Schema(description = "Preferred UI theme.", example = "light") String theme,
    @Schema(description = "List of all gyms where the user has an active membership.")
        List<MembershipSummary> memberships,
    @Schema(description = "Account creation timestamp.") LocalDateTime createdAt,
    @Schema(description = "Last profile update timestamp.") LocalDateTime updatedAt) {}
