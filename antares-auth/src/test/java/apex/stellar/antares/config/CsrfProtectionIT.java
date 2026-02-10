package apex.stellar.antares.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.dto.ProfileUpdateRequest;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Integration tests for verifying the behavior of CSRF protection mechanisms in the application.
 * This class specifically tests scenarios involving CSRF token validation for mutating requests.
 *
 * <p>Ensures that mutating requests without a CSRF token are rejected with a 403 (Forbidden)
 * status.
 *
 * <p>Verifies that valid CSRF tokens matching the session allow mutating requests.
 *
 * <p>Asserts that forged or mismatched CSRF tokens from a different session result in a 403
 * (Forbidden) status.
 *
 * <p>Validates that requests with a missing CSRF cookie but a present `X-XSRF-TOKEN` header are
 * flagged and rejected.
 */
class CsrfProtectionIT extends BaseIntegrationTest {

  private Cookie[] authCookies;
  private String csrfCookie;

  @BeforeEach
  void setUp() throws Exception {
    userRepository.deleteAll();

    // 1. Register and login to get auth cookies
    authCookies = registerAndLogin("csrf.user@example.com", "password123");

    // 2. Extract CSRF token from auth cookies
    csrfCookie = getXsrfToken(authCookies);
  }

  @Test
  @DisplayName("Should reject mutating request without CSRF token (403)")
  void shouldRejectMutatingRequestWithoutCsrfToken() throws Exception {
    // Given
    ProfileUpdateRequest request = new ProfileUpdateRequest("John", "Doe", "csrf.user@example.com");

    // Filter out CSRF cookie to simulate missing CSRF token
    Cookie[] cookiesWithoutCsrf =
        Arrays.stream(authCookies)
            .filter(c -> !"XSRF-TOKEN".equals(c.getName()))
            .toArray(Cookie[]::new);

    // When / Then
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .secure(true) // Important: CSRF cookie is Secure, so request must be Secure
                .cookie(cookiesWithoutCsrf)
                // No X-XSRF-TOKEN header provided
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should accept mutating request with valid CSRF token (200)")
  void shouldAcceptMutatingRequestWithValidCsrfToken() throws Exception {
    // Given
    ProfileUpdateRequest request = new ProfileUpdateRequest("John", "Doe", "csrf.user@example.com");

    // When / Then: Perform PUT with CSRF cookie AND matching header
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .secure(true)
                .cookie(authCookies) // Contains JWTs + CSRF Cookie
                .header("X-XSRF-TOKEN", csrfCookie) // Token in Header
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should reject CSRF token from different session/forged (403)")
  void shouldRejectCsrfTokenFromDifferentSession() throws Exception {
    // Given
    ProfileUpdateRequest request =
        new ProfileUpdateRequest("Hacker", "Doe", "csrf.user@example.com");

    // When / Then
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .secure(true)
                .cookie(authCookies)
                .header("X-XSRF-TOKEN", "forged-token-value") // Mismatch with cookie
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject request with missing CSRF cookie but present header (403)")
  void shouldRejectRequestWithMissingCsrfCookie() throws Exception {
    // Given
    String fakeToken = "some-random-token";
    ProfileUpdateRequest request = new ProfileUpdateRequest("John", "Doe", "csrf.user@example.com");

    // Filter out the XSRF-TOKEN cookie to simulate a missing cookie (Double-Submit bypass attempt)
    Cookie[] cookiesWithoutCsrf =
        Arrays.stream(authCookies)
            .filter(c -> !"XSRF-TOKEN".equals(c.getName()))
            .toArray(Cookie[]::new);

    // When / Then
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .secure(true)
                .cookie(cookiesWithoutCsrf) // Send cookies WITHOUT CSRF token
                .header("X-XSRF-TOKEN", fakeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }
}
