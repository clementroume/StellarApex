package apex.stellar.antares.dto;

import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * A membership as gym staff see it in their member list.
 *
 * @param id The unique identifier of the membership.
 * @param user The member.
 * @param gymRole The role in the gym (OWNER, PROGRAMMER, COACH, ATHLETE).
 * @param status The membership status (PENDING, ACTIVE, INACTIVE, BANNED).
 * @param permissions The permissions assigned to the member (meaningful for a COACH only).
 * @param createdAt When the member first asked to join.
 * @param capabilities What the current user may do with this membership.
 */
public record MembershipResponse(
    @Schema(description = "Membership ID", example = "50") Long id,
    @Schema(description = "User associated with this membership") UserSummary user,
    @Schema(description = "Role within the gym", example = "ATHLETE") GymRole gymRole,
    @Schema(description = "Status of the membership", example = "ACTIVE") MembershipStatus status,
    @Schema(description = "Assigned permissions", example = "[\"WOD_WRITE\", \"SCORE_VERIFY\"]")
        Set<Permission> permissions,
    @Schema(description = "First request date", example = "2026-10-01T10:00:00")
        LocalDateTime createdAt,
    @Schema(description = "Actions the current user may perform on this membership")
        MembershipCapabilities capabilities) {}
