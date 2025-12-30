package apex.stellar.aldebaran.config;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
    return Optional.of(SecurityContextHolder.getContext())
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(
            authentication -> {
              Object principal = authentication.getPrincipal();

              // Cas 1: JWT (si on gardait une config hybride)
              if (principal instanceof Jwt jwt) {
                return jwt.getSubject();
              }
              // Cas 2: UserDetails (Standard Spring Security / MockUser)
              else if (principal instanceof UserDetails userDetails) {
                return userDetails.getUsername();
              }
              // Cas 3: String (Pre-Authenticated Header / MockUser simple)
              else if (principal instanceof String principalName) {
                return principalName;
              }

              // Fallback standard
              return authentication.getName();
            })
        .orElseThrow(() -> new IllegalStateException("Utilisateur non authentifié"));
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

  /**
   * Checks if a user is authenticated.
   *
   * @return true if authenticated, false otherwise
   */
  public static boolean isAuthenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
  }
}
