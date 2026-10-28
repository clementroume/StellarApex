package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to rename a gym or change its description (the type never changes).
 *
 * @param name The new unique name.
 * @param description The new description (null or blank clears it).
 */
public record GymUpdateRequest(
    @Schema(description = "Name of the Gym", example = "CrossFit Stellar")
        @NotBlank(message = "{validation.gym.name.required}")
        @Size(max = 100, message = "{validation.gym.name.size}")
        String name,
    @Schema(description = "Short description of the facility", example = "The best box in town.")
        @Size(max = 1000, message = "{validation.gym.description.size}")
        String description) {}
