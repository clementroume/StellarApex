package com.antares.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antares.api.dto.ChangePasswordRequest;
import com.antares.api.dto.ProfileUpdateRequest;
import com.antares.api.exception.InvalidPasswordException;
import com.antares.api.mapper.UserMapper;
import com.antares.api.model.User;
import com.antares.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for the {@link UserService}.
 *
 * <p>Each test follows the Given/When/Then pattern for clarity and maintainability.
 *
 * <p>Given: The initial state or preconditions for the test. When: The action or event being
 * tested. Then: The expected outcome or assertion.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserMapper userMapper;

  @InjectMocks private UserService userService;

  private User testUser;

  @BeforeEach
  void setUp() {
    // Given: A reusable User object for each test.
    testUser = new User();
    testUser.setId(1L);
    testUser.setEmail("test@example.com");
    testUser.setPassword("hashedPassword");
  }

  @Test
  @DisplayName("updateProfile should call mapper and repository to save changes")
  void testUpdateProfile_shouldUpdateAndSaveChanges() {
    // Given: A request to update the user's profile.
    ProfileUpdateRequest request = new ProfileUpdateRequest("John", "Doe", "john.doe@example.com");

    // When: updateProfile is called.
    userService.updateProfile(testUser, request);

    // Then: The userMapper and userRepository are called to apply and persist changes.
    verify(userMapper).updateFromProfile(request, testUser);
    verify(userRepository).save(testUser);
  }

  @Test
  @DisplayName("changePassword should succeed with correct current password")
  void testChangePassword_withCorrectCurrentPassword_shouldChangePassword() {
    // Given: A valid password change request and correct current password.
    ChangePasswordRequest request =
        new ChangePasswordRequest("oldPassword", "newPassword", "newPassword");
    when(passwordEncoder.matches("oldPassword", "hashedPassword")).thenReturn(true);

    // When: changePassword is called.
    userService.changePassword(request, testUser);

    // Then: The password is encoded and the user is saved.
    verify(passwordEncoder).encode("newPassword");
    verify(userRepository).save(testUser);
  }

  @Test
  @DisplayName(
      "changePassword should throw InvalidPasswordException for incorrect current password")
  void testChangePassword_withIncorrectCurrentPassword_shouldThrowException() {
    // Given: A password change request with incorrect current password.
    ChangePasswordRequest request =
        new ChangePasswordRequest("wrongOldPassword", "newPassword", "newPassword");
    when(passwordEncoder.matches("wrongOldPassword", "hashedPassword")).thenReturn(false);

    // When & Then: changePassword throws InvalidPasswordException and user is not saved.
    assertThrows(
        InvalidPasswordException.class, () -> userService.changePassword(request, testUser));
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("changePassword should throw InvalidPasswordException for mismatched new passwords")
  void testChangePassword_withMismatchedNewPasswords_shouldThrowException() {
    // Given: A request where new password and confirmation do not match.
    ChangePasswordRequest request =
        new ChangePasswordRequest("oldPassword", "newPassword", "mismatchedPassword");
    when(passwordEncoder.matches("oldPassword", "hashedPassword")).thenReturn(true);

    // When & Then: changePassword throws InvalidPasswordException and user is not saved.
    assertThrows(
        InvalidPasswordException.class, () -> userService.changePassword(request, testUser));
    verify(userRepository, never()).save(any(User.class));
  }
}
