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
 * Integration tests for the {@link AuthenticationController}.
 *
 * <p>These tests cover the full authentication and authorization flows, running against a real
 * database and Redis instance via Testcontainers.
 */
class AuthenticationControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  /** Cleans the database before each test (except for admin users). */
  @BeforeEach
  void setUp() {
    userRepository.deleteAll(
        userRepository.findAll().stream()
            .filter(u -> !u.getRole().name().equals("ROLE_ADMIN"))
            .toList());
  }

  /** Cleans Redis after each test to ensure isolation. */
  @AfterEach
  void cleanUpRedis(@Autowired StringRedisTemplate redisTemplate) {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  @Test
  @DisplayName("Full authentication flow: Register > Login > Access Resource > Logout")
  void testFullAuthenticationFlow_shouldSucceed() throws Exception {
    // === 1. Registration ===
    // Given
    RegisterRequest registerRequest =
        new RegisterRequest("Test", "User", "test.user@example.com", "password123");

    // When/Then
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("test.user@example.com"))
        .andExpect(jsonPath("$.role").value("ROLE_USER"));

    // === 2. Login ===
    // Given
    AuthenticationRequest loginRequest =
        new AuthenticationRequest("test.user@example.com", "password123");

    // When
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
    // Given
    // (User is authenticated via cookies)
    // When/Then
    mockMvc
        .perform(get("/api/v1/users/me").cookie(loginCookies).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("test.user@example.com"));

    // === 4. Logout ===
    // Given
    // (User is authenticated via cookies)
    // When/Then
    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(loginCookies).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge("antares_access_token", 0));
  }

  @Test
  @DisplayName("Register: should return 409 Conflict for existing email")
  void testRegister_withExistingEmail_shouldReturnConflict() throws Exception {
    // Given
    RegisterRequest initialRequest =
        new RegisterRequest("Existing", "User", "existing.user@example.com", "password123");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialRequest)))
        .andExpect(status().isCreated());

    // When
    RegisterRequest conflictRequest =
        new RegisterRequest("Another", "User", "existing.user@example.com", "password456");

    // Then
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(conflictRequest)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Login: should return 401 Unauthorized for wrong password")
  void testLogin_withWrongPassword_shouldReturnUnauthorized() throws Exception {
    // Given
    RegisterRequest registerRequest =
        new RegisterRequest("Login", "Test", "login.test@example.com", "correctPassword");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    // When
    AuthenticationRequest loginRequest =
        new AuthenticationRequest("login.test@example.com", "wrongPassword");

    // Then
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Access Protected: should return 403 Forbidden without auth cookie")
  void testAccessProtectedResource_withoutCookie_shouldReturnForbidden() throws Exception {
    // Given (No cookie)
    // When/Then
    mockMvc.perform(get("/api/v1/users/me").with(csrf())).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Refresh Token: should succeed with a valid refresh token cookie")
  void testRefreshTokenFlow_shouldSucceed() throws Exception {
    // Given
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
    Cookie refreshTokenCookie = loginResult.getResponse().getCookie("antares_refresh_token");

    // When
    mockMvc
        .perform(post("/api/v1/auth/refresh-token").cookie(refreshTokenCookie).with(csrf()))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  @Test
  @DisplayName("Refresh Token: should return 404 Not Found with an invalid token")
  void testRefreshTokenFlow_withInvalidToken_shouldReturnNotFound() throws Exception {
    // Given
    Cookie invalidRefreshTokenCookie = new Cookie("antares_refresh_token", "invalid-token-value");

    // When/Then
    mockMvc
        .perform(post("/api/v1/auth/refresh-token").cookie(invalidRefreshTokenCookie).with(csrf()))
        .andExpect(status().isNotFound());
  }
}
