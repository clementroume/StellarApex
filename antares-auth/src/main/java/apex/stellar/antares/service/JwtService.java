package apex.stellar.antares.service;

import apex.stellar.antares.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

/**
 * Service responsible for managing the lifecycle of JSON Web Tokens (JWT).
 *
 * <p>This service handles token generation using a configured {@link JwtEncoder} and provides
 * utility methods for retrieving tokens from HTTP cookies.
 *
 * <p><b>Note:</b> Token validation and parsing are delegated to the Spring Security OAuth2 Resource
 * Server configuration and are not handled by this service.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

  /** Claim holding the acting admin id in a support token. */
  public static final String CLAIM_ACTOR = "act";

  /** Claim holding the support session id in a support token. */
  public static final String CLAIM_SESSION_ID = "sid";

  /** Claim holding the token type. */
  public static final String CLAIM_TYPE = "typ";

  /** Value of {@link #CLAIM_TYPE} for support tokens. */
  public static final String SUPPORT_TOKEN_TYPE = "support";

  private final JwtProperties jwtProperties;
  private final JwtEncoder jwtEncoder;

  /**
   * Retrieves the configured name for the access token cookie.
   *
   * @return The access token cookie name.
   */
  public String getAccessTokenCookieName() {
    return jwtProperties.accessToken().name();
  }

  /**
   * Retrieves the configured name for the refresh token cookie.
   *
   * @return The refresh token cookie name.
   */
  public String getRefreshTokenCookieName() {
    return jwtProperties.refreshToken().name();
  }

  /**
   * Retrieves the configured expiration duration for access tokens.
   *
   * @return The expiration time in milliseconds.
   */
  public long getAccessTokenDurationMs() {
    return jwtProperties.accessToken().expiration();
  }

  /**
   * Retrieves the configured expiration duration for refresh tokens.
   *
   * @return The expiration time in milliseconds.
   */
  public long getRefreshTokenDurationMs() {
    return jwtProperties.refreshToken().expiration();
  }

  /**
   * Generates a signed JWT access token for the provided user details.
   *
   * <p>This method constructs a {@link JwtClaimsSet} containing standard claims (iss, aud, sub,
   * exp, iat, jti) and a custom 'scope' claim representing the user's authorities. The token is
   * signed using the {@link JwtEncoder} with the HMAC SHA-256 algorithm.
   *
   * @param userDetails The user for whom the token is being generated.
   * @return The signed JWT string.
   */
  public String generateToken(UserDetails userDetails) {
    Instant now = Instant.now();

    // Convert authorities to a space-separated string, compliant with the standard OAuth2 "scope"
    // claim format.
    String scope =
        userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(" "));

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .audience(Collections.singletonList(jwtProperties.audience()))
            .issuedAt(now)
            .expiresAt(now.plus(getAccessTokenDurationMs(), ChronoUnit.MILLIS))
            .subject(userDetails.getUsername())
            .id(UUID.randomUUID().toString())
            .claim("scope", scope)
            .build();

    JwtEncoderParameters parameters =
        JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);

    return jwtEncoder.encode(parameters).getTokenValue();
  }

  /**
   * Generates the signed, never-refreshable token of a support session.
   *
   * <p>Claims: {@code sub} = target user id, {@code act} = admin id, {@code sid} = session id,
   * {@code typ} = {@value #SUPPORT_TOKEN_TYPE}. Access tokens never carry that type, so neither
   * token can be used in place of the other.
   *
   * @param targetUserId The user the admin acts as.
   * @param adminId The acting admin.
   * @param sessionId The persisted support session id.
   * @param issuedAt Start of the session.
   * @param expiresAt End of the session.
   * @return The signed JWT string.
   */
  public String generateSupportToken(
      Long targetUserId, Long adminId, Long sessionId, Instant issuedAt, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .audience(Collections.singletonList(jwtProperties.audience()))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(String.valueOf(targetUserId))
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_ACTOR, adminId)
            .claim(CLAIM_SESSION_ID, sessionId)
            .claim(CLAIM_TYPE, SUPPORT_TOKEN_TYPE)
            .build();

    JwtEncoderParameters parameters =
        JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);

    return jwtEncoder.encode(parameters).getTokenValue();
  }

  /**
   * Retrieves the value of a specific token from the HTTP request cookies.
   *
   * <p>Uses {@link WebUtils} for safe extraction.
   *
   * @param request The incoming HTTP request.
   * @param cookieName The name of the cookie containing the token.
   * @return The token string if the cookie exists, or {@code null} otherwise.
   */
  public String getJwtFromCookies(HttpServletRequest request, String cookieName) {
    Cookie cookie = WebUtils.getCookie(request, cookieName);
    return cookie != null ? cookie.getValue() : null;
  }
}
