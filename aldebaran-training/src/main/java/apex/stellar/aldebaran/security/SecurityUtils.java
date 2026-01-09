package apex.stellar.aldebaran.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for accessing security context information within the application.
 *
 * <p>This helper provides static accessors to retrieve the currently authenticated user's details
 * (ID, Gym, etc.) from the Spring Security context, avoiding repetitive casting and null checks in
 * the service layer.
 */
public final class SecurityUtils {

  private SecurityUtils() {
    // Private constructor to prevent instantiation of utility class
  }

  /**
   * Retrieves the ID of the currently authenticated user.
   *
   * <p>This method expects the security context to contain an {@link AldebaranUserPrincipal}.
   *
   * @return The unique identifier (ID) of the authenticated user.
   * @throws IllegalStateException If no user is authenticated or the principal type is invalid.
   */
  public static Long getCurrentUserId() {
    return getPrincipal()
        .map(AldebaranUserPrincipal::getId)
        .orElseThrow(
            () -> new IllegalStateException("Current user ID not found in security context"));
  }

  /**
   * Retrieves the Gym ID associated with the current user's context.
   *
   * <p>Useful for multi-tenant filtering in services.
   *
   * @return The Gym ID if present, or null if the user has no gym context (e.g., global admin or
   *     personal scope).
   */
  public static Long getCurrentGymId() {
    return getPrincipal().map(AldebaranUserPrincipal::getGymId).orElse(null);
  }

  /**
   * Internal helper to extract the {@link AldebaranUserPrincipal} safely.
   *
   * @return An Optional containing the principal if authentication is valid.
   */
  private static Optional<AldebaranUserPrincipal> getPrincipal() {
    return Optional.of(SecurityContextHolder.getContext())
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getPrincipal)
        .filter(AldebaranUserPrincipal.class::isInstance)
        .map(AldebaranUserPrincipal.class::cast);
  }
}
