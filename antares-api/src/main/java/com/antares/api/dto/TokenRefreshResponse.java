package com.antares.api.dto;

/**
 * DTO for the response when a token is successfully refreshed. It contains the new access token.
 *
 * @param accessToken The new, short-lived access token.
 */
public record TokenRefreshResponse(String accessToken) {}
