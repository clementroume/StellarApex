package atlas.stellar.antares.model;

/**
 * Enumeration representing the roles that can be assigned to a user.
 *
 * <p>These roles are used by Spring Security to control authorization and access levels within the
 * application.
 *
 * <ul>
 *   <li>ROLE_USER: Standard user with basic application access.
 *   <li>ROLE_ADMIN: Administrator with elevated permissions.
 * </ul>
 */
public enum Role {
  ROLE_USER,
  ROLE_ADMIN
}
