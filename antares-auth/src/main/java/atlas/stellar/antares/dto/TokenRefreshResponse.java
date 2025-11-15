package atlas.stellar.antares.dto;

/**
 * Data Transfer Object (DTO) for the response when a token is successfully refreshed. It contains
 * the new, short-lived access token.
 *
 * @param accessToken The new access token.
 */
public record TokenRefreshResponse(String accessToken) {}
