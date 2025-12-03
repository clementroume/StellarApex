package apex.stellar.antares.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.PreferencesUpdateRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for user profile and settings management endpoints in {@link UserController}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UserControllerIT extends BaseIntegrationTest {

  private final String initialEmail = "profile.user@example.com";
  private final String initialPassword = "password123";
  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @Autowired private UserRepository userRepository;
  private Cookie[] authCookies; // Stores auth cookies for test requests

  /** Cleans Redis after each test. */
  @AfterEach
  void cleanUpRedis(@Autowired StringRedisTemplate redisTemplate) {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  /**
   * Before each test: 1. Clean the user database (except admins). 2. Register a new test user. 3.
   * Log in as that user. 4. Store the authentication cookies in `authCookies` for use in tests.
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
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    AuthenticationRequest loginRequest = new AuthenticationRequest(initialEmail, initialPassword);
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/antares/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

    this.authCookies = loginResult.getResponse().getCookies();
  }

  @Test
  @DisplayName("Update Profile: should succeed with valid data and auth")
  void testUpdateProfile_shouldSucceed() throws Exception {
    // Given
    ProfileUpdateRequest profileRequest =
        new ProfileUpdateRequest(
            "UpdatedFirstName", "UpdatedLastName", "updated.email@example.com");

    // When
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .cookie(authCookies)
                .with(csrf()) // Add CSRF token
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(profileRequest)))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("UpdatedFirstName"))
        .andExpect(jsonPath("$.email").value("updated.email@example.com"));
  }

  @Test
  @DisplayName("Update Preferences: should succeed with valid data and auth")
  void testUpdatePreferences_shouldSucceed() throws Exception {
    // Given
    PreferencesUpdateRequest preferencesRequest = new PreferencesUpdateRequest("fr", "dark");

    // When
    mockMvc
        .perform(
            patch("/antares/users/me/preferences")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(preferencesRequest)))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locale").value("fr"))
        .andExpect(jsonPath("$.theme").value("dark"));
  }

  @Test
  @DisplayName("Change Password: should succeed and invalidate old password")
  void testChangePassword_shouldSucceedAndInvalidateOldPassword() throws Exception {
    // Given
    String newPassword = "newStrongPassword123";
    ChangePasswordRequest passwordRequest =
        new ChangePasswordRequest(initialPassword, newPassword, newPassword);

    // When
    mockMvc
        .perform(
            put("/antares/users/me/password")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(passwordRequest)))
        // Then
        .andExpect(status().isOk());

    // --- Verification ---
    // Then: Login with old password should fail
    AuthenticationRequest loginWithOldPassword =
        new AuthenticationRequest(initialEmail, initialPassword);
    mockMvc
        .perform(
            post("/antares/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginWithOldPassword)))
        .andExpect(status().isUnauthorized());

    // And: Login with a new password should succeed
    AuthenticationRequest loginWithNewPassword =
        new AuthenticationRequest(initialEmail, newPassword);
    mockMvc
        .perform(
            post("/antares/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginWithNewPassword)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Delete Account: should remove user data and clear cookies")
  void testDeleteAccount_shouldSucceed() throws Exception {
    // Given: An authenticated user
    // (We reuse the cookies from setupUserAndLogin() in @BeforeEach, or recreate context if needed)
    // Here, we assume 'authCookies' is available thanks to the @BeforeEach of the existing class.

    // When: DELETE call to /me
    mockMvc
        .perform(delete("/antares/users/me").cookie(authCookies).with(csrf()))
        // Then: Status 204 No Content
        .andExpect(status().isNoContent())
        // Verify cookie clearing (Max-Age=0)
        .andExpect(cookie().maxAge("stellar_access_token", 0))
        .andExpect(cookie().maxAge("stellar_refresh_token", 0));

    // DB Verification: The user must no longer exist
    assertThat(userRepository.findByEmail(initialEmail)).isEmpty();

    // Login Verification: A subsequent login attempt must fail
    AuthenticationRequest loginRequest = new AuthenticationRequest(initialEmail, initialPassword);
    mockMvc
        .perform(
            post("/antares/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(
            status().isUnauthorized()); // Or ResourceNotFound depending on your error handling
  }
}
