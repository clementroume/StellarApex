package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object (DTO) for submitting gym settings configuration requests.
 *
 * <p>This record serves as the request body for endpoints that update or configure gym-specific
 * settings, such as enrollment and subscription preferences. Validation annotations are included to
 * enforce the required structure of the input data.
 *
 * @param enrollmentCode The code required to configure or verify enrollment for the gym. This field
 *     is mandatory and must not be blank.
 * @param isAutoSubscription Indicates whether the gym allows automatic subscription for users. This
 *     field is mandatory and must not be null.
 */
public record GymSettingsRequest(
    @Schema(description = "Code required to join the gym", example = "JOIN-9988")
        @NotBlank(message = "{validation.gym.enrollmentCode.required}")
        String enrollmentCode,
    @Schema(description = "Automatically approve new members", example = "false")
        @NotNull(message = "{validation.gym.isAutoSubscription.required}")
        Boolean isAutoSubscription) {}
