package atlas.stellar.antares.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Holds configuration properties for JWT tokens and cookies, loaded from application properties
 * prefixed with "application.security.jwt".
 *
 * <p>Uses Java Records for immutable, concise configuration data.
 *
 * <p>Includes nested records for AccessToken, RefreshToken, and Cookie settings.
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

  /** CookieProperties including whether cookies should be secure (HTTPS only). */
  public record CookieProperties(boolean secure) {}
}
