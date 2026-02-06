package apex.stellar.aldebaran.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
 * authorization logic, holding tenant-specific context (Gym ID) and RBAC permissions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AldebaranUserPrincipal implements UserDetails {

  /** The unique identifier of the user (Subject). */
  private Long id;

  /** The ID of the gym context the user is currently operating in (Tenant). */
  private Long gymId;

  /** The user's role within the current gym context (e.g., ROLE_COACH). */
  private String role;

  /** Granular permissions assigned to the user (e.g., WOD_WRITE). */
  private List<String> permissions;

  /**
   * Checks if the user holds a specific granular permission in the current context.
   *
   * @param permission The permission string to verify (e.g., "WOD_WRITE").
   * @return true if the permission is present in the principal's authority set.
   */
  public boolean hasPermission(String permission) {
    return permissions != null && permissions.contains(permission);
  }

  // -------------------------------------------------------------------------
  // UserDetails Implementation
  // -------------------------------------------------------------------------

  /**
   * Returns the authorities granted to the user. Adds the 'ROLE_' prefix to the raw role string to
   * comply with Spring Security defaults.
   */
  @Override
  @NonNull
  public Collection<? extends GrantedAuthority> getAuthorities() {
    if (role == null) {
      return Collections.emptyList();
    }
    // Defensive check: avoid double prefixing if upstream changes
    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
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
