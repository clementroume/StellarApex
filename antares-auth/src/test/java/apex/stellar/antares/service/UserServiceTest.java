package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.exception.InvalidPasswordException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests for the {@link UserService}. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserMapper userMapper;
  @Mock private RefreshTokenService refreshTokenService;

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
  @DisplayName("updateProfile: should call mapper and repository to save changes")
  void testUpdateProfile_shouldUpdateAndSaveChanges() {
    // Given
    ProfileUpdateRequest request = new ProfileUpdateRequest("John", "Doe", "john.doe@example.com");

    // When
    userService.updateProfile(testUser, request);

    // Then
    verify(userMapper).updateFromProfile(request, testUser);
    verify(userRepository).save(testUser);
  }

  @Test
  @DisplayName("changePassword: should succeed with correct current password")
  void testChangePassword_withCorrectCurrentPassword_shouldChangePassword() {
    // Given
    ChangePasswordRequest request =
        new ChangePasswordRequest("oldPassword", "newPassword", "newPassword");
    when(passwordEncoder.matches("oldPassword", "hashedPassword")).thenReturn(true);

    // When
    userService.changePassword(request, testUser);

    // Then
    verify(passwordEncoder).encode("newPassword");
    verify(userRepository).save(testUser);
  }

  @Test
  @DisplayName(
      "changePassword: should throw InvalidPasswordException for incorrect current password")
  void testChangePassword_withIncorrectCurrentPassword_shouldThrowException() {
    // Given
    ChangePasswordRequest request =
        new ChangePasswordRequest("wrongOldPassword", "newPassword", "newPassword");
    when(passwordEncoder.matches("wrongOldPassword", "hashedPassword")).thenReturn(false);

    // When & Then
    assertThrows(
        InvalidPasswordException.class, () -> userService.changePassword(request, testUser));
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("changePassword: should throw InvalidPasswordException for mismatched new passwords")
  void testChangePassword_withMismatchedNewPasswords_shouldThrowException() {
    // Given
    ChangePasswordRequest request =
        new ChangePasswordRequest("oldPassword", "newPassword", "mismatchedPassword");
    when(passwordEncoder.matches("oldPassword", "hashedPassword")).thenReturn(true);

    // When & Then
    assertThrows(
        InvalidPasswordException.class, () -> userService.changePassword(request, testUser));
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("deleteAccount: should remove refresh token and delete user from repository")
  void testDeleteAccount_shouldCleanupAndDelete() {
    // When
    userService.deleteAccount(testUser);

    // Then
    verify(refreshTokenService).deleteTokenForUser(testUser);
    verify(userRepository).delete(testUser);
  }
}
