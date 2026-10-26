package apex.stellar.aldebaran.security;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Factory of {@link AldebaranUserPrincipal} instances for unit tests. */
final class TestPrincipals {

  private static final Set<String> ALL_PERMISSIONS = Set.of("WOD_WRITE", "SCORE_VERIFY");

  private TestPrincipals() {}

  /**
   * Builds a principal the way the forwarded headers would. ADMIN has no membership. Any other role
   * gets a single membership in {@code gymId} (USER meaning ATHLETE), with permissions normalised as
   * Antares does: OWNER/PROGRAMMER hold them all, others the given set.
   */
  static AldebaranUserPrincipal of(Long id, Long gymId, String role, List<String> permissions) {
    if ("ADMIN".equals(role)) {
      return AldebaranUserPrincipal.builder().id(id).platformRole("ADMIN").build();
    }
    String gymRole = "USER".equals(role) ? "ATHLETE" : role;
    Set<String> effective =
        "OWNER".equals(gymRole) || "PROGRAMMER".equals(gymRole)
            ? ALL_PERMISSIONS
            : Set.copyOf(permissions);
    return withMemberships(
        id, gymId != null ? Map.of(gymId, new GymAccess(gymRole, effective)) : Map.of());
  }

  /** Builds a USER principal holding the given memberships. */
  static AldebaranUserPrincipal withMemberships(Long id, Map<Long, GymAccess> memberships) {
    return AldebaranUserPrincipal.builder()
        .id(id)
        .platformRole("USER")
        .memberships(memberships)
        .build();
  }
}
