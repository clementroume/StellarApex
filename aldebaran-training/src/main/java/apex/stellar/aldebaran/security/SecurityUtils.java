package apex.stellar.aldebaran.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for accessing security context information and evaluating common role-based
 * policies.
 *
 * <p>This helper provides static accessors to retrieve the currently authenticated user's details
 * and encapsulates reusable authorization logic (e.g., checking for Admin or Staff privileges).
 */
public final class SecurityUtils {

  private static final String ADMIN = "ADMIN";
  private static final String OWNER = "OWNER";
  private static final String PROGRAMMER = "PROGRAMMER";
  private static final String COACH = "COACH";
  private static final String SCORE_VERIFY = "SCORE_VERIFY";
  private static final String WOD_WRITE = "WOD_WRITE";

  private SecurityUtils() {
    // Private constructor to prevent instantiation
  }

  /**
   * Retrieves the ID of the currently authenticated user.
   *
   * @return The unique identifier (ID) of the authenticated user.
   * @throws IllegalStateException If no user is authenticated.
   */
  public static Long getCurrentUserId() {
    return getPrincipal()
        .map(AldebaranUserPrincipal::getId)
        .orElseThrow(
            () -> new IllegalStateException("Current user ID not found in security context"));
  }

  /** Checks if the current user is a Platform Administrator. */
  public static boolean isAdmin(AldebaranUserPrincipal principal) {
    return principal != null && ADMIN.equals(principal.getRole());
  }

  /**
   * Checks if the user has write access to WODs within their Gym. Valid for: Owners, Programmers,
   * and Coaches with specific permission.
   */
  public static boolean hasWodWriteAccess(AldebaranUserPrincipal principal) {
    if (principal.getGymId() == null) {
      return false;
    }
    return isOwnerOrProgrammer(principal)
        || (isCoach(principal) && principal.hasPermission(WOD_WRITE));
  }

  /**
   * Checks if the user has authority to verify/modify scores of other athletes. Valid for: Owners,
   * Programmers, and Coaches with specific permission.
   */
  public static boolean hasScoreVerificationRights(AldebaranUserPrincipal principal) {
    if (principal.getGymId() == null) {
      return false;
    }
    return isOwnerOrProgrammer(principal)
        || (isCoach(principal) && principal.hasPermission(SCORE_VERIFY));
  }

  // --- Internal Role Helpers ---

  private static boolean isOwnerOrProgrammer(AldebaranUserPrincipal principal) {
    String role = principal.getRole();
    return OWNER.equals(role) || PROGRAMMER.equals(role);
  }

  private static boolean isCoach(AldebaranUserPrincipal principal) {
    return COACH.equals(principal.getRole());
  }

  private static Optional<AldebaranUserPrincipal> getPrincipal() {
    return Optional.of(SecurityContextHolder.getContext())
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getPrincipal)
        .filter(AldebaranUserPrincipal.class::isInstance)
        .map(AldebaranUserPrincipal.class::cast);
  }
}
