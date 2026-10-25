package apex.stellar.aldebaran.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Custom Principal implementation representing the authenticated user within the Aldebaran context.
 *
 * <p>This class adapts the authentication data transmitted via HTTP headers (X-Auth-*) by the
 * Antares Identity Provider (via Traefik) into a Spring Security compatible principal.
 *
 * <p>It bridges the gap between the infrastructure authentication (Gateway) and the microservice
 * authorization logic. The platform role (ADMIN/USER) is kept apart from the per-gym memberships
 * (role and effective permissions in each gym the user belongs to).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AldebaranUserPrincipal implements UserDetails {

  /** The unique identifier of the user (Subject). */
  private Long id;

  /** The user's platform-wide role (ADMIN or USER), independent of any gym. */
  private String platformRole;

  /**
   * Usable memberships, keyed by gym ID. Rights are evaluated on the gym owning each resource, not
   * on a single "current" gym.
   */
  @Builder.Default private Map<Long, GymAccess> memberships = Map.of();

  /**
   * The platform admin acting as this user through an audited support session ({@code
   * X-Auth-Actor-Id}), or {@code null} for a normal request. Rights stay those of the user.
   */
  @Nullable private Long actorId;

  /**
   * Returns every gym the user can read from.
   *
   * @return The member gym IDs (never null).
   */
  public Set<Long> getMemberGymIds() {
    return memberships == null ? Set.of() : memberships.keySet();
  }

  /**
   * Checks if the user is a member of the given gym.
   *
   * @param gymId The gym owning the resource.
   * @return true if the user holds a usable membership in that gym.
   */
  public boolean isMemberOf(Long gymId) {
    return gymId != null && memberships != null && memberships.containsKey(gymId);
  }

  /**
   * Checks if the user holds a permission in the given gym.
   *
   * @param gymId The gym owning the resource.
   * @param permission The permission to verify (e.g., "WOD_WRITE").
   * @return true if the user's membership in that gym grants the permission.
   */
  public boolean hasGymPermission(Long gymId, String permission) {
    if (gymId == null || memberships == null) {
      return false;
    }
    GymAccess access = memberships.get(gymId);
    return access != null && access.hasPermission(permission);
  }

  // -------------------------------------------------------------------------
  // UserDetails Implementation
  // -------------------------------------------------------------------------

  /**
   * Returns the authorities granted to the user, derived from the platform role only (so that
   * {@code hasRole('ADMIN')} never depends on the gym context). Adds the 'ROLE_' prefix to comply
   * with Spring Security defaults.
   */
  @Override
  @NonNull
  public Collection<? extends GrantedAuthority> getAuthorities() {
    if (platformRole == null) {
      return Collections.emptyList();
    }
    // Defensive check: avoid double prefixing if upstream changes
    String authority = platformRole.startsWith("ROLE_") ? platformRole : "ROLE_" + platformRole;
    return List.of(new SimpleGrantedAuthority(authority));
  }

  /**
   * Returns the password used to authenticate the user.
   *
   * @return null, as authentication is offloaded to the Gateway/IdP and no password is required
   *     within the microservice context.
   */
  @Override
  @Nullable
  public String getPassword() {
    return null;
  }

  /**
   * Returns the username used to authenticate the user.
   *
   * <p>In this architecture, the User ID (Long) serves as the unique principal identifier.
   *
   * @return The string representation of the User ID. Never returns null.
   */
  @Override
  @NonNull
  public String getUsername() {
    return id != null ? String.valueOf(id) : "";
  }
}
