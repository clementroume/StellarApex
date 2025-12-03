package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object (DTO) for the response when a token is successfully refreshed. It contains
 * the new, short-lived access token.
 *
 * @param accessToken The new access token.
 */
public record TokenRefreshResponse(
    @Schema(description = "New short-lived JWT Access Token") String accessToken) {}
