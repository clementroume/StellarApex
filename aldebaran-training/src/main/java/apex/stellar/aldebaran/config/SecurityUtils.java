package apex.stellar.aldebaran.config;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Security utilities for extracting authenticated user information.
 *
 * <p>Expects JWT tokens issued by Antares Auth with a "userId" claim.
 */
public class SecurityUtils {

  private SecurityUtils() {
    // Private constructor to prevent instantiation
  }

  /**
   * Retrieves the user ID of the currently authenticated user from the security context. This
   * method extracts the principal from the authentication object, assuming the user is
   * authenticated. If no authenticated user is found, an exception is thrown.
   *
   * @return the user ID of the currently authenticated user as a {@code String}.
   * @throws IllegalStateException if no authenticated user is present in the security context.
   */
  public static String getCurrentUserId() {
    return Optional.of(SecurityContextHolder.getContext())
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getPrincipal)
        .map(Object::toString)
        .orElseThrow(() -> new IllegalStateException("No authenticated user found"));
  }

  /**
   * Retrieves the user ID as a Long.
   *
   * @return the user ID.
   */
  public static Long getCurrentUserIdAsLong() {
    return Long.valueOf(getCurrentUserId());
  }

  /**
   * Checks if the given userId matches the current authenticated user.
   *
   * @param userId The userId to check
   * @return true if it matches the current user
   */
  public static boolean isCurrentUser(String userId) {
    return userId != null && userId.equals(getCurrentUserId());
  }
}
