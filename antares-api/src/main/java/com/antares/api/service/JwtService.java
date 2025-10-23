package com.antares.api.service;

import com.antares.api.config.JwtProperties;
import com.antares.api.exception.InvalidTokenException;
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

/** JwtService handles JWT token creation, validation, and extraction logic. */
@Service
@Getter
public class JwtService {

  private final JwtProperties jwtProperties;
  private SecretKey signInKey;
  private final String accessTokenCookieName;
  private final String refreshTokenCookieName;
  private final long accessTokenDurationMs;
  private final long refreshTokenDurationMs;

  /**
   * Constructs a JwtService with the specified JwtProperties.
   *
   * @param jwtProperties the JWT properties for configuration
   */
  public JwtService(JwtProperties jwtProperties) {

    this.jwtProperties = jwtProperties;
    this.accessTokenCookieName = jwtProperties.accessToken().name();
    this.refreshTokenCookieName = jwtProperties.refreshToken().name();
    this.accessTokenDurationMs = jwtProperties.accessToken().expiration();
    this.refreshTokenDurationMs = jwtProperties.refreshToken().expiration();
  }

  /**
   * Initializes the JwtService by decoding the secret key from Base64 and creating the signing key.
   * This method is called after the bean is constructed.
   */
  @PostConstruct
  public void init() {

    this.signInKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secretKey()));
  }

  /**
   * Retrieves the JWT token from cookies in the HTTP request.
   *
   * @param request the HTTP request containing cookies
   * @param cookieName the name of the cookie to retrieve the JWT from
   * @return the JWT token if present, otherwise null
   */
  public String getJwtFromCookies(HttpServletRequest request, String cookieName) {

    Cookie cookie = WebUtils.getCookie(request, cookieName);

    return cookie != null ? cookie.getValue() : null;
  }

  /**
   * Extracts the username (subject) from the JWT token.
   *
   * @param token the JWT token
   * @return the username extracted from the token
   */
  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  /**
   * Extracts a specific claim from the JWT token using the provided claims resolver function.
   *
   * @param token the JWT token
   * @param claimsResolver a function to extract the desired claim from the Claims object
   * @param <T> the type of the claim to be extracted
   * @return the extracted claim
   */
  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {

    return claimsResolver.apply(extractAllClaims(token));
  }

  /**
   * Generates a JWT token for the given user details with default access token expiration.
   *
   * @param userDetails the user details for whom to generate the token
   * @return the generated JWT token
   */
  public String generateToken(UserDetails userDetails) {

    return buildToken(new HashMap<>(), userDetails, accessTokenDurationMs);
  }

  /**
   * Builds a JWT token with the specified extra claims, user details, and expiration time.
   *
   * @param extraClaims additional claims to include in the token
   * @param userDetails the user details for whom to generate the token
   * @param expiration the expiration time in milliseconds
   * @return the generated JWT token
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
   * @param token the JWT token to validate
   * @param userDetails the user details to validate against
   * @return true if the token is valid and belongs to the user, false otherwise
   */
  public boolean isTokenValid(String token, UserDetails userDetails) {

    return (extractUsername(token).equals(userDetails.getUsername())) && !isTokenExpired(token);
  }

  /**
   * Checks if the JWT token is expired.
   *
   * @param token the JWT token to check
   * @return true if the token is expired, false otherwise
   * @throws InvalidTokenException if the token is invalid
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
   * @param token the JWT token
   * @return the expiration date of the token
   */
  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  /**
   * Extracts all claims from the JWT token.
   *
   * @param token the JWT token
   * @return the Claims object containing all claims from the token
   * @throws InvalidTokenException if the token is invalid
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
