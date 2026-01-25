package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object (DTO) for submitting requests to join a gym.
 *
 * <p>This record is used as the request body for endpoints allowing users to enroll in a gym. It
 * includes validation constraints to ensure the data integrity and validity of the request.
 *
 * @param gymId The unique identifier of the gym the user intends to join. This field is mandatory
 *     and must not be null.
 * @param enrollmentCode The code required to enroll in the gym. This field is mandatory and must
 *     not be blank.
 */
public record JoinGymRequest(
    @Schema(description = "The unique identifier of the gym to join", example = "1")
        @NotNull(message = "{validation.join.gymId.required}")
        Long gymId,
    @Schema(description = "Enrollment code provided by the Gym Owner", example = "JOIN-9988")
        @NotBlank(message = "{validation.join.enrollmentCode.required}")
        String enrollmentCode) {}
