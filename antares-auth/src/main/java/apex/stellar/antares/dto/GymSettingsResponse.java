package apex.stellar.antares.dto;

import apex.stellar.antares.model.Gym.GymStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything gym staff with {@code MANAGE_SETTINGS} can see and edit about their gym.
 *
 * @param id The gym ID.
 * @param name The gym name.
 * @param description The description, if any.
 * @param isProgramming {@code true} for a virtual programming.
 * @param status The approval status of the gym.
 * @param enrollmentCode The code members enter (or receive in an invitation link) to join.
 * @param isAutoSubscription {@code true} if new members are ACTIVE at once (no validation).
 */
public record GymSettingsResponse(
    @Schema(description = "Gym ID", example = "1") Long id,
    @Schema(description = "Name of the Gym", example = "CrossFit Stellar") String name,
    @Schema(description = "Description", example = "Elite training facility") String description,
    @Schema(description = "Virtual programming (not a physical box)", example = "false")
        boolean isProgramming,
    @Schema(description = "Approval status of the Gym", example = "ACTIVE") GymStatus status,
    @Schema(description = "Code required to join the gym", example = "JOIN-9988")
        String enrollmentCode,
    @Schema(description = "Automatically approve new members", example = "false")
        boolean isAutoSubscription) {}
