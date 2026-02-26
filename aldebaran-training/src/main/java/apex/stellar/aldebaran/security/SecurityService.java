package apex.stellar.aldebaran.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service for accessing security context information and evaluating common role-based policies.
 *
 * <p>This bean provides accessors to retrieve the currently authenticated user's details and
 * encapsulates reusable authorization logic (e.g., checking for Admin or Staff privileges).
 */
@Service
public class SecurityService {

  private static final String ADMIN = "ADMIN";
  private static final String OWNER = "OWNER";
  private static final String PROGRAMMER = "PROGRAMMER";
  private static final String COACH = "COACH";
  private static final String SCORE_VERIFY = "SCORE_VERIFY";
  private static final String WOD_WRITE = "WOD_WRITE";

  /**
   * Retrieves the ID of the currently authenticated user.
   *
   * @return The unique identifier (ID) of the authenticated user.
   * @throws IllegalStateException If no user is authenticated.
   */
  public Long getCurrentUserId() {
    return getPrincipal()
        .map(AldebaranUserPrincipal::getId)
        .orElseThrow(
            () -> new IllegalStateException("Current user ID not found in security context"));
  }

  /** Checks if the current user is a Platform Administrator. */
  public boolean isAdmin(AldebaranUserPrincipal principal) {
    return principal != null && ADMIN.equals(principal.getRole());
  }

  /**
   * Retrieves the Gym ID associated with the currently authenticated user.
   *
   * @return The unique identifier (ID) of the gym associated with the authenticated user, or {@code
   *     null} if no gym ID is available in the security context.
   */
  @SuppressWarnings("unused")
  public Long getCurrentUserGymId() {
    return getPrincipal().map(AldebaranUserPrincipal::getGymId).orElse(null);
  }

  /**
   * Safe check if the current authenticated user is an Admin. Used specifically for SpEL queries.
   */
  @SuppressWarnings("unused")
  public boolean isCurrentUserAdmin() {
    return getPrincipal().map(p -> ADMIN.equals(p.getRole())).orElse(false);
  }

  /**
   * Checks if the user has write access to WODs within their Gym. Valid for: Owners, Programmers,
   * and Coaches with specific permission.
   */
  public boolean hasWodWriteAccess(AldebaranUserPrincipal principal) {
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
  public boolean hasScoreVerificationRights(AldebaranUserPrincipal principal) {
    if (principal.getGymId() == null) {
      return false;
    }
    return isOwnerOrProgrammer(principal)
        || (isCoach(principal) && principal.hasPermission(SCORE_VERIFY));
  }

  // --- Internal Role Helpers ---

  private boolean isOwnerOrProgrammer(AldebaranUserPrincipal principal) {
    String role = principal.getRole();
    return OWNER.equals(role) || PROGRAMMER.equals(role);
  }

  private boolean isCoach(AldebaranUserPrincipal principal) {
    return COACH.equals(principal.getRole());
  }

  private Optional<AldebaranUserPrincipal> getPrincipal() {
    return Optional.of(SecurityContextHolder.getContext())
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getPrincipal)
        .filter(AldebaranUserPrincipal.class::isInstance)
        .map(AldebaranUserPrincipal.class::cast);
  }
}
