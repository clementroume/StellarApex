package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import apex.stellar.antares.model.*; 
import apex.stellar.antares.repository.jpa.MembershipRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AntaresSecurityServiceTest {

  @Mock private MembershipRepository membershipRepository;
  @Mock private SecurityContext securityContext;
  @Mock private Authentication authentication;

  @InjectMocks private AntaresSecurityService securityService;

  private User currentUser;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.setContext(securityContext);
    currentUser = new User();
    currentUser.setId(1L);
    currentUser.setPlatformRole(PlatformRole.USER);
  }

  private void mockUser() {
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getPrincipal()).thenReturn(currentUser);
  }

  @Test
  @DisplayName("hasGymPermission: Global Admin should always return true")
  void hasGymPermission_GlobalAdmin_ShouldReturnTrue() {
    // Given
    currentUser.setPlatformRole(PlatformRole.ADMIN);
    mockUser();

    // When / Then
    assertTrue(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  @DisplayName("hasGymPermission: Owner should return true implicitly")
  void hasGymPermission_Owner_ShouldReturnTrue_Implicitly() {
    // Given
    mockUser();
    Membership m = new Membership();
    m.setGymRole(GymRole.OWNER); // VIP Owner
    m.setPermissions(Set.of());

    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(m));

    // When / Then
    assertTrue(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  @DisplayName("hasGymPermission: Programmer should return true implicitly")
  void hasGymPermission_Programmer_ShouldReturnTrue_Implicitly() {
    // Given
    mockUser(); // Verify Programmer is also VIP
    Membership m = new Membership();
    m.setGymRole(GymRole.PROGRAMMER); // VIP Programmer
    m.setPermissions(Set.of()); // No explicit permissions needed

    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(m));

    // When / Then
    assertTrue(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  @DisplayName("hasGymPermission: Coach with permission should return true")
  void hasGymPermission_Coach_WithPermission_ShouldReturnTrue() {
    // Given
    mockUser();
    Membership m = new Membership();
    m.setGymRole(GymRole.COACH);
    m.setPermissions(Set.of(Permission.MANAGE_MEMBERSHIPS));

    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(m));

    // When / Then
    assertTrue(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  @DisplayName("hasGymPermission: Coach without permission should return false")
  void hasGymPermission_Coach_WithoutPermission_ShouldReturnFalse() {
    // Given
    mockUser();
    Membership m = new Membership();
    m.setGymRole(GymRole.COACH);
    m.setPermissions(Set.of(Permission.WOD_WRITE));

    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(m));

    // When / Then
    assertFalse(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  @DisplayName("hasGymPermission: Invalid permission name should return false")
  void hasGymPermission_InvalidPermissionName_ShouldReturnFalse() {
    // Given
    mockUser();

    // When / Then
    assertFalse(securityService.hasGymPermission(100L, "INVALID_PERM_NAME"));
  }

  @Test
  @DisplayName("canManageMembership: Should return true if user has permission in gym")
  void canManageMembership_ShouldReturnTrue() {
    // Given
    mockUser();
    Gym gym = new Gym();
    gym.setId(100L);
    Membership target = new Membership();
    target.setGym(gym);

    Membership requesterMem = new Membership();
    requesterMem.setGymRole(GymRole.OWNER);

    when(membershipRepository.findById(50L)).thenReturn(Optional.of(target));
    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(requesterMem));

    // When / Then
    assertTrue(securityService.canManageMembership(50L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  @DisplayName("canManageMembership: Should return false if target not found")
  void canManageMembership_NotFound() {
    // Given
    mockUser();
    when(membershipRepository.findById(50L)).thenReturn(Optional.empty());

    // When / Then
    assertFalse(securityService.canManageMembership(50L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  @DisplayName("canManageMembership: Global Admin should always return true")
  void canManageMembership_GlobalAdmin_ShouldReturnTrue() {
    // Given
    currentUser.setPlatformRole(PlatformRole.ADMIN);
    mockUser();

    // When / Then
    assertTrue(securityService.canManageMembership(50L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  @DisplayName("hasGymPermission: Should return false if no user authenticated")
  void hasGymPermission_NoUser_ShouldReturnFalse() {
    // Given: No authentication in context
    when(securityContext.getAuthentication()).thenReturn(null);

    // When / Then
    assertFalse(securityService.hasGymPermission(100L, "ANY"));
  }
}
