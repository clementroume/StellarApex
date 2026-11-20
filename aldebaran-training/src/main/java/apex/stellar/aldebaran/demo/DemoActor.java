package apex.stellar.aldebaran.demo;

import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.GymAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Lets the demo seeders write through the services as a demo account (decision of 2026-11-19): the
 * seeding thread holds that account's identity while one call runs, built from the Antares tables
 * the way Antares builds the gateway headers (usable memberships, effective permissions, division,
 * birth year). In memory only, on the seeder's own thread at start-up, under the {@code demo}
 * profile: nothing reachable over HTTP. Rights are the account's own; the seeders only do what that
 * account may do in the app.
 */
@Component
@Profile("demo")
class DemoActor {

  private static final Set<String> ALL_PERMISSIONS =
      Set.of("WOD_WRITE", "SCORE_VERIFY", "MANAGE_MEMBERSHIPS", "MANAGE_SETTINGS");

  private final JdbcTemplate jdbcTemplate;
  private final Map<Long, AldebaranUserPrincipal> principals = new HashMap<>();

  DemoActor(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * The identity of a demo account, as Antares sends it.
   *
   * @param userId The account.
   * @return Its principal.
   */
  AldebaranUserPrincipal principal(Long userId) {
    return principals.computeIfAbsent(userId, this::load);
  }

  /**
   * Runs one call as a demo account, then restores the thread's previous security context.
   *
   * @param userId The account.
   * @param call The call.
   * @param <T> Its result.
   * @return Its result.
   */
  <T> T as(Long userId, Supplier<T> call) {
    AldebaranUserPrincipal principal = principal(userId);
    SecurityContext previous = SecurityContextHolder.getContext();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    SecurityContextHolder.setContext(context);
    try {
      return call.get();
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }

  /** Same as {@link #as(Long, Supplier)} for a call without result. */
  void run(Long userId, Runnable call) {
    as(
        userId,
        () -> {
          call.run();
          return null;
        });
  }

  private AldebaranUserPrincipal load(Long userId) {
    Map<String, Object> user =
        jdbcTemplate.queryForMap(
            "SELECT platform_role, division, birth_year FROM antares.users WHERE id = ?", userId);
    Map<Long, GymAccess> memberships = new HashMap<>();
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT m.id, m.gym_id, m.gym_role, g.status AS gym_status FROM antares.memberships m"
                + " JOIN antares.gyms g ON g.id = m.gym_id WHERE m.user_id = ? AND m.status = 'ACTIVE'",
            userId);
    for (Map<String, Object> row : rows) {
      String role = (String) row.get("gym_role");
      String gymStatus = (String) row.get("gym_status");
      boolean holder = "OWNER".equals(role) || "PROGRAMMER".equals(role);
      // Usable: an active gym, or a gym waiting for approval for its holder (as in Antares)
      if (!"ACTIVE".equals(gymStatus) && !("PENDING_APPROVAL".equals(gymStatus) && holder)) {
        continue;
      }
      Set<String> permissions =
          switch (role) {
            case "OWNER", "PROGRAMMER" -> ALL_PERMISSIONS;
            case "COACH" ->
                Set.copyOf(
                    jdbcTemplate.queryForList(
                        "SELECT permission FROM antares.membership_permissions WHERE membership_id = ?",
                        String.class,
                        row.get("id")));
            default -> Set.of();
          };
      memberships.put(((Number) row.get("gym_id")).longValue(), new GymAccess(role, permissions));
    }
    Object division = user.get("division");
    Object birthYear = user.get("birth_year");
    return AldebaranUserPrincipal.builder()
        .id(userId)
        .platformRole((String) user.get("platform_role"))
        .memberships(memberships)
        .division(division == null ? null : Division.valueOf((String) division))
        .birthYear(birthYear == null ? null : ((Number) birthYear).intValue())
        .build();
  }
}
