package apex.stellar.antares.dto;

import apex.stellar.antares.model.Gym.GymStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for returning gym-related information.
 *
 * <p>This record provides a comprehensive representation of a gym entity, including its metadata
 * and current status. It is typically used in responses for endpoints that retrieve gym details.
 *
 * @param id The unique identifier of the gym.
 * @param name The name of the gym.
 * @param description An optional description of the gym, providing context or additional details.
 * @param isProgramming Indicates whether the gym involves programming-related activities.
 * @param isAutoSubscription Indicates whether the gym supports automatic user subscription.
 * @param status The current status of the gym (e.g., PENDING_APPROVAL, ACTIVE, REJECTED,
 *     SUSPENDED).
 * @param createdAt The timestamp when the gym record was created.
 */
public record GymResponse(
    @Schema(description = "Unique identifier of the Gym", example = "1")
    Long id,
    @Schema(description = "Name of the Gym", example = "CrossFit Stellar")
    String name,
    @Schema(description = "Description", example = "Elite training facility")
    String description,
    @Schema(description = "Whether this gym provides programming tracks", example = "true")
    boolean isProgramming,
    @Schema(description = "Whether this gym allows auto subscription", example = "false")
    boolean isAutoSubscription,
    @Schema(description = "Current status of the Gym", example = "ACTIVE")
    GymStatus status,
    @Schema(description = "Creation timestamp", example = "2023-10-01T10:00:00")
    LocalDateTime createdAt) {}
