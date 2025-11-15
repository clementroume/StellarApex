package atlas.stellar.antares.service;

import atlas.stellar.antares.config.JwtProperties;
import atlas.stellar.antares.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

/** Service for handling JWT (JSON Web Token) creation, validation, and extraction logic. */
@Service
@Getter
public class JwtService {

  private final JwtProperties jwtProperties;
  private final String accessTokenCookieName;
  private final String refreshTokenCookieName;
  private final long accessTokenDurationMs;
  private final long refreshTokenDurationMs;
  private SecretKey signInKey; // The HMAC-SHA key used for signing tokens

  /**
   * Constructs a JwtService with the specified JwtProperties.
   *
   * @param jwtProperties The JWT configuration properties.
   */
  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.accessTokenCookieName = jwtProperties.accessToken().name();
    this.refreshTokenCookieName = jwtProperties.refreshToken().name();
    this.accessTokenDurationMs = jwtProperties.accessToken().expiration();
    this.refreshTokenDurationMs = jwtProperties.refreshToken().expiration();
  }

  /** Initializes the service by decoding the Base64 secret key. */
  @PostConstruct
  public void init() {
    this.signInKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secretKey()));
  }

  /**
   * Retrieves a JWT token from the cookies of an HTTP request.
   *
   * @param request The HTTP request.
   * @param cookieName The name of the cookie to retrieve.
   * @return The JWT token string if present, otherwise null.
   */
  public String getJwtFromCookies(HttpServletRequest request, String cookieName) {
    Cookie cookie = WebUtils.getCookie(request, cookieName);
    return cookie != null ? cookie.getValue() : null;
  }

  /**
   * Extracts the username (subject) from the JWT token.
   *
   * @param token The JWT token.
   * @return The username (email).
   */
  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  /**
   * Extracts a specific claim from the JWT token using a claims resolver function.
   *
   * @param token The JWT token.
   * @param claimsResolver A function to extract the desired claim.
   * @param <T> The type of the claim.
   * @return The extracted claim.
   */
  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    return claimsResolver.apply(extractAllClaims(token));
  }

  /**
   * Generates a standard access token for the given user.
   *
   * @param userDetails The user details.
   * @return The generated JWT access token.
   */
  public String generateToken(UserDetails userDetails) {
    return buildToken(new HashMap<>(), userDetails, accessTokenDurationMs);
  }

  /**
   * Builds a JWT token with the specified claims, subject, and expiration.
   *
   * @param extraClaims Additional claims to include.
   * @param userDetails The user (subject) of the token.
   * @param expiration The expiration time in milliseconds.
   * @return The compact, signed JWT string.
   */
  public String buildToken(
      Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
    return Jwts.builder()
        .claims(extraClaims)
        .subject(userDetails.getUsername())
        .issuer(jwtProperties.issuer())
        .audience()
        .add(jwtProperties.audience())
        .and()
        .id(UUID.randomUUID().toString())
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(signInKey)
        .compact();
  }

  /**
   * Validates the JWT token against the provided user details.
   *
   * @param token The JWT token to validate.
   * @param userDetails The user details to validate against.
   * @return true if the token is valid and belongs to the user.
   */
  public boolean isTokenValid(String token, UserDetails userDetails) {
    return (extractUsername(token).equals(userDetails.getUsername())) && !isTokenExpired(token);
  }

  /**
   * Checks if the JWT token is expired.
   *
   * @param token The JWT token.
   * @return true if the token is expired.
   * @throws InvalidTokenException if the token is malformed or invalid.
   */
  private boolean isTokenExpired(String token) {
    try {
      return extractExpiration(token).before(new Date());
    } catch (JwtException e) {
      throw new InvalidTokenException("error.token.invalid");
    }
  }

  /**
   * Extracts the expiration date from the JWT token.
   *
   * @param token The JWT token.
   * @return The expiration date.
   */
  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  /**
   * Extracts all claims from the JWT token after verifying its signature, issuer, and audience.
   *
   * @param token The JWT token.
   * @return The Claims object.
   * @throws InvalidTokenException if the token fails validation.
   */
  private Claims extractAllClaims(String token) {
    try {
      return Jwts.parser()
          .verifyWith(signInKey)
          .requireIssuer(jwtProperties.issuer())
          .requireAudience(jwtProperties.audience())
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (JwtException e) {
      throw new InvalidTokenException("error.token.invalid");
    }
  }
}
