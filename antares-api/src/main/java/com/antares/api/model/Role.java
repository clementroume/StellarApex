package com.antares.api.model;

/**
 * Enumeration representing the roles that can be assigned to a user.
 *
 * <p>Each role defines a set of permissions and access levels within the application. The roles are
 * used to control authentication and authorization processes, determining actions and resources a
 * user can access.
 *
 * <ul>
 *   <li>ROLE_USER: Represents a standard user role with basic access permissions.
 *   <li>ROLE_ADMIN: Represents an administrative user role with elevated access permissions.
 * </ul>
 */
public enum Role {
  ROLE_USER,
  ROLE_ADMIN
}
