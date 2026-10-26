package apex.stellar.antares.dto;

import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Lightweight summary of a user's membership to a Gym.
 *
 * <p>Designed for the "Context Switcher" in the UI. It provides just enough information to display
 * a list of available gyms (Name + ID) and the user's standing in them, without fetching full
 * details.
 *
 * @param gymId The unique identifier of the Gym.
 * @param gymName The name of the Gym (for display purposes).
 * @param gymStatus The status of the Gym itself (e.g., ACTIVE).
 * @param gymRole The role the user holds in this context.
 * @param status The user's membership status (e.g., ACTIVE, PENDING).
 * @param permissions The effective permissions in this gym (all for OWNER/PROGRAMMER, the assigned
 *     ones for COACH, none for ATHLETE).
 */
@Schema(description = "Lightweight view of a membership for UI lists and menus.")
public record MembershipSummary(
    @Schema(description = "Gym ID", example = "101") Long gymId,
    @Schema(description = "Gym Name", example = "Spartacus CrossFit") String gymName,
    @Schema(description = "Gym Status", example = "ACTIVE") GymStatus gymStatus,
    @Schema(description = "User's Role in this Gym", example = "COACH") GymRole gymRole,
    @Schema(description = "User's Membership Status", example = "ACTIVE")
        MembershipStatus status,
    @Schema(description = "Effective permissions in this Gym", example = "[\"WOD_WRITE\"]")
        List<Permission> permissions) {}
