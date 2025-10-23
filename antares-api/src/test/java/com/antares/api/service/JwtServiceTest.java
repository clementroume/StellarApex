package com.antares.api.service;

import static org.junit.jupiter.api.Assertions.*;

import com.antares.api.config.JwtProperties;
import com.antares.api.exception.InvalidTokenException;
import com.antares.api.model.Role;
import com.antares.api.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>Each test follows the Given/When/Then pattern for clarity and maintainability.
 *
 * <p>Given: The initial state or preconditions for the test. When: The action or event being
 * tested. Then: The expected outcome or assertion.
 */
class JwtServiceTest {
  private JwtService jwtService;
  private UserDetails userDetails;

  @BeforeEach
  void setUp() {
    // Given: A JwtService initialized with test properties and a test user.
    String testSecretKey =
        "YjQ1ZGRjYjU5YjYwNzZkMWY2MzE4YmFiY2Y4ZjgxMGE0YzY4ZmIwYmZkOTRkMjYxYmVjZGU1Y2Y3YWQyYjQzYw==";
    JwtProperties jwtProperties =
        new JwtProperties(
            testSecretKey,
            "test-issuer",
            "test-audience",
            new JwtProperties.AccessToken(900000L, "access_token"),
            new JwtProperties.RefreshToken(604800000L, "refresh_token"),
            new JwtProperties.CookieProperties(false));
    jwtService = new JwtService(jwtProperties);
    jwtService.init(); // Manually call @PostConstruct method

    userDetails =
        User.builder().email("test@example.com").password("password").role(Role.ROLE_USER).build();
  }

  @Test
  @DisplayName("generateToken should create a valid access token with default expiration")
  void testGenerateToken_shouldCreateValidToken() {
    // Given: A valid userDetails.
    // When: Generating a token.
    String token = jwtService.generateToken(userDetails);

    // Then: The token is not null and contains expected claims.
    assertNotNull(token);
    Claims claims =
        Jwts.parser()
            .verifyWith(jwtService.getSignInKey())
            .requireIssuer("test-issuer")
            .requireAudience("test-audience")
            .build()
            .parseSignedClaims(token)
            .getPayload();

    assertEquals("test@example.com", claims.getSubject());
    assertNotNull(claims.getId());
    assertNotNull(claims.getIssuedAt());
    assertNotNull(claims.getExpiration());
  }

  @Test
  @DisplayName("isTokenValid should return true for a valid token")
  void testIsTokenValid_withValidToken_shouldReturnTrue() {
    // Given: A valid token for the user.
    String token = jwtService.generateToken(userDetails);

    // When & Then: Validating the token does not throw.
    assertDoesNotThrow(() -> jwtService.isTokenValid(token, userDetails));
  }

  @Test
  @DisplayName("isTokenValid should throw InvalidTokenException for an expired token")
  void testIsTokenValid_withExpiredToken_shouldThrowException() {
    // Given: An expired token created with the JwtService configuration.
    String expiredToken =
        Jwts.builder()
            .subject(userDetails.getUsername())
            .issuer(jwtService.getJwtProperties().issuer())
            .audience()
            .add(jwtService.getJwtProperties().audience())
            .and()
            .issuedAt(Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)))
            .expiration(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
            .signWith(jwtService.getSignInKey())
            .compact();

    // When & Then: Validating the expired token throws InvalidTokenException.
    assertThrows(
        InvalidTokenException.class, () -> jwtService.isTokenValid(expiredToken, userDetails));
  }

  @Test
  @DisplayName("isTokenValid should return false for a different user")
  void testIsTokenValid_withDifferentUser_shouldReturnFalse() {
    // Given: A token for the primary user and a different user.
    String token = jwtService.generateToken(userDetails);
    UserDetails anotherUser = User.builder().email("another@example.com").build();

    // When: Validating the token against a different user.
    // Then: The result is false.
    assertFalse(jwtService.isTokenValid(token, anotherUser));
  }
}
