package apex.stellar.antares.dto;

import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/**
 * Data Transfer Object (DTO) representing the response for a membership entity.
 *
 * <p>This record encapsulates all the relevant information about a user's membership in a gym or
 * organization, including associated details about the user, their membership status, role, and
 * permissions. It is used for communication between the backend services and client applications.
 *
 * @param id The unique identifier of the membership.
 * @param user The user associated with the membership, represented as a `UserResponse`.
 * @param gymRole The role assigned to the user within the context of this membership (e.g.,
 *     OWNER, COACH).
 * @param status The current status of the membership, such as ACTIVE, PENDING, or BANNED.
 * @param permissions A set of permissions granted to the user for this membership, defining their
 *     allowed actions (e.g., MANAGE_MEMBERSHIPS, WOD_WRITE).
 */
public record MembershipResponse(
    @Schema(description = "Membership ID", example = "50") Long id,
    @Schema(description = "User associated with this membership") UserSummary user,
    @Schema(description = "Role within the gym", example = "ATHLETE") GymRole gymRole,
    @Schema(description = "Status of the membership", example = "ACTIVE") MembershipStatus status,
    @Schema(description = "Assigned permissions", example = "[\"MEMBER_READ\"]")
        Set<Permission> permissions) {}
