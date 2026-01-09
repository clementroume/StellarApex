package apex.stellar.antares.model;

/**
 * Enumeration representing specific permissions that can be assigned to a user within a gym
 * context.
 *
 * <p>These permissions are granular and used to control access to specific resources or actions in
 * downstream services like Aldebaran (Training).
 */
public enum Permission {
  WOD_WRITE,
  SCORE_VERIFY,
  MEMBER_READ,
  MEMBER_WRITE,
  GYM_SETTINGS
}
