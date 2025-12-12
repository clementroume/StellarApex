package apex.stellar.aldebaran.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

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
   * Extracts the current authenticated user's ID from the JWT.
   *
   * @return The userId claim from the JWT
   * @throws IllegalStateException if no user is authenticated
   */
  public static String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      return jwt.getSubject();
    }

    throw new IllegalStateException("Utilisateur non authentifié");
  }

  /**
   * Checks if the given userId matches the current authenticated user.
   *
   * @param userId The userId to check
   * @return true if it matches the current user
   */
  public static boolean isCurrentUser(String userId) {
    try {
      return getCurrentUserId().equals(userId);
    } catch (IllegalStateException e) {
      return false;
    }
  }

  /**
   * Checks if a user is authenticated.
   *
   * @return true if authenticated, false otherwise
   */
  public static boolean isAuthenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt;
  }
}
