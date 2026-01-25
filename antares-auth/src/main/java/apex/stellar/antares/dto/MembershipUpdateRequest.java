package apex.stellar.antares.dto;

import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Data Transfer Object (DTO) for updating membership details.
 *
 * <p>This record is designed for requests that modify the membership status, role, or permissions
 * of a user within a gym (tenant) context. It provides validation annotations to ensure data
 * integrity and consistency.
 *
 * @param status The updated membership status of the user. This field is mandatory and cannot be
 *     null. The value must correspond to one of the valid statuses defined in {@link
 *     Membership.MembershipStatus}.
 * @param gymRole The updated role assigned to the user. This field is mandatory and cannot be null.
 *     The role must align with one of the enumerated values in {@link GymRole}.
 * @param permissions The updated set of permissions associated with the user. This field is
 *     mandatory and cannot be null. The set may include permissions defined in {@link Permission}.
 */
public record MembershipUpdateRequest(
    @Schema(description = "Status of the membership", example = "ACTIVE")
        @NotNull(message = "{validation.membership.status.required}")
        Membership.MembershipStatus status,
    @Schema(description = "New role to assign to the member", example = "COACH")
        @NotNull(message = "{validation.membership.role.required}")
        GymRole gymRole,
    @Schema(
            description = "Set of permissions to grant",
            example = "[\"WOD_WRITE\", \"SCORE_VERIFY\"]")
        @NotNull(message = "{validation.membership.permissions.required}")
        Set<Permission> permissions) {}
