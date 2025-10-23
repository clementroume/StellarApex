package com.antares.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JwtProperties holds configuration properties for JWT tokens and cookies.
 *
 * <p>Properties are loaded from application properties with the prefix "application.security.jwt".
 *
 * <p>Includes nested properties for access tokens, refresh tokens, and cookie settings.
 */
@ConfigurationProperties(prefix = "application.security.jwt")
@Validated
public record JwtProperties(
    @NotBlank String secretKey,
    @NotBlank String issuer,
    @NotBlank String audience,
    AccessToken accessToken,
    RefreshToken refreshToken,
    CookieProperties cookie) {

  /** AccessToken properties including expiration time and cookie name. */
  public record AccessToken(long expiration, @NotBlank String name) {}

  /** RefreshToken properties including expiration time and cookie name. */
  public record RefreshToken(long expiration, @NotBlank String name) {}

  /** CookieProperties including whether cookies should be secure. */
  public record CookieProperties(boolean secure) {}
}
