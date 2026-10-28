package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What any signed-in user may see of an ACTIVE gym when looking for one to join: never its
 * enrollment code nor its approval settings.
 *
 * @param id The gym ID.
 * @param name The gym name.
 * @param description The description, if any.
 * @param isProgramming {@code true} for a virtual programming, {@code false} for a physical box.
 */
@Schema(description = "Public view of an active box or program, for discovery.")
public record GymPublicResponse(
    @Schema(description = "Gym ID", example = "1") Long id,
    @Schema(description = "Name of the Gym", example = "CrossFit Stellar") String name,
    @Schema(description = "Description", example = "Elite training facility") String description,
    @Schema(description = "Virtual programming (not a physical box)", example = "false")
        boolean isProgramming) {}
