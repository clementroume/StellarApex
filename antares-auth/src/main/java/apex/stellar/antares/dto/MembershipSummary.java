package apex.stellar.antares.dto;

import apex.stellar.antares.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Summary of a user's membership to a gym.
 *
 * @param gymId The unique identifier of the gym.
 * @param role The role the user has in this gym.
 */
@Schema(description = "Represents a lightweight view of a user's access rights to a specific Gym.")
public record MembershipSummary(
    @Schema(description = "The unique identifier of the Gym (Tenant).", example = "101") Long gymId,
    @Schema(
            description = "The specific role assigned to the user within this Gym context ",
            example = "ROLE_COACH")
        Role role) {}
