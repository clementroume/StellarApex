package apex.stellar.antares.service;

import static apex.stellar.antares.service.GatewayIdentityService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GatewayIdentityServiceTest {

  private static final String SECRET = "internal-secret";

  @Mock private UserRepository userRepository;

  private GatewayIdentityService service;
  private User user;

  @BeforeEach
  void setUp() {
    service = new GatewayIdentityService(userRepository, SECRET);
    user = User.builder().id(1L).platformRole(PlatformRole.USER).locale("fr").build();
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
  }

  private void addMembership(
      long gymId,
      GymStatus gymStatus,
      GymRole role,
      MembershipStatus status,
      Set<Permission> perms) {
    user.getMemberships()
        .add(
            Membership.builder()
                .user(user)
                .gym(Gym.builder().id(gymId).status(gymStatus).build())
                .gymRole(role)
                .status(status)
                .permissions(perms)
                .build());
  }

  private HttpHeaders verify() {
    ResponseEntity<Void> response = service.verify(user, null);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    return response.getHeaders();
  }

  // --- Standard flow ---

  @Test
  @DisplayName("verify: No membership should send platform role and empty memberships")
  void verify_NoMembership_ShouldSendUserHeadersOnly() {
    HttpHeaders headers = verify();

    assertEquals("1", headers.getFirst(HEADER_AUTH_USER_ID));
    assertEquals("USER", headers.getFirst(HEADER_AUTH_USER_ROLE));
    assertEquals("fr", headers.getFirst(HEADER_AUTH_USER_LOCALE));
    assertEquals(SECRET, headers.getFirst(HEADER_INTERNAL_SECRET));
    assertEquals("", headers.getFirst(HEADER_AUTH_MEMBERSHIPS));
    assertFalse(headers.containsHeader(HEADER_AUTH_ACTOR_ID));
  }

  @Test
  @DisplayName("verify: Several memberships should be formatted, sorted, unusable ones excluded")
  void verify_SeveralMemberships_ShouldListUsableOnes() {
    addMembership(
        30L,
        GymStatus.ACTIVE,
        GymRole.ATHLETE,
        MembershipStatus.ACTIVE,
        Set.of(Permission.WOD_WRITE)); // Athlete: stored permission ignored
    addMembership(
        10L,
        GymStatus.ACTIVE,
        GymRole.COACH,
        MembershipStatus.ACTIVE,
        Set.of(Permission.SCORE_VERIFY, Permission.WOD_WRITE));
    // Pending gym but OWNER -> usable, with every permission
    addMembership(
        20L, GymStatus.PENDING_APPROVAL, GymRole.OWNER, MembershipStatus.ACTIVE, Set.of());
    // Pending membership -> excluded
    addMembership(40L, GymStatus.ACTIVE, GymRole.COACH, MembershipStatus.PENDING, Set.of());
    // Pending gym and ATHLETE -> excluded
    addMembership(
        50L, GymStatus.PENDING_APPROVAL, GymRole.ATHLETE, MembershipStatus.ACTIVE, Set.of());

    HttpHeaders headers = verify();

    assertEquals(
        "10:COACH:WOD_WRITE+SCORE_VERIFY,"
            + "20:OWNER:WOD_WRITE+SCORE_VERIFY+MANAGE_MEMBERSHIPS+MANAGE_SETTINGS,"
            + "30:ATHLETE:",
        headers.getFirst(HEADER_AUTH_MEMBERSHIPS));
    assertEquals("USER", headers.getFirst(HEADER_AUTH_USER_ROLE));
  }

  @Test
  @DisplayName("verify: Admin with memberships should keep ADMIN platform role")
  void verify_AdminWithMembership_ShouldKeepAdminRole() {
    user.setPlatformRole(PlatformRole.ADMIN);
    addMembership(10L, GymStatus.ACTIVE, GymRole.ATHLETE, MembershipStatus.ACTIVE, Set.of());

    HttpHeaders headers = verify();

    assertEquals("ADMIN", headers.getFirst(HEADER_AUTH_USER_ROLE));
    assertEquals("10:ATHLETE:", headers.getFirst(HEADER_AUTH_MEMBERSHIPS));
  }

  // --- Support session ---

  @Test
  @DisplayName("verify: Support session should send the target identity plus the actor id")
  void verify_SupportSession_ShouldSendActorId() {
    addMembership(10L, GymStatus.ACTIVE, GymRole.ATHLETE, MembershipStatus.ACTIVE, Set.of());

    ResponseEntity<Void> response = service.verify(user, 99L);

    HttpHeaders headers = response.getHeaders();
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("1", headers.getFirst(HEADER_AUTH_USER_ID));
    assertEquals("USER", headers.getFirst(HEADER_AUTH_USER_ROLE));
    assertEquals("10:ATHLETE:", headers.getFirst(HEADER_AUTH_MEMBERSHIPS));
    assertEquals("99", headers.getFirst(HEADER_AUTH_ACTOR_ID));
  }
}
