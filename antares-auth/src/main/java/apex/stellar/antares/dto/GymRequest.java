package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to create a box or program. Any user may send one; the gym starts {@code
 * PENDING_APPROVAL} until a platform admin approves it.
 *
 * @param name The unique name of the gym.
 * @param description An optional description.
 * @param isProgramming {@code true} for a virtual programming, {@code false} for a physical box.
 */
public record GymRequest(
    @Schema(description = "Name of the Gym", example = "CrossFit Stellar")
        @NotBlank(message = "{validation.gym.name.required}")
        @Size(max = 100, message = "{validation.gym.name.size}")
        String name,
    @Schema(description = "Short description of the facility", example = "The best box in town.")
        @Size(max = 1000, message = "{validation.gym.description.size}")
        String description,
    @Schema(description = "Whether this gym provides programming tracks", example = "true")
        @NotNull(message = "{validation.gym.isProgramming.required}")
        Boolean isProgramming) {}
