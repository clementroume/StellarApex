package apex.stellar.aldebaran.security;

import java.util.Set;

/**
 * Access held by a user in one gym, as forwarded by Antares in {@code X-Auth-Memberships}.
 *
 * @param role The role in the gym (OWNER, PROGRAMMER, COACH, ATHLETE).
 * @param permissions The effective permissions in the gym, already normalised by Antares
 *     (OWNER/PROGRAMMER hold all of them, COACH its assigned set, ATHLETE none).
 */
public record GymAccess(String role, Set<String> permissions) {

  /** Defensive copy, never null. */
  public GymAccess {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  /**
   * Checks if this access grants the given permission.
   *
   * @param permission The permission to check (e.g., WOD_WRITE).
   * @return true if granted.
   */
  public boolean hasPermission(String permission) {
    return permissions.contains(permission);
  }
}
