package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request to transfer the holder role (OWNER or PROGRAMMER) of a gym.
 *
 * @param membershipId The membership of the new holder (an ACTIVE member of the same gym).
 */
public record TransferOwnershipRequest(
    @Schema(description = "Membership of the new owner/programmer", example = "50")
        @NotNull(message = "{validation.transfer.membershipId.required}")
        Long membershipId) {}
