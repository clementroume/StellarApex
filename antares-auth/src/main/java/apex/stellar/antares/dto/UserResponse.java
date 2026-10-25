package apex.stellar.antares.dto;

import apex.stellar.antares.model.PlatformRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object (DTO) representing a public view of a User.
 *
 * @param id Unique identifier for the user.
 * @param firstName First name.
 * @param lastName Last name.
 * @param email Email address.
 * @param platformRole Global role (e.g., USER, ADMIN).
 * @param memberships List of gyms the user belongs to (Summary view).
 * @param locale User's preferred locale.
 * @param theme User's preferred theme.
 * @param createdAt Account creation timestamp.
 * @param supportSession The active support session when an admin acts as this user, else null.
 */
public record UserResponse(
    @Schema(description = "User ID", example = "1") Long id,
    @Schema(description = "First Name", example = "John") String firstName,
    @Schema(description = "Last Name", example = "Doe") String lastName,
    @Schema(description = "Email address", example = "john.doe@example.com") String email,
    @Schema(description = "Global System Role", example = "USER") PlatformRole platformRole,
    @Schema(description = "List of associated Gyms") List<MembershipSummary> memberships,
    @Schema(description = "Preferred language", example = "en") String locale,
    @Schema(description = "UI Theme preference", example = "dark") String theme,
    @Schema(description = "Account creation date", example = "2023-01-01T12:00:00")
        LocalDateTime createdAt,
    @Schema(description = "Active support session, null outside a support session", nullable = true)
        SupportSessionInfo supportSession) {

  /**
   * Returns a copy of this response describing the given support session.
   *
   * @param session the active support session.
   * @return the copy.
   */
  public UserResponse withSupportSession(SupportSessionInfo session) {
    return new UserResponse(
        id, firstName, lastName, email, platformRole, memberships, locale, theme, createdAt,
        session);
  }
}
