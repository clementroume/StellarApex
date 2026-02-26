package apex.stellar.aldebaran.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.service.WodService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WodSecurityTest {

  @Mock private SecurityService securityService;
  @InjectMocks private WodSecurity wodSecurity;
  @Mock private WodService wodService;

  // --- canRead Checks ---

  @Test
  @DisplayName("canRead: Admin is always allowed")
  void canRead_Admin_ReturnsTrue() {
    AldebaranUserPrincipal admin =
        new AldebaranUserPrincipal(1L, null, "ADMIN", Collections.emptyList());
    when(securityService.isAdmin(admin)).thenReturn(true);

    boolean result = wodSecurity.canRead(10L, admin);

    assertTrue(result);
    verify(wodService, never()).getWodDetail(any());
  }

  @Test
  @DisplayName("canRead: Throws Exception if WOD not found (Fail-Closed)")
  void canRead_WodNotFound_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "ATHLETE", Collections.emptyList());

    when(wodService.getWodDetail(10L)).thenThrow(new ResourceNotFoundException("error", 10L));

    assertThrows(ResourceNotFoundException.class, () -> wodSecurity.canRead(10L, user));
  }

  @Test
  @DisplayName("canRead: Public WOD is readable by anyone")
  void canRead_PublicWod_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, null, "USER", Collections.emptyList());
    WodResponse publicWod = mock(WodResponse.class);

    when(securityService.isAdmin(user)).thenReturn(false);
    when(publicWod.isPublic()).thenReturn(true);
    when(wodService.getWodDetail(10L)).thenReturn(publicWod);

    boolean result = wodSecurity.canRead(10L, user);

    assertTrue(result);
  }

  @Test
  @DisplayName("canRead: Gym WOD readable by member of the same gym")
  void canRead_GymWod_SameGym_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "ATHLETE", Collections.emptyList());
    WodResponse gymWod = mock(WodResponse.class);

    when(securityService.isAdmin(user)).thenReturn(false);
    when(gymWod.isPublic()).thenReturn(false);
    when(gymWod.gymId()).thenReturn(100L);
    when(wodService.getWodDetail(10L)).thenReturn(gymWod);

    boolean result = wodSecurity.canRead(10L, user);

    assertTrue(result);
  }

  @Test
  @DisplayName("canRead: Gym WOD denied for member of a different gym")
  void canRead_GymWod_DifferentGym_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 200L, "ATHLETE", Collections.emptyList());
    WodResponse gymWod = mock(WodResponse.class);

    when(securityService.isAdmin(user)).thenReturn(false);
    when(gymWod.isPublic()).thenReturn(false);
    when(gymWod.gymId()).thenReturn(100L);
    when(wodService.getWodDetail(10L)).thenReturn(gymWod);

    boolean result = wodSecurity.canRead(10L, user);

    assertFalse(result);
  }

  @Test
  @DisplayName("canRead: Private WOD readable by its author")
  void canRead_PrivateWod_Author_ReturnsTrue() {
    AldebaranUserPrincipal author =
        new AldebaranUserPrincipal(2L, 100L, "ATHLETE", Collections.emptyList());
    WodResponse privateWod = mock(WodResponse.class);

    when(securityService.isAdmin(author)).thenReturn(false);
    when(privateWod.isPublic()).thenReturn(false);
    when(privateWod.gymId()).thenReturn(null);
    when(privateWod.authorId()).thenReturn(2L);
    when(wodService.getWodDetail(10L)).thenReturn(privateWod);

    boolean result = wodSecurity.canRead(10L, author);

    assertTrue(result);
  }

  @Test
  @DisplayName("canRead: Private WOD denied for anyone except author")
  void canRead_PrivateWod_NotAuthor_ReturnsFalse() {
    AldebaranUserPrincipal otherUser =
        new AldebaranUserPrincipal(3L, 100L, "ATHLETE", Collections.emptyList());
    WodResponse privateWod = mock(WodResponse.class);

    when(securityService.isAdmin(otherUser)).thenReturn(false);
    when(privateWod.isPublic()).thenReturn(false);
    when(privateWod.gymId()).thenReturn(null);
    when(privateWod.authorId()).thenReturn(2L);
    when(wodService.getWodDetail(10L)).thenReturn(privateWod);

    boolean result = wodSecurity.canRead(10L, otherUser);

    assertFalse(result);
  }

  // --- canCreate Checks ---

  @Test
  @DisplayName("canCreate: Admin is always allowed")
  void canCreate_Admin_ReturnsTrue() {
    AldebaranUserPrincipal admin =
        new AldebaranUserPrincipal(1L, null, "ADMIN", Collections.emptyList());
    WodRequest request =
        new WodRequest(
            "Test", null, null, null, null, null, 100L, false, null, null, null, null, null);

    when(securityService.isAdmin(admin)).thenReturn(true);

    boolean result = wodSecurity.canCreate(request, admin);

    assertTrue(result);
  }

  @Test
  @DisplayName("canCreate: Owner allowed to create WOD for their gym")
  void canCreate_GymWod_Owner_SameGym_ReturnsTrue() {
    AldebaranUserPrincipal owner =
        new AldebaranUserPrincipal(2L, 100L, "OWNER", Collections.emptyList());
    WodRequest request =
        new WodRequest(
            "Gym WOD", null, null, null, null, null, 100L, false, null, null, null, null, null);

    when(securityService.isAdmin(owner)).thenReturn(false);
    when(securityService.hasWodWriteAccess(owner)).thenReturn(true);

    boolean result = wodSecurity.canCreate(request, owner);

    assertTrue(result);
  }

  @Test
  @DisplayName("canCreate: Programmer allowed to create WOD for their gym")
  void canCreate_GymWod_Programmer_SameGym_ReturnsTrue() {
    AldebaranUserPrincipal programmer =
        new AldebaranUserPrincipal(3L, 100L, "PROGRAMMER", Collections.emptyList());
    WodRequest request =
        new WodRequest(
            "Prog WOD", null, null, null, null, null, 100L, false, null, null, null, null, null);

    when(securityService.isAdmin(programmer)).thenReturn(false);
    when(securityService.hasWodWriteAccess(programmer)).thenReturn(true);

    boolean result = wodSecurity.canCreate(request, programmer);

    assertTrue(result);
  }

  @Test
  @DisplayName("canCreate: Coach with WOD_WRITE permission allowed")
  void canCreate_GymWod_Coach_WithRights_ReturnsTrue() {
    AldebaranUserPrincipal coach =
        new AldebaranUserPrincipal(4L, 100L, "COACH", List.of("WOD_WRITE"));
    WodRequest request =
        new WodRequest(
            "Coach WOD", null, null, null, null, null, 100L, false, null, null, null, null, null);

    when(securityService.isAdmin(coach)).thenReturn(false);
    when(securityService.hasWodWriteAccess(coach)).thenReturn(true);

    boolean result = wodSecurity.canCreate(request, coach);

    assertTrue(result);
  }

  @Test
  @DisplayName("canCreate: Coach without permission denied")
  void canCreate_GymWod_Coach_NoRights_ReturnsFalse() {
    AldebaranUserPrincipal coach =
        new AldebaranUserPrincipal(4L, 100L, "COACH", Collections.emptyList());
    WodRequest request =
        new WodRequest(
            "Coach WOD", null, null, null, null, null, 100L, false, null, null, null, null, null);

    when(securityService.isAdmin(coach)).thenReturn(false);
    when(securityService.hasWodWriteAccess(coach)).thenReturn(false);

    boolean result = wodSecurity.canCreate(request, coach);

    assertFalse(result);
  }

  @Test
  @DisplayName("canCreate: Regular User denied creation of Gym WOD")
  void canCreate_GymWod_User_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(5L, 100L, "ATHLETE", Collections.emptyList());
    WodRequest request =
        new WodRequest(
            "User WOD", null, null, null, null, null, 100L, false, null, null, null, null, null);

    when(securityService.isAdmin(user)).thenReturn(false);
    when(securityService.hasWodWriteAccess(user)).thenReturn(false);

    boolean result = wodSecurity.canCreate(request, user);

    assertFalse(result);
  }

  @Test
  @DisplayName("canCreate: Owner denied creation for a different gym")
  void canCreate_GymWod_Owner_DifferentGym_ReturnsFalse() {
    AldebaranUserPrincipal owner =
        new AldebaranUserPrincipal(2L, 200L, "OWNER", Collections.emptyList());
    WodRequest request =
        new WodRequest(
            "Hack WOD", null, null, null, null, null, 100L, false, null, null, null, null, null);

    when(securityService.isAdmin(owner)).thenReturn(false);

    boolean result = wodSecurity.canCreate(request, owner);

    assertFalse(result);
    // Ensure we blocked at Gym ID check before checking roles
    verify(securityService, never()).hasWodWriteAccess(any());
  }

  @Test
  @DisplayName("canCreate: User allowed to create personal WOD for themselves")
  void canCreate_PersonalWod_Self_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "ATHLETE", Collections.emptyList());
    WodRequest request =
        new WodRequest(
            "My WOD", null, null, null, null, 2L, null, false, null, null, null, null, null);

    when(securityService.isAdmin(user)).thenReturn(false);

    boolean result = wodSecurity.canCreate(request, user);

    assertTrue(result);
  }

  @Test
  @DisplayName("canCreate: User denied creating personal WOD for someone else")
  void canCreate_PersonalWod_OtherUser_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "ATHLETE", Collections.emptyList());
    WodRequest request =
        new WodRequest(
            "Impersonation",
            null,
            null,
            null,
            null,
            99L,
            null,
            false,
            null,
            null,
            null,
            null,
            null);

    when(securityService.isAdmin(user)).thenReturn(false);

    boolean result = wodSecurity.canCreate(request, user);

    assertFalse(result);
  }

  // --- canModify Checks ---

  @Test
  @DisplayName("canModify: Admin is always allowed")
  void canModify_Admin_ReturnsTrue() {
    AldebaranUserPrincipal admin =
        new AldebaranUserPrincipal(1L, null, "ADMIN", Collections.emptyList());
    when(securityService.isAdmin(admin)).thenReturn(true);

    boolean result = wodSecurity.canModify(10L, admin);

    assertTrue(result);
    verify(wodService, never()).getWodDetail(any());
  }

  @Test
  @DisplayName("canModify: Returns true if WOD not found (delegates 404 to service)")
  void canModify_WodNotFound_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "ATHLETE", Collections.emptyList());
    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenThrow(new ResourceNotFoundException("error", 10L));

    assertThrows(ResourceNotFoundException.class, () -> wodSecurity.canModify(10L, user));
  }

  @Test
  @DisplayName("canModify: Owner allowed to modify their gym's WOD")
  void canModify_GymWod_Owner_SameGym_ReturnsTrue() {
    AldebaranUserPrincipal owner =
        new AldebaranUserPrincipal(2L, 100L, "OWNER", Collections.emptyList());
    WodResponse gymWod = mock(WodResponse.class);
    when(gymWod.gymId()).thenReturn(100L);

    when(securityService.isAdmin(owner)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenReturn(gymWod);
    when(securityService.hasWodWriteAccess(owner)).thenReturn(true);

    boolean result = wodSecurity.canModify(10L, owner);

    assertTrue(result);
  }

  @Test
  @DisplayName("canModify: Programmer allowed to modify their gym's WOD")
  void canModify_GymWod_Programmer_SameGym_ReturnsTrue() {
    AldebaranUserPrincipal programmer =
        new AldebaranUserPrincipal(3L, 100L, "PROGRAMMER", Collections.emptyList());
    WodResponse gymWod = mock(WodResponse.class);
    when(gymWod.gymId()).thenReturn(100L);

    when(securityService.isAdmin(programmer)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenReturn(gymWod);
    when(securityService.hasWodWriteAccess(programmer)).thenReturn(true);

    boolean result = wodSecurity.canModify(10L, programmer);

    assertTrue(result);
  }

  @Test
  @DisplayName("canModify: Coach with permissions allowed to modify gym WOD")
  void canModify_GymWod_Coach_WithRights_ReturnsTrue() {
    AldebaranUserPrincipal coach =
        new AldebaranUserPrincipal(4L, 100L, "COACH", List.of("WOD_WRITE"));
    WodResponse gymWod = mock(WodResponse.class);
    when(gymWod.gymId()).thenReturn(100L);

    when(securityService.isAdmin(coach)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenReturn(gymWod);
    when(securityService.hasWodWriteAccess(coach)).thenReturn(true);

    boolean result = wodSecurity.canModify(10L, coach);

    assertTrue(result);
  }

  @Test
  @DisplayName("canModify: Regular User denied modification of Gym WOD")
  void canModify_GymWod_User_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(5L, 100L, "ATHLETE", Collections.emptyList());
    WodResponse gymWod = mock(WodResponse.class);
    when(gymWod.gymId()).thenReturn(100L);

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenReturn(gymWod);
    when(securityService.hasWodWriteAccess(user)).thenReturn(false);

    boolean result = wodSecurity.canModify(10L, user);

    assertFalse(result);
  }

  @Test
  @DisplayName("canModify: Owner denied modification of another gym's WOD")
  void canModify_GymWod_DifferentGym_Owner_ReturnsFalse() {
    AldebaranUserPrincipal owner =
        new AldebaranUserPrincipal(2L, 200L, "OWNER", Collections.emptyList());
    WodResponse gymWod = mock(WodResponse.class);
    when(gymWod.gymId()).thenReturn(100L);

    when(securityService.isAdmin(owner)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenReturn(gymWod);

    boolean result = wodSecurity.canModify(10L, owner);

    assertFalse(result);
    // Ensure we didn't check write access for the wrong gym
    verify(securityService, never()).hasWodWriteAccess(any());
  }

  @Test
  @DisplayName("canModify: Programmer denied modification of another gym's WOD")
  void canModify_GymWod_DifferentGym_Programmer_ReturnsFalse() {
    AldebaranUserPrincipal owner =
        new AldebaranUserPrincipal(3L, 300L, "PROGRAMMER", Collections.emptyList());
    WodResponse gymWod = mock(WodResponse.class);
    when(gymWod.gymId()).thenReturn(100L);

    when(securityService.isAdmin(owner)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenReturn(gymWod);

    boolean result = wodSecurity.canModify(10L, owner);

    assertFalse(result);
    // Ensure we didn't check write access for the wrong gym
    verify(securityService, never()).hasWodWriteAccess(any());
  }

  @Test
  @DisplayName("canModify: Author allowed to modify their private WOD")
  void canModify_PrivateWod_Author_ReturnsTrue() {
    AldebaranUserPrincipal author =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodResponse privateWod = mock(WodResponse.class);
    when(privateWod.gymId()).thenReturn(null);
    when(privateWod.authorId()).thenReturn(2L);

    when(securityService.isAdmin(author)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenReturn(privateWod);

    boolean result = wodSecurity.canModify(10L, author);

    assertTrue(result);
  }

  @Test
  @DisplayName("canModify: Non-author denied modification of private WOD")
  void canModify_PrivateWod_NotAuthor_ReturnsFalse() {
    AldebaranUserPrincipal otherUser =
        new AldebaranUserPrincipal(3L, 100L, "ATHLETE", Collections.emptyList());
    WodResponse privateWod = mock(WodResponse.class);
    when(privateWod.gymId()).thenReturn(null);
    when(privateWod.authorId()).thenReturn(2L);

    when(securityService.isAdmin(otherUser)).thenReturn(false);
    when(wodService.getWodDetail(10L)).thenReturn(privateWod);

    boolean result = wodSecurity.canModify(10L, otherUser);

    assertFalse(result);
  }
}
