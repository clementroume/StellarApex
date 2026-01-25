package apex.stellar.antares.model;

/**
 * Enumeration of granular permissions that can be assigned to a user within a specific Gym context.
 *
 * <p>These permissions allow for fine-grained access control (RBAC) beyond standard roles. They are
 * typically verified via the {@code AntaresSecurity} service or passed to downstream services.
 */
public enum Permission {
  /** Allows creating, editing, and deleting Workouts of the Day (WODs). Used in Aldebaran. */
  WOD_WRITE,

  /** Allows verifying and validating athlete scores. Used in Aldebaran. */
  SCORE_VERIFY,

  /**
   * Allows advanced membership management (e.g., validating pending members). Typically assigned to
   * Coaches by Owners.
   */
  MANAGE_MEMBERSHIPS,
  /**
   * Allows managing gym configuration settings such as enrollment codes and auto-subscription
   * rules. Typically assigned to Owners.
   */
  MANAGE_SETTINGS
}
