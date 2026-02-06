 package apex.stellar.antares.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link AuthenticationController}.
 *
 * <p>These tests cover the full authentication and authorization flows, running against a real
 * database and Redis instance via Testcontainers.
 */
@Transactional
class AuthenticationControllerIT extends BaseIntegrationTest {


  // Cleans the database before each test (except for admin users).
  @BeforeEach
  void setUp() {
    userRepository.deleteAllInBatch(
        userRepository.findAll().stream()
            .filter(u -> !u.getPlatformRole().name().equals("ADMIN"))
            .toList());
  }

  @Test
  @DisplayName("Full authentication flow: Register > Login > Access Resource > Logout")
  void testFullAuthenticationFlow_shouldSucceed() throws Exception {
    // === 1. Registration ===
    // Given: A new user registration request
    RegisterRequest registerRequest =
        new RegisterRequest("Test", "User", "test.user@example.com", "password123");

    // When/Then
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("test.user@example.com"))
        .andExpect(jsonPath("$.platformRole").value("USER"));

    // === 2. Login ===
    // Given: Valid credentials
    AuthenticationRequest loginRequest =
        new AuthenticationRequest("test.user@example.com", "password123");

    // When: Login endpoint is called
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/antares/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test.user@example.com"))
            .andReturn();
    Cookie[] loginCookies = loginResult.getResponse().getCookies();

    // === 3. Access Protected Resource ===
    // Given: Authenticated session (cookies)
    // (User is authenticated via cookies)
    // When/Then
    mockMvc
        .perform(get("/antares/users/me").cookie(loginCookies).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("test.user@example.com"));

    // === 4. Logout ===
    // Given: Authenticated session
    // (User is authenticated via cookies)
    // When/Then
    mockMvc
        .perform(post("/antares/auth/logout").cookie(loginCookies).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge("stellar_access_token", 0));
  }

  @Test
  @DisplayName("Register: should return 409 Conflict for existing email")
  void testRegister_withExistingEmail_shouldReturnConflict() throws Exception {
    // Given: An existing user
    RegisterRequest initialRequest =
        new RegisterRequest("Existing", "User", "existing.user@example.com", "password123");
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(initialRequest)))
        .andExpect(status().isCreated());

    // When: Trying to register with the same email
    RegisterRequest conflictRequest =
        new RegisterRequest("Another", "User", "existing.user@example.com", "password456");

    // Then: Conflict status is returned
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(conflictRequest)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Login: should return 401 Unauthorized for wrong password")
  void testLogin_withWrongPassword_shouldReturnUnauthorized() throws Exception {
    // Given: A registered user
    RegisterRequest registerRequest =
        new RegisterRequest("Login", "Test", "login.test@example.com", "correctPassword");
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    // When: Login with an incorrect password
    AuthenticationRequest loginRequest =
        new AuthenticationRequest("login.test@example.com", "wrongPassword");

    // Then: Unauthorized status
    mockMvc
        .perform(
            post("/antares/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Access Protected: should return 403 Forbidden without auth cookie")
  void testAccessProtectedResource_withoutCookie_shouldReturnForbidden() throws Exception {
    // Given: No authentication cookies
    // When/Then: Accessing protected resource fails
    mockMvc.perform(get("/antares/users/me").with(csrf())).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Refresh Token: should succeed with a valid refresh token cookie")
  void testRefreshTokenFlow_shouldSucceed() throws Exception {
    // Given: A logged-in user with a refresh token
    RegisterRequest registerRequest =
        new RegisterRequest("Refresh", "User", "refresh.user@example.com", "password123");
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/antares/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new AuthenticationRequest("refresh.user@example.com", "password123"))))
            .andExpect(status().isOk())
            .andReturn();
    Cookie refreshTokenCookie = loginResult.getResponse().getCookie("stellar_refresh_token");
    Assertions.assertNotNull(refreshTokenCookie, "Refresh token cookie must not be null");

    // When: Refresh token endpoint is called
    mockMvc
        .perform(post("/antares/auth/refresh-token").cookie(refreshTokenCookie).with(csrf()))
        // Then: New access token is issued
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  @Test
  @DisplayName("Refresh Token: should return 404 Not Found with an invalid token")
  void testRefreshTokenFlow_withInvalidToken_shouldReturnNotFound() throws Exception {
    // Given: An invalid refresh token
    Cookie invalidRefreshTokenCookie = new Cookie("antares_refresh_token", "invalid-token-value");

    // When/Then: Request fails
    mockMvc
        .perform(post("/antares/auth/refresh-token").cookie(invalidRefreshTokenCookie).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Register: should return 400 Bad Request for invalid input")
  void testRegister_withInvalidInput_shouldReturnBadRequest() throws Exception {
    // Given: Request with invalid email and short password
    RegisterRequest invalidRequest = new RegisterRequest("", "", "not-an-email", "short");

    // When/Then
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Login: should lock account after max failed attempts (HTTP 429)")
  void testLogin_AccountLocking_shouldReturnTooManyRequests() throws Exception {
    // Given: A registered user
    RegisterRequest registerRequest =
        new RegisterRequest("Lock", "User", "lock@test.com", "password123");
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    AuthenticationRequest badLogin = new AuthenticationRequest("lock@test.com", "wrongpass");

    // When: Failing 5 times (default max-attempts)
    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(
              post("/antares/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(badLogin)))
          .andExpect(status().isUnauthorized());
    }

    // Then: The 6th attempt should be blocked with 429 Too Many Requests
    mockMvc
        .perform(
            post("/antares/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(badLogin)))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("Refresh Token: should return 404 Not Found if cookie is completely missing")
  void testRefreshTokenFlow_missingCookie_shouldReturnNotFound() throws Exception {
    // When/Then: Calling endpoint without any cookies
    mockMvc
        .perform(post("/antares/auth/refresh-token").with(csrf()))
        .andExpect(status().isNotFound());
  }
}
