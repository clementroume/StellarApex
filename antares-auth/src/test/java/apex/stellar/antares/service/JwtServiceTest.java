package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.antares.config.JwtProperties;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>This test class verifies the token generation logic (delegation to {@link JwtEncoder}) and the
 * cookie retrieval mechanism. Token validation logic is not tested here as it is now handled
 * natively by the Spring Security OAuth2 Resource Server.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

  @Mock private JwtProperties jwtProperties;
  @Mock private JwtEncoder jwtEncoder;
  @Mock private HttpServletRequest request;

  @InjectMocks private JwtService jwtService;

  private UserDetails userDetails;

  /**
   * Sets up the test environment.
   *
   * <p>Initializes a default {@link UserDetails} instance used across multiple tests. Note: {@code
   * jwtProperties} are not mocked globally here to avoid {@code UnnecessaryStubbingException} in
   * tests that do not use them (e.g., cookie retrieval).
   */
  @BeforeEach
  void setUp() {
    userDetails =
        User.builder()
            .email("test@example.com")
            .password("password")
            .platformRole(PlatformRole.USER)
            .build();
  }

  @Test
  @DisplayName("generateToken: should correctly delegate to JwtEncoder with expected claims")
  void generateToken_shouldDelegateToJwtEncoder() {
    // Given
    // Mocking JWT properties specifically for this test case
    JwtProperties.AccessToken accessTokenProps = mock(JwtProperties.AccessToken.class);

    // Use lenient() because strictly speaking, other calls might happen internally
    lenient().when(jwtProperties.accessToken()).thenReturn(accessTokenProps);
    lenient().when(accessTokenProps.expiration()).thenReturn(60000L);

    // Important: The issuer must be a valid URL to satisfy Spring Security's strict validation
    when(jwtProperties.issuer()).thenReturn("https://test-issuer.com");
    when(jwtProperties.audience()).thenReturn("test-audience");

    // Mocking the JwtEncoder behavior
    Jwt jwtMock = mock(Jwt.class);
    when(jwtMock.getTokenValue()).thenReturn("encoded-jwt-token-value");
    when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwtMock);

    // When
    String tokenValue = jwtService.generateToken(userDetails);

    // Then
    assertEquals("encoded-jwt-token-value", tokenValue);

    // Capturing the parameters passed to the encoder to verify claims content
    ArgumentCaptor<JwtEncoderParameters> captor =
        ArgumentCaptor.forClass(JwtEncoderParameters.class);
    verify(jwtEncoder).encode(captor.capture());

    JwtEncoderParameters params = captor.getValue();

    // Assertions on standard claims
    assertEquals("https://test-issuer.com", params.getClaims().getIssuer().toString());
    assertTrue(params.getClaims().getAudience().contains("test-audience"));
    assertEquals("test@example.com", params.getClaims().getSubject());

    // Assertion on the custom "scope" claim (mapped from authorities)
    assertEquals("ROLE_USER", params.getClaims().getClaim("scope"));
  }

  @Test
  @DisplayName("getJwtFromCookies: should return the token value when the cookie exists")
  void getJwtFromCookies_shouldReturnToken_whenCookieExists() {
    // Given
    Cookie cookie = new Cookie("stellar_access_token", "cookie-token-value");
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});

    // When
    String token = jwtService.getJwtFromCookies(request, "stellar_access_token");

    // Then
    assertEquals("cookie-token-value", token);
  }

  @Test
  @DisplayName("getJwtFromCookies: should return null when the cookie is missing")
  void getJwtFromCookies_shouldReturnNull_whenCookieMissing() {
    // Given
    when(request.getCookies()).thenReturn(new Cookie[] {});

    // When
    String token = jwtService.getJwtFromCookies(request, "stellar_access_token");

    // Then
    assertNull(token);
  }
}
