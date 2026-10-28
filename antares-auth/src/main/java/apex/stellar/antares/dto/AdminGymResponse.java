package apex.stellar.antares.dto;

import apex.stellar.antares.model.Gym.GymStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * A gym as platform admins review it (approval queue, suspension), with the user who holds it.
 *
 * @param id The gym ID.
 * @param name The gym name.
 * @param description The description, if any.
 * @param isProgramming {@code true} for a virtual programming.
 * @param status The approval status.
 * @param createdAt When the gym was requested.
 * @param holder The OWNER/PROGRAMMER (the requester until a transfer), or null if none.
 * @param memberCount The number of ACTIVE members.
 */
public record AdminGymResponse(
    @Schema(description = "Gym ID", example = "1") Long id,
    @Schema(description = "Name of the Gym", example = "CrossFit Stellar") String name,
    @Schema(description = "Description", example = "Elite training facility") String description,
    @Schema(description = "Virtual programming (not a physical box)", example = "false")
        boolean isProgramming,
    @Schema(description = "Approval status of the Gym", example = "PENDING_APPROVAL")
        GymStatus status,
    @Schema(description = "Creation timestamp", example = "2026-10-01T10:00:00")
        LocalDateTime createdAt,
    @Schema(description = "Owner or programmer of the Gym") UserSummary holder,
    @Schema(description = "Number of active members", example = "42") long memberCount) {}
