package com.antares.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antares.api.config.BaseIntegrationTest;
import com.antares.api.dto.AuthenticationRequest;
import com.antares.api.dto.RegisterRequest;
import com.antares.api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the AuthenticationController.
 *
 * <p>These tests cover user authentication and authorization flows, including registration, login,
 * logout, access to protected resources, and token refresh. Each test follows the Given/When/Then
 * pattern for clarity.
 */
class AuthenticationControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  /**
   * Given: The database contains users. When: Each test starts. Then: Remove all users except
   * admins to ensure test isolation.
   */
  @BeforeEach
  void setUp() {
    userRepository.deleteAll(
        userRepository.findAll().stream()
            .filter(u -> !u.getRole().name().equals("ROLE_ADMIN"))
            .toList());
  }

  /**
   * Given: Redis may contain data from previous tests. When: Each test ends. Then: Flush all Redis
   * data to ensure test isolation.
   */
  @AfterEach
  void cleanUpRedis(@Autowired StringRedisTemplate redisTemplate) {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  @Test
  @DisplayName("Full authentication flow: register, login, access protected resource, logout")
  void testFullAuthenticationFlow_shouldSucceed() throws Exception {
    // === 1. Registration ===
    // Given: A new user registration request.
    RegisterRequest registerRequest =
        new RegisterRequest("Test", "User", "test.user@example.com", "password123");
    // When: The user registers.
    // Then: The response is 201 Created and the user has the default role.
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("test.user@example.com"))
        .andExpect(jsonPath("$.role").value("ROLE_USER"));

    // === 2. Login ===
    // Given: The registered user's credentials.
    AuthenticationRequest loginRequest =
        new AuthenticationRequest("test.user@example.com", "password123");
    // When: The user logs in.
    // Then: The response is 200 OK and the email matches.
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test.user@example.com"))
            .andReturn();
    Cookie[] loginCookies = loginResult.getResponse().getCookies();

    // === 3. Access Protected Resource ===
    // Given: The user is authenticated (has cookies).
    // When: The user accesses a protected endpoint.
    // Then: The response is 200 OK and the email matches.
    mockMvc
        .perform(get("/api/v1/users/me").cookie(loginCookies).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("test.user@example.com"));

    // === 4. Logout ===
    // Given: The user is authenticated.
    // When: The user logs out.
    // Then: The response is 200 OK and the access token cookie is cleared.
    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(loginCookies).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge("antares_access_token", 0));
  }

  @Test
  @DisplayName("Register with an existing email should return 409 Conflict")
  void testRegister_withExistingEmail_shouldReturnConflict() throws Exception {
    // Given: A user already exists with a specific email.
    RegisterRequest initialRequest =
        new RegisterRequest("Existing", "User", "existing.user@example.com", "password123");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialRequest)))
        .andExpect(status().isCreated());

    // When: Registering again with the same email.
    RegisterRequest conflictRequest =
        new RegisterRequest("Another", "User", "existing.user@example.com", "password456");
    // Then: The response is 409 Conflict.
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(conflictRequest)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Login with wrong password should return 401 Unauthorized")
  void testLogin_withWrongPassword_shouldReturnUnauthorized() throws Exception {
    // Given: A registered user.
    RegisterRequest registerRequest =
        new RegisterRequest("Login", "Test", "login.test@example.com", "correctPassword");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    // When: Logging in with the wrong password.
    AuthenticationRequest loginRequest =
        new AuthenticationRequest("login.test@example.com", "wrongPassword");
    // Then: The response is 401 Unauthorized.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Accessing a protected resource without a cookie should return 403 Forbidden")
  void testAccessProtectedResource_withoutCookie_shouldReturnForbidden() throws Exception {
    // Given: No authentication cookie is present.
    // When: Accessing a protected endpoint.
    // Then: The response is 403 Forbidden.
    mockMvc.perform(get("/api/v1/users/me").with(csrf())).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Refresh Token Flow: Use refresh token to get a new access token")
  void testRefreshTokenFlow_shouldSucceed() throws Exception {
    // Given: A registered and logged-in user.
    RegisterRequest registerRequest =
        new RegisterRequest("Refresh", "User", "refresh.user@example.com", "password123");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new AuthenticationRequest("refresh.user@example.com", "password123"))))
            .andExpect(status().isOk())
            .andReturn();

    // When: Using the refresh token to get a new access token.
    Cookie refreshTokenCookie = loginResult.getResponse().getCookie("antares_refresh_token");
    // Then: The response is 200 OK and a new access token is returned.
    mockMvc
        .perform(post("/api/v1/auth/refresh-token").cookie(refreshTokenCookie).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  @Test
  @DisplayName("Refresh Token with an invalid token should return 404 Not Found")
  void testRefreshTokenFlow_withInvalidToken_shouldReturnNotFound() throws Exception {
    // Given: An invalid refresh token cookie.
    Cookie invalidRefreshTokenCookie = new Cookie("antares_refresh_token", "invalid-token-value");

    // When: Calling the refresh-token endpoint with this cookie.
    // Then: The response is 404 Not Found.
    mockMvc
        .perform(post("/api/v1/auth/refresh-token").cookie(invalidRefreshTokenCookie).with(csrf()))
        .andExpect(status().isNotFound());
  }
}
