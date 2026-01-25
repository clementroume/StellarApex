package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import apex.stellar.antares.model.*;
import apex.stellar.antares.repository.MembershipRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
  void hasGymPermission_GlobalAdmin_ShouldReturnTrue() {
    currentUser.setPlatformRole(PlatformRole.ADMIN);
    mockUser();
    assertTrue(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  void hasGymPermission_Owner_ShouldReturnTrue_Implicitly() {
    mockUser();
    Membership m = new Membership();
    m.setGymRole(GymRole.OWNER); // VIP Owner
    m.setPermissions(Set.of());

    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(m));

    assertTrue(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  void hasGymPermission_Programmer_ShouldReturnTrue_Implicitly() {
    mockUser(); // Verify Programmer is also VIP
    Membership m = new Membership();
    m.setGymRole(GymRole.PROGRAMMER); // VIP Programmer
    m.setPermissions(Set.of()); // No explicit permissions needed

    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(m));

    assertTrue(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  void hasGymPermission_Coach_WithPermission_ShouldReturnTrue() {
    mockUser();
    Membership m = new Membership();
    m.setGymRole(GymRole.COACH);
    m.setPermissions(Set.of(Permission.MANAGE_MEMBERSHIPS));

    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(m));

    assertTrue(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  void hasGymPermission_Coach_WithoutPermission_ShouldReturnFalse() {
    mockUser();
    Membership m = new Membership();
    m.setGymRole(GymRole.COACH);
    m.setPermissions(Set.of(Permission.WOD_WRITE));

    when(membershipRepository.findByUserIdAndGymId(1L, 100L)).thenReturn(Optional.of(m));

    assertFalse(securityService.hasGymPermission(100L, "MANAGE_MEMBERSHIPS"));
  }

  @Test
  void hasGymPermission_InvalidPermissionName_ShouldReturnFalse() {
    mockUser();
    assertFalse(securityService.hasGymPermission(100L, "INVALID_PERM_NAME"));
  }
}
