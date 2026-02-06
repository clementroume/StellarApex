package apex.stellar.antares.service;

import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.PreferencesUpdateRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.InvalidPasswordException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.User; 
import apex.stellar.antares.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class responsible for managing user account details.
 *
 * <p>Handles profile updates, password changes, and preference settings. Ensures data integrity
 * (e.g., unique emails) and handles cache invalidation.
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;
  private final RefreshTokenService refreshTokenService;

  /**
   * Retrieves the public profile of the authenticated user.
   *
   * @param currentUser The authenticated user entity.
   * @return The {@link UserResponse} DTO.
   */
  @Transactional(readOnly = true)
  public UserResponse getProfile(User currentUser) {
    User user =
        userRepository
            .findById(currentUser.getId())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.user.not.found", currentUser.getId()));
    return userMapper.toUserResponse(user);
  }

  /**
   * Updates the core profile information (name, email) of a user.
   *
   * <p><b>Uniqueness Check:</b> Verifies that the new email address is not already taken by another
   * user before applying updates.
   *
   * @param currentUser The user entity to update (provided by Security Context).
   * @param request The DTO with the new profile data.
   * @return The updated {@link UserResponse} DTO.
   * @throws DataConflictException If the new email address is already in use.
   */
  @Transactional
  @CacheEvict(value = "users", key = "#currentUser.email")
  public UserResponse updateProfile(User currentUser, ProfileUpdateRequest request) {
    // Check for email uniqueness only if the email is actually changing
    if (!currentUser.getEmail().equals(request.email())
        && userRepository.existsByEmail(request.email())) {
      throw new DataConflictException("error.email.in.use", request.email());
    }

    userMapper.updateFromProfile(request, currentUser);

    return userMapper.toUserResponse(userRepository.save(currentUser));
  }

  /**
   * Updates the preferences (locale, theme) of a user.
   *
   * @param currentUser The user entity to update.
   * @param request The DTO with the new preferences.
   * @return The updated {@link UserResponse} DTO.
   */
  @Transactional
  @CacheEvict(value = "users", key = "#currentUser.email")
  public UserResponse updatePreferences(User currentUser, PreferencesUpdateRequest request) {
    userMapper.updateFromPreferences(request, currentUser);
    return userMapper.toUserResponse(userRepository.save(currentUser));
  }

  /**
   * Securely changes the password for the currently authenticated user.
   *
   * @param request The DTO containing old and new passwords.
   * @param currentUser The user entity to update.
   * @throws InvalidPasswordException If the current password is incorrect or new passwords
   *     mismatch.
   */
  @Transactional
  @CacheEvict(value = "users", key = "#currentUser.email")
  public void changePassword(ChangePasswordRequest request, User currentUser) {
    // 1. Verify the current password
    if (!passwordEncoder.matches(request.currentPassword(), currentUser.getPassword())) {
      throw new InvalidPasswordException("error.password.incorrect");
    }

    // 2. Verify confirmation (Double check)
    if (!request.newPassword().equals(request.confirmationPassword())) {
      throw new InvalidPasswordException("error.password.mismatch");
    }

    // 3. Update password
    currentUser.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(currentUser);
  }

  /**
   * Deletes the account of the currently authenticated user.
   *
   * <p>Removes user data and invalidates all associated security tokens.
   *
   * @param currentUser The user entity to be deleted.
   */
  @Transactional
  @CacheEvict(value = "users", key = "#currentUser.email")
  public void deleteAccount(User currentUser) {
    refreshTokenService.deleteTokenForUser(currentUser);
    userRepository.delete(currentUser);
  }
}
