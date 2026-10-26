package apex.stellar.antares.service;

import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.DisplayNameResponse;
import apex.stellar.antares.dto.PreferencesUpdateRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.dto.UserSummary;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.InvalidPasswordException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
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

  /** Maximum page size of {@link #searchUsers}. */
  public static final int SEARCH_MAX_PAGE_SIZE = 50;

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
   * Searches users for platform admins (e.g. to start a support session).
   *
   * @param search text matched against first name, last name and email (case-insensitive); blank
   *     matches everyone.
   * @param gymId optional gym the user must belong to.
   * @param role optional role of that membership.
   * @param status optional status of that membership.
   * @param pageable the requested page (size capped at {@value #SEARCH_MAX_PAGE_SIZE}).
   * @return a slice of users with their memberships, ordered by last name.
   */
  @Transactional(readOnly = true)
  public Slice<UserResponse> searchUsers(
      String search, Long gymId, GymRole role, MembershipStatus status, Pageable pageable) {
    String text = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    Pageable page =
        PageRequest.of(
            pageable.getPageNumber(), Math.min(pageable.getPageSize(), SEARCH_MAX_PAGE_SIZE));
    boolean anyMembership = gymId == null && role == null && status == null;

    Slice<Long> ids =
        userRepository.searchIds("%" + escaped + "%", anyMembership, gymId, role, status, page);
    List<UserResponse> users = loadWithMemberships(ids.getContent());
    return new SliceImpl<>(users, page, ids.hasNext());
  }

  /**
   * Loads users with their memberships, keeping the order of the given ids.
   *
   * @param ids the user ids, in display order.
   * @return the users, in the same order (unknown ids are skipped).
   */
  @Transactional(readOnly = true)
  public List<UserResponse> loadWithMemberships(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, User> byId =
        userRepository.findWithMembershipsByIdIn(ids).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    return ids.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(userMapper::toUserResponse)
        .toList();
  }

  /**
   * Resolves user ids into lightweight summaries (e.g. WOD authors shown to platform admins).
   *
   * @param ids the user ids.
   * @return the summaries of the existing users, ordered by last name.
   */
  @Transactional(readOnly = true)
  public List<UserSummary> lookup(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return userRepository.findAllById(ids).stream()
        .sorted(
            Comparator.comparing(User::getLastName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(User::getFirstName, String.CASE_INSENSITIVE_ORDER))
        .map(userMapper::toSummary)
        .toList();
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
   * Updates the preferences (locale, theme, load unit) of a user.
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

  /**
   * Resolves the display names ("Clément R.") a user may see, e.g. for a leaderboard page. A name is
   * visible to its owner, to members of a gym the owner belongs to (usable memberships on both
   * sides), to everyone when the owner allowed it on public leaderboards, and to platform admins.
   * Other users are left out, so clients show them as anonymous.
   *
   * @param viewer the authenticated user.
   * @param userIds the users to resolve.
   * @return the visible display names, in no particular order.
   */
  @Transactional(readOnly = true)
  public List<DisplayNameResponse> getDisplayNames(User viewer, Collection<Long> userIds) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    boolean admin = viewer.getPlatformRole() == PlatformRole.ADMIN;
    Set<Long> viewerGyms =
        userRepository.findWithMembershipsByIdIn(List.of(viewer.getId())).stream()
            .flatMap(u -> usableGymIds(u).stream())
            .collect(Collectors.toSet());

    return userRepository.findWithMembershipsByIdIn(Set.copyOf(userIds)).stream()
        .filter(
            u ->
                admin
                    || Objects.equals(u.getId(), viewer.getId())
                    || u.isPublicLeaderboardName()
                    || usableGymIds(u).stream().anyMatch(viewerGyms::contains))
        .map(u -> new DisplayNameResponse(u.getId(), displayName(u)))
        .toList();
  }

  private static Set<Long> usableGymIds(User user) {
    return user.getMemberships().stream()
        .filter(Membership::isUsable)
        .map(m -> m.getGym().getId())
        .collect(Collectors.toSet());
  }

  /** First name and last-name initial: "Clément R.". */
  static String displayName(User user) {
    String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
    String last = user.getLastName() == null ? "" : user.getLastName().trim();
    return last.isEmpty() ? first : first + " " + last.substring(0, 1).toUpperCase(Locale.ROOT) + ".";
  }
}
