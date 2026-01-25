package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object (DTO) for handling gym-related requests.
 *
 * <p>This record is designed for endpoints that require gym information, such as creating or
 * updating gym details. It includes validation annotations to ensure that the provided data meets
 * the specified requirements.
 *
 * @param name The name of the gym. This field is mandatory and cannot be blank.
 * @param description An optional description of the gym, providing additional context or details.
 * @param isProgramming Indicates whether the gym is associated with programming-related activities.
 *     This field is mandatory and cannot be null.
 * @param creationToken An anti-spam token used to validate the origin and legitimacy of the
 *     request. This field is mandatory and cannot be blank.
 */
public record GymRequest(
    @Schema(description = "Name of the Gym", example = "CrossFit Stellar")
        @NotBlank(message = "{validation.gym.name.required}")
        String name,
    @Schema(description = "Short description of the facility", example = "The best box in town.")
        String description,
    @Schema(description = "Whether this gym provides programming tracks", example = "true")
        @NotNull(message = "{validation.gym.isProgramming.required}")
        Boolean isProgramming,
    @Schema(description = "Anti-spam token required for creation", example = "ANT-1234-SECURE")
        @NotBlank(message = "{validation.gym.creationToken.required}")
        String creationToken) {}
