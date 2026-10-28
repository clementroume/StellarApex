package apex.stellar.aldebaran.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apex.stellar.aldebaran.config.SecurityConfig.ForwardedAuthFilter;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.GymAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit tests for the forwarded identity header parsing of {@link ForwardedAuthFilter}. */
class ForwardedAuthFilterTest {

  private static final String SECRET = "secret";

  private final ForwardedAuthFilter filter = new ForwardedAuthFilter(SECRET);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private AldebaranUserPrincipal filterAndGetPrincipal(MockHttpServletRequest request)
      throws Exception {
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null ? null : assertInstanceOf(AldebaranUserPrincipal.class, auth.getPrincipal());
  }

  private static MockHttpServletRequest baseRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/aldebaran/wods");
    request.addHeader("X-Internal-Secret", SECRET);
    request.addHeader("X-Auth-User-Id", "42");
    return request;
  }

  @Test
  @DisplayName("Parses platform role, memberships with effective permissions and support actor")
  void shouldParseAllHeaders() throws Exception {
    MockHttpServletRequest request = baseRequest();
    request.addHeader("X-Auth-User-Role", "ADMIN");
    request.addHeader("X-Auth-Memberships", "12:COACH:WOD_WRITE+SCORE_VERIFY, 15:ATHLETE:");
    request.addHeader("X-Auth-Actor-Id", " 1 ");

    AldebaranUserPrincipal principal = filterAndGetPrincipal(request);

    assertEquals(42L, principal.getId());
    assertEquals("ADMIN", principal.getPlatformRole());
    assertEquals(
        Map.of(
            12L, new GymAccess("COACH", Set.of("WOD_WRITE", "SCORE_VERIFY")),
            15L, new GymAccess("ATHLETE", Set.of())),
        principal.getMemberships());
    assertEquals(Set.of(12L, 15L), principal.getMemberGymIds());
    assertTrue(principal.hasGymPermission(12L, "WOD_WRITE"));
    assertFalse(principal.hasGymPermission(15L, "WOD_WRITE"));
    assertEquals(1L, principal.getActorId());
    // Authorities only reflect the platform role
    assertEquals(
        List.of("ROLE_ADMIN"),
        principal.getAuthorities().stream().map(a -> a.getAuthority()).toList());
  }

  @Test
  @DisplayName("Without memberships: empty map, no actor")
  void shouldParseMinimalHeaders() throws Exception {
    MockHttpServletRequest request = baseRequest();
    request.addHeader("X-Auth-User-Role", "USER");
    request.addHeader("X-Auth-Memberships", "");

    AldebaranUserPrincipal principal = filterAndGetPrincipal(request);

    assertTrue(principal.getMemberships().isEmpty());
    assertNull(principal.getActorId());
    assertEquals(
        List.of("ROLE_USER"),
        principal.getAuthorities().stream().map(a -> a.getAuthority()).toList());
  }

  @Test
  @DisplayName("Malformed membership entries are skipped, valid ones kept")
  void shouldSkipMalformedMemberships() throws Exception {
    MockHttpServletRequest request = baseRequest();
    request.addHeader("X-Auth-User-Role", "USER");
    request.addHeader(
        "X-Auth-Memberships", "abc:COACH:WOD_WRITE,13,14::WOD_WRITE,,15:OWNER:WOD_WRITE+ +");

    AldebaranUserPrincipal principal = filterAndGetPrincipal(request);

    assertEquals(
        Map.of(15L, new GymAccess("OWNER", Set.of("WOD_WRITE"))), principal.getMemberships());
  }

  @Test
  @DisplayName("Malformed user ID leaves the request unauthenticated")
  void shouldNotAuthenticateOnMalformedUserId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/aldebaran/wods");
    request.addHeader("X-Internal-Secret", SECRET);
    request.addHeader("X-Auth-User-Id", "not-a-number");
    request.addHeader("X-Auth-User-Role", "USER");

    assertNull(filterAndGetPrincipal(request));
  }

  @Test
  @DisplayName("Missing platform role leaves the request unauthenticated")
  void shouldNotAuthenticateWithoutPlatformRole() throws Exception {
    assertNull(filterAndGetPrincipal(baseRequest()));
  }

  @Test
  @DisplayName("Malformed actor ID leaves the request unauthenticated")
  void shouldNotAuthenticateOnMalformedActorId() throws Exception {
    MockHttpServletRequest request = baseRequest();
    request.addHeader("X-Auth-User-Role", "USER");
    request.addHeader("X-Auth-Actor-Id", "admin");

    assertNull(filterAndGetPrincipal(request));
  }

  @Test
  @DisplayName("Division header: MEN / WOMEN parsed, blank or unknown values ignored")
  void shouldParseDivision() throws Exception {
    MockHttpServletRequest women = baseRequest();
    women.addHeader("X-Auth-User-Role", "USER");
    women.addHeader("X-Auth-User-Division", "WOMEN");
    assertEquals(Division.WOMEN, filterAndGetPrincipal(women).getDivision());

    for (String value : new String[] {"", "OTHER", "men"}) {
      SecurityContextHolder.clearContext();
      MockHttpServletRequest request = baseRequest();
      request.addHeader("X-Auth-User-Role", "USER");
      request.addHeader("X-Auth-User-Division", value);
      assertNull(filterAndGetPrincipal(request).getDivision(), value);
    }
  }
}
