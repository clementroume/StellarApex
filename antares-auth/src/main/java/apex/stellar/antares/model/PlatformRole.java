package apex.stellar.antares.model;

/**
 * Enumeration representing the global roles assigned to users within the platform.
 */
public enum PlatformRole {
  /** System-wide Administrator with full access to all tenants and configurations. */
  ADMIN,

  /** Standard user role. Default for new registrations. */
  USER
}