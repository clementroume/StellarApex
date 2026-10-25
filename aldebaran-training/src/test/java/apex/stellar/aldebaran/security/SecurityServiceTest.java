package apex.stellar.aldebaran.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

  @InjectMocks private SecurityService securityService;

  private static AldebaranUserPrincipal principal(
      String platformRole, Long gymId, String gymRole, List<String> permissions) {
    return AldebaranUserPrincipal.builder()
        .id(1L)
        .platformRole(platformRole)
        .memberships(
            gymId != null
                ? Map.of(gymId, new GymAccess(gymRole, Set.copyOf(permissions)))
                : Map.of())
        .build();
  }

  private static SecurityContext contextWith(AldebaranUserPrincipal principal) {
    Authentication auth = mock(Authentication.class);
    SecurityContext context = mock(SecurityContext.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getPrincipal()).thenReturn(principal);
    when(context.getAuthentication()).thenReturn(auth);
    return context;
  }

  @Test
  @DisplayName("getCurrentUserId: should return ID from Principal")
  void testGetCurrentUserId_Success() {
    AldebaranUserPrincipal principal =
        AldebaranUserPrincipal.builder().id(123L).platformRole("USER").build();
    SecurityContext context = contextWith(principal);

    try (MockedStatic<SecurityContextHolder> mockedHolder =
        mockStatic(SecurityContextHolder.class)) {
      mockedHolder.when(SecurityContextHolder::getContext).thenReturn(context);

      Long userId = securityService.getCurrentUserId();
      assertEquals(123L, userId);
    }
  }

  @Test
  @DisplayName("getCurrentUserId: should throw exception if no authentication")
  void testGetCurrentUserId_NoAuth() {
    SecurityContext context = mock(SecurityContext.class);
    when(context.getAuthentication()).thenReturn(null);

    try (MockedStatic<SecurityContextHolder> mockedHolder =
        mockStatic(SecurityContextHolder.class)) {
      mockedHolder.when(SecurityContextHolder::getContext).thenReturn(context);

      assertThrows(IllegalStateException.class, () -> securityService.getCurrentUserId());
    }
  }

  @Test
  @DisplayName("isAdmin: should return true for ADMIN platform role")
  void testIsAdmin_True() {
    assertTrue(securityService.isAdmin(principal("ADMIN", null, null, List.of())));
  }

  @Test
  @DisplayName("isAdmin: admin who is also a gym member stays admin")
  void testIsAdmin_InGymContext_StaysAdmin() {
    AldebaranUserPrincipal admin = principal("ADMIN", 101L, "ATHLETE", List.of());

    assertTrue(securityService.isAdmin(admin));
    assertTrue(
        admin.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
  }

  @Test
  @DisplayName("isAdmin: should return false for USER platform role, whatever the gym role")
  void testIsAdmin_False() {
    assertFalse(securityService.isAdmin(principal("USER", null, null, List.of())));
    assertFalse(securityService.isAdmin(principal("USER", 101L, "OWNER", List.of())));
  }

  @Test
  @DisplayName("isCurrentUserAdmin: reads the platform role of the current principal")
  void testIsCurrentUserAdmin() {
    SecurityContext context = contextWith(principal("ADMIN", 101L, "COACH", List.of()));

    try (MockedStatic<SecurityContextHolder> mockedHolder =
        mockStatic(SecurityContextHolder.class)) {
      mockedHolder.when(SecurityContextHolder::getContext).thenReturn(context);

      assertTrue(securityService.isCurrentUserAdmin());
    }
  }

  @Test
  @DisplayName("getCurrentUserMemberGymIds: returns member gyms of the principal")
  void testGetCurrentUserMemberGymIds() {
    AldebaranUserPrincipal principal =
        AldebaranUserPrincipal.builder()
            .id(1L)
            .platformRole("USER")
            .memberships(
                Map.of(
                    101L, new GymAccess("ATHLETE", Set.of()),
                    202L, new GymAccess("COACH", Set.of("WOD_WRITE"))))
            .build();
    SecurityContext context = contextWith(principal);

    try (MockedStatic<SecurityContextHolder> mockedHolder =
        mockStatic(SecurityContextHolder.class)) {
      mockedHolder.when(SecurityContextHolder::getContext).thenReturn(context);

      assertEquals(Set.of(101L, 202L), securityService.getCurrentUserMemberGymIds());
    }
  }

  @Test
  @DisplayName("getCurrentUserMemberGymIds: returns {-1} sentinel when there are none")
  void testGetCurrentUserMemberGymIds_Empty() {
    SecurityContext context = contextWith(principal("USER", null, null, List.of()));

    try (MockedStatic<SecurityContextHolder> mockedHolder =
        mockStatic(SecurityContextHolder.class)) {
      mockedHolder.when(SecurityContextHolder::getContext).thenReturn(context);

      assertEquals(Set.of(-1L), securityService.getCurrentUserMemberGymIds());
    }
  }

  @Test
  @DisplayName("hasGymPermission: OWNER (all permissions) has WOD_WRITE in their gym")
  void testHasGymPermission_Owner() {
    AldebaranUserPrincipal owner =
        principal("USER", 101L, "OWNER", List.of("WOD_WRITE", "SCORE_VERIFY"));
    assertTrue(securityService.hasGymPermission(owner, 101L, "WOD_WRITE"));
    assertTrue(securityService.hasGymPermission(owner, 101L, "SCORE_VERIFY"));
  }

  @Test
  @DisplayName("hasGymPermission: COACH with WOD_WRITE in gym A but not in gym B")
  void testHasGymPermission_CoachPerGym() {
    AldebaranUserPrincipal coach =
        AldebaranUserPrincipal.builder()
            .id(1L)
            .platformRole("USER")
            .memberships(
                Map.of(
                    101L, new GymAccess("COACH", Set.of("WOD_WRITE")),
                    202L, new GymAccess("COACH", Set.of("SCORE_VERIFY"))))
            .build();

    assertTrue(securityService.hasGymPermission(coach, 101L, "WOD_WRITE"));
    assertFalse(securityService.hasGymPermission(coach, 202L, "WOD_WRITE"));
    assertTrue(securityService.hasGymPermission(coach, 202L, "SCORE_VERIFY"));
  }

  @Test
  @DisplayName("hasGymPermission: COACH without WOD_WRITE is denied")
  void testHasGymPermission_CoachNoPerm() {
    assertFalse(
        securityService.hasGymPermission(
            principal("USER", 101L, "COACH", List.of("SCORE_VERIFY")), 101L, "WOD_WRITE"));
  }

  @Test
  @DisplayName("hasGymPermission: outsider, null gym or null principal are denied")
  void testHasGymPermission_Outsider() {
    AldebaranUserPrincipal coach = principal("USER", 101L, "COACH", List.of("WOD_WRITE"));
    assertFalse(securityService.hasGymPermission(coach, 999L, "WOD_WRITE"));
    assertFalse(securityService.hasGymPermission(coach, null, "WOD_WRITE"));
    assertFalse(securityService.hasGymPermission(null, 101L, "WOD_WRITE"));
  }
}
