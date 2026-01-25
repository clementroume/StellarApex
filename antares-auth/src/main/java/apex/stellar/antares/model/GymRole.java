package apex.stellar.antares.model;

/**
 * Enumeration representing the contextual roles assigned to users within a specific Gym.
 */
public enum GymRole {
  /** Grants full administrative rights over a specific Gym/Box. */
  OWNER,

  /** Equivalent to OWNER but specific to virtual programming tracks. */
  PROGRAMMER,

  /** Grants management permissions (e.g., WODs, Members) within a specific Gym. */
  COACH,

  /** Standard member role within a Gym. */
  ATHLETE
}