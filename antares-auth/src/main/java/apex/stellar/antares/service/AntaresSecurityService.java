package apex.stellar.antares.service;

import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security Service used for Method-Level Security (Expression-Based Access Control).
 *
 * <p>Registered as a bean named "sec", it allows SpEL usage in annotations:
 *
 * <pre>
 * &#64;PreAuthorize("@sec.hasGymPermission(#gymId, 'MANAGE_MEMBERSHIPS')")
 * </pre>
 */
@Slf4j
@Service("sec")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AntaresSecurityService {

  private final MembershipRepository membershipRepository;

  /**
   * Checks if the current user has a specific permission within a Gym context.
   *
   * <p><b>Privilege Logic:</b>
   *
   * <ul>
   *   <li>Global Admins always return {@code true}.
   *   <li><b>Gym Owners and Programmers</b> always return {@code true} (Implicit super-admin of the
   *       gym).
   *   <li>Other roles (e.g., Coaches) must have the explicit permission in their list.
   * </ul>
   *
   * @param gymId The ID of the gym context.
   * @param permissionName The required permission name (case-insensitive string matching {@link
   *     Permission}).
   * @return {@code true} if access is granted.
   */
  public boolean hasGymPermission(Long gymId, String permissionName) {
    User user = getCurrentUser();
    if (user == null) {
      return false;
    }

    // 1. Global Admin Override
    if (user.getPlatformRole() == PlatformRole.ADMIN) {
      return true;
    }

    // 2. Resolve Permission Enum safely
    Permission permission = resolvePermission(permissionName);
    if (permission == null) {
      return false;
    }

    // 3. Check Contextual Access
    return membershipRepository
        .findByUserIdAndGymId(user.getId(), gymId)
        .map(membership -> hasAccess(membership, permission))
        .orElse(false);
  }

  /**
   * Checks if the current user has permission to manage a specific target membership.
   *
   * <p>This resolves the Gym ID from the target membership and delegates to {@link
   * #hasGymPermission}.
   *
   * @param targetMembershipId The ID of the membership being acted upon.
   * @param permissionName The required permission.
   * @return {@code true} if the user has rights in the Gym to which the target belongs.
   */
  public boolean canManageMembership(Long targetMembershipId, String permissionName) {
    User user = getCurrentUser();
    if (user == null) {
      return false;
    }
    if (user.getPlatformRole() == PlatformRole.ADMIN) {
      return true;
    }

    return membershipRepository
        .findById(targetMembershipId)
        .map(target -> hasGymPermission(target.getGym().getId(), permissionName))
        .orElse(false);
  }

  // --- Core Logic ---

  /** Evaluates if a membership grants the required permission. */
  private boolean hasAccess(Membership membership, Permission requiredPermission) {
    GymRole role = membership.getGymRole();

    // Implicit "VIP" Pass for Tenant Administrators (Owner AND Programmer)
    if (role == GymRole.OWNER || role == GymRole.PROGRAMMER) {
      return true;
    }

    // Granular check for delegated roles (e.g., Coach)
    return membership.getPermissions().contains(requiredPermission);
  }

  /** Safely retrieves the authenticated User from the SecurityContext. */
  private User getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
      return user;
    }
    log.warn("Security Check failed: No valid authenticated User found in context.");
    return null;
  }

  /** Helper to convert String to Permission Enum without throwing exceptions. */
  private Permission resolvePermission(String permissionName) {
    try {
      return Permission.valueOf(permissionName);
    } catch (IllegalArgumentException | NullPointerException e) {
      log.error("Security Check failed: Invalid permission requested '{}'", permissionName, e);
      return null;
    }
  }
}
