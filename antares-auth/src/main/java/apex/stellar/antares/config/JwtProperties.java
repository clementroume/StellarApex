package apex.stellar.antares.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Holds configuration properties for JWT tokens and cookies.
 *
 * <p>Includes robust validation rules to ensure security best practices are met at the application
 * startup (e.g., minimum secret length, safe expiration windows).
 */
@ConfigurationProperties(prefix = "application.security.jwt")
@Validated
public record JwtProperties(
    @NotBlank
        @Pattern(
            regexp = "^[A-Za-z0-9+/=]{32,}$",
            message = "Secret key must be at least 32 characters (256 bits)")
        String secretKey,
    @NotBlank String issuer,
    @NotBlank String audience,
    @Valid AccessToken accessToken,
    @Valid RefreshToken refreshToken,
    @Valid CookieProperties cookie) {

  /** AccessToken properties including expiration time and cookie name. */
  public record AccessToken(
      @Min(value = 60000, message = "Access token expiration must be at least 1 minute")
          @Max(value = 3600000, message = "Access token should not exceed 1 hour for security")
          long expiration,
      @NotBlank String name) {}

  /** RefreshToken properties including expiration time and cookie name. */
  public record RefreshToken(
      @Min(value = 3600000, message = "Refresh token must be at least 1 hour")
          @Max(value = 2592000000L, message = "Refresh token should not exceed 30 days")
          long expiration,
      @NotBlank String name) {}

  /** CookieProperties including whether cookies should be secure (HTTPS only). */
  public record CookieProperties(boolean secure, String domain) {}
}
