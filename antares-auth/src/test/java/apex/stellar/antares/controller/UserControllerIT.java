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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

/**
 * Integration tests for user profile and settings management endpoints in {@link UserController}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UserControllerIT extends BaseIntegrationTest {

  private final String initialEmail = "profile.user@example.com";
  private final String initialPassword = "password123";
  private Cookie[] authCookies; // Stores auth cookies for test requests

  /**
   * Before each test: 1. Clean the user database (except admins). 2. Register a new test user. 3.
   * Log in as that user. 4. Store the authentication cookies in `authCookies` for use in tests.
   */
  @BeforeEach
  void setupUserAndLogin() throws Exception {
    userRepository.deleteAll(
        userRepository.findAll().stream()
            .filter(u -> !u.getPlatformRole().name().equals("ADMIN"))
            .toList());

    this.authCookies = registerAndLogin(initialEmail, initialPassword);
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
                .content(jsonMapper.writeValueAsString(profileRequest)))
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
                .content(jsonMapper.writeValueAsString(preferencesRequest)))
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
                .content(jsonMapper.writeValueAsString(passwordRequest)))
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
                .content(jsonMapper.writeValueAsString(loginWithOldPassword)))
        .andExpect(status().isUnauthorized());

    // And: Login with the new password should succeed
    AuthenticationRequest loginWithNewPassword =
        new AuthenticationRequest(initialEmail, newPassword);
    mockMvc
        .perform(
            post("/antares/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(loginWithNewPassword)))
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
                .content(jsonMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Update Profile: should return 409 Conflict if email is already taken")
  void testUpdateProfile_withExistingEmail_shouldReturnConflict() throws Exception {
    // Given: A second user exists in the database
    register("another.user@example.com", "password123");

    // When: The first user tries to take the second user's email
    ProfileUpdateRequest conflictRequest =
        new ProfileUpdateRequest("Conflict", "User", "another.user@example.com");

    // Then: The request should be rejected with 409 Conflict
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(conflictRequest)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Update Profile: should return 400 Bad Request for invalid data")
  void testUpdateProfile_withInvalidData_shouldReturnBadRequest() throws Exception {
    // Given: A request with a blank first name
    ProfileUpdateRequest invalidRequest = new ProfileUpdateRequest("", "Doe", "valid@email.com");

    // When/Then
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Update Preferences: should return 400 Bad Request for invalid locale format")
  void testUpdatePreferences_withInvalidData_shouldReturnBadRequest() throws Exception {
    // Given: A request with an invalid locale format
    PreferencesUpdateRequest invalidRequest =
        new PreferencesUpdateRequest("invalid-locale", "dark");

    // When/Then
    mockMvc
        .perform(
            patch("/antares/users/me/preferences")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Change Password: should return 400 for wrong current password")
  void testChangePassword_withWrongCurrentPassword_shouldReturnBadRequest() throws Exception {
    // Given
    ChangePasswordRequest request =
        new ChangePasswordRequest("wrong-current-password", "newPass123", "newPass123");

    // When/Then
    mockMvc
        .perform(
            put("/antares/users/me/password")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Change Password: should return 400 for mismatched new passwords")
  void testChangePassword_withMismatchedNewPassword_shouldReturnBadRequest() throws Exception {
    // Given
    ChangePasswordRequest request =
        new ChangePasswordRequest(initialPassword, "newPass123", "mismatchedPass456");

    // When/Then
    mockMvc
        .perform(
            put("/antares/users/me/password")
                .cookie(authCookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
