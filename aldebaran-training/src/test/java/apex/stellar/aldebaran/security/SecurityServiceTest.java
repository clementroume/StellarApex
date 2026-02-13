package apex.stellar.aldebaran.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
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

  @Test
  @DisplayName("getCurrentUserId: should return ID from Principal")
  void testGetCurrentUserId_Success() {
    AldebaranUserPrincipal principal = new AldebaranUserPrincipal(123L, 1L, "USER", List.of());
    Authentication auth = mock(Authentication.class);
    SecurityContext context = mock(SecurityContext.class);

    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getPrincipal()).thenReturn(principal);
    when(context.getAuthentication()).thenReturn(auth);

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
  @DisplayName("isAdmin: should return true for ADMIN role")
  void testIsAdmin_True() {
    AldebaranUserPrincipal principal = new AldebaranUserPrincipal(1L, null, "ADMIN", List.of());
    assertTrue(securityService.isAdmin(principal));
  }

  @Test
  @DisplayName("isAdmin: should return false for other roles")
  void testIsAdmin_False() {
    AldebaranUserPrincipal principal = new AldebaranUserPrincipal(1L, null, "USER", List.of());
    assertFalse(securityService.isAdmin(principal));
  }

  @Test
  @DisplayName("hasWodWriteAccess: should return true for OWNER")
  void testHasWodWriteAccess_Owner() {
    AldebaranUserPrincipal principal = new AldebaranUserPrincipal(1L, 101L, "OWNER", List.of());
    assertTrue(securityService.hasWodWriteAccess(principal));
  }

  @Test
  @DisplayName("hasWodWriteAccess: should return true for COACH with WOD_WRITE")
  void testHasWodWriteAccess_CoachWithPerm() {
    AldebaranUserPrincipal principal =
        new AldebaranUserPrincipal(1L, 101L, "COACH", List.of("WOD_WRITE"));
    assertTrue(securityService.hasWodWriteAccess(principal));
  }

  @Test
  @DisplayName("hasWodWriteAccess: should return false for COACH without WOD_WRITE")
  void testHasWodWriteAccess_CoachNoPerm() {
    AldebaranUserPrincipal principal = new AldebaranUserPrincipal(1L, 101L, "COACH", List.of());
    assertFalse(securityService.hasWodWriteAccess(principal));
  }

  @Test
  @DisplayName("hasScoreVerificationRights: should return true for COACH with SCORE_VERIFY")
  void testHasScoreVerificationRights_CoachWithPerm() {
    AldebaranUserPrincipal principal =
        new AldebaranUserPrincipal(1L, 101L, "COACH", List.of("SCORE_VERIFY"));
    assertTrue(securityService.hasScoreVerificationRights(principal));
  }
}