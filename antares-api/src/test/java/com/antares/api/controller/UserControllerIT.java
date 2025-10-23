package com.antares.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antares.api.config.BaseIntegrationTest;
import com.antares.api.dto.AuthenticationRequest;
import com.antares.api.dto.ChangePasswordRequest;
import com.antares.api.dto.PreferencesUpdateRequest;
import com.antares.api.dto.ProfileUpdateRequest;
import com.antares.api.dto.RegisterRequest;
import com.antares.api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for user profile and settings management endpoints.
 *
 * <p>Each test follows the Given/When/Then pattern for clarity and maintainability.
 *
 * <p>Given: The initial state or preconditions for the test. When: The action or event being
 * tested. Then: The expected outcome or assertion.
 *
 * <p>This class uses {@link Testcontainers} to run against a real PostgreSQL database, ensuring
 * isolation and production-like behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class UserControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  private Cookie[] authCookies;
  private final String initialEmail = "profile.user@example.com";
  private final String initialPassword = "password123";

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

  /**
   * Given: The database may contain users from previous tests. When: Each test starts. Then: Remove
   * all users except admins, register a new user, and log in to obtain valid cookies.
   */
  @BeforeEach
  void setupUserAndLogin() throws Exception {
    userRepository.deleteAll(
        userRepository.findAll().stream()
            .filter(u -> !u.getRole().name().equals("ROLE_ADMIN"))
            .toList());

    RegisterRequest registerRequest =
        new RegisterRequest("Profile", "User", initialEmail, initialPassword);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    AuthenticationRequest loginRequest = new AuthenticationRequest(initialEmail, initialPassword);
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    this.authCookies = loginResult.getResponse().getCookies();
  }

  @Test
  @DisplayName("Update profile with valid data should succeed")
  void testUpdateProfile_shouldSucceed() throws Exception {
    // Given: A valid profile update request.
    ProfileUpdateRequest profileRequest =
        new ProfileUpdateRequest(
            "UpdatedFirstName", "UpdatedLastName", "updated.email@example.com");

    // When: Sending a PUT request to update the profile.
    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(profileRequest)))
        // Then: The response is OK and contains the updated data.
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("UpdatedFirstName"))
        .andExpect(jsonPath("$.email").value("updated.email@example.com"));
  }

  @Test
  @DisplayName("Update preferences with valid data should succeed")
  void testUpdatePreferences_shouldSucceed() throws Exception {
    // Given: A valid preferences update request.
    PreferencesUpdateRequest preferencesRequest = new PreferencesUpdateRequest("fr", "dark");

    // When: Sending a PATCH request to update preferences.
    mockMvc
        .perform(
            patch("/api/v1/users/me/preferences")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(preferencesRequest)))
        // Then: The response is OK and contains the updated preferences.
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locale").value("fr"))
        .andExpect(jsonPath("$.theme").value("dark"));
  }

  @Test
  @DisplayName("Change password with valid data should succeed and invalidate old password")
  void testChangePassword_shouldSucceedAndInvalidateOldPassword() throws Exception {
    // Given: A valid password change request.
    String newPassword = "newStrongPassword123";
    ChangePasswordRequest passwordRequest =
        new ChangePasswordRequest(initialPassword, newPassword, newPassword);

    // When: Sending a PUT request to change the password.
    mockMvc
        .perform(
            put("/api/v1/users/me/password")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        .andExpect(status().isOk());

    // Then: Logging in with the old password should fail.
    AuthenticationRequest loginWithOldPassword =
        new AuthenticationRequest(initialEmail, initialPassword);
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginWithOldPassword)))
        .andExpect(status().isUnauthorized());

    // And: Logging in with the new password should succeed.
    AuthenticationRequest loginWithNewPassword =
        new AuthenticationRequest(initialEmail, newPassword);
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginWithNewPassword)))
        .andExpect(status().isOk());
  }
}
