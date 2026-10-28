package apex.stellar.antares.dto;

import apex.stellar.antares.model.Division;
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
 * @param emailVerified Whether the address was confirmed through a mailed link.
 * @param platformRole Global role (e.g., USER, ADMIN).
 * @param memberships List of gyms the user belongs to (Summary view).
 * @param locale User's preferred locale.
 * @param theme User's preferred theme.
 * @param palette User's UI colour palette (ocean, chalk, volt, rig).
 * @param unitSystem Display unit for loads (METRIC, IMPERIAL), or null to follow the language.
 * @param publicLeaderboardName Whether the name is shown on public leaderboards.
 * @param division Competition division (MEN, WOMEN), or null when not chosen.
 * @param birthYear Birth year (private), or null.
 * @param heightCm Height in centimetres (private), or null.
 * @param createdAt Account creation timestamp.
 * @param supportSession The active support session when an admin acts as this user, else null.
 */
public record UserResponse(
    @Schema(description = "User ID", example = "1") Long id,
    @Schema(description = "First Name", example = "John") String firstName,
    @Schema(description = "Last Name", example = "Doe") String lastName,
    @Schema(description = "Email address", example = "john.doe@example.com") String email,
    @Schema(description = "Email address confirmed by a mailed link", example = "true")
        boolean emailVerified,
    @Schema(description = "Global System Role", example = "USER") PlatformRole platformRole,
    @Schema(description = "List of associated Gyms") List<MembershipSummary> memberships,
    @Schema(description = "Preferred language", example = "en") String locale,
    @Schema(description = "UI Theme preference", example = "dark") String theme,
    @Schema(description = "UI colour palette", example = "ocean") String palette,
    @Schema(
            description = "Display unit for loads, null follows the language",
            example = "METRIC",
            nullable = true)
        String unitSystem,
    @Schema(description = "Name shown on public leaderboards", example = "false")
        boolean publicLeaderboardName,
    @Schema(description = "Competition division, null when not chosen", nullable = true)
        Division division,
    @Schema(description = "Birth year (private)", example = "1990", nullable = true)
        Integer birthYear,
    @Schema(description = "Height in centimetres (private)", example = "178", nullable = true)
        Integer heightCm,
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
        id, firstName, lastName, email, emailVerified, platformRole, memberships, locale, theme, palette, unitSystem,
        publicLeaderboardName, division, birthYear, heightCm, createdAt, session);
  }
}
