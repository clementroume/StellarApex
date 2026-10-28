package apex.stellar.aldebaran.security;

import apex.stellar.aldebaran.model.enums.Division;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.Set;
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

  /** Permission to create, update and delete the WODs of a gym. */
  public static final String WOD_WRITE = "WOD_WRITE";

  /** Permission to log and correct scores of other athletes in a gym. */
  public static final String SCORE_VERIFY = "SCORE_VERIFY";

  private static final String ADMIN = "ADMIN";

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

  /**
   * Retrieves the division of the currently authenticated user ({@code X-Auth-User-Division}).
   *
   * @return The division, or {@code null} when not chosen or not authenticated.
   */
  public @Nullable Division getCurrentUserDivision() {
    return getPrincipal().map(AldebaranUserPrincipal::getDivision).orElse(null);
  }

  /**
   * Checks if the user is a Platform Administrator. Relies on the platform role only, so an admin
   * operating inside a gym context stays admin.
   */
  public boolean isAdmin(AldebaranUserPrincipal principal) {
    return principal != null && ADMIN.equals(principal.getPlatformRole());
  }

  /**
   * Safe check if the current authenticated user is an Admin. Used specifically for SpEL queries.
   */
  @SuppressWarnings("unused")
  public boolean isCurrentUserAdmin() {
    return getPrincipal().map(this::isAdmin).orElse(false);
  }

  /**
   * Retrieves every gym the current user can read from.
   *
   * @return The member gym IDs, or {@code {-1}} when there are none so that a JPQL {@code IN}
   *     clause stays valid.
   */
  public Set<Long> getCurrentUserMemberGymIds() {
    return getPrincipal()
        .map(AldebaranUserPrincipal::getMemberGymIds)
        .filter(ids -> !ids.isEmpty())
        .orElse(Set.of(-1L));
  }

  /**
   * Checks if the user holds a permission in the given gym (the gym owning the resource). Relies
   * on the effective permissions sent by Antares (Owners/Programmers hold them all, Coaches their
   * assigned set, Athletes none).
   *
   * @param principal The authenticated user.
   * @param gymId The gym owning the resource.
   * @param permission The permission to check (e.g., WOD_WRITE, SCORE_VERIFY).
   * @return true if the user's membership in that gym grants the permission.
   */
  public boolean hasGymPermission(AldebaranUserPrincipal principal, Long gymId, String permission) {
    return principal != null && principal.hasGymPermission(gymId, permission);
  }

  // --- Internal Helpers ---

  private Optional<AldebaranUserPrincipal> getPrincipal() {
    return Optional.of(SecurityContextHolder.getContext())
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getPrincipal)
        .filter(AldebaranUserPrincipal.class::isInstance)
        .map(AldebaranUserPrincipal.class::cast);
  }
}
