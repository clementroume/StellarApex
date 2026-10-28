package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.dto.MembershipCapabilities;
import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.dto.MembershipUpdateRequest;
import apex.stellar.antares.exception.BusinessRuleException;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.exception.TooManyAttemptsException;
import apex.stellar.antares.mapper.MembershipMapper;
import apex.stellar.antares.model.*;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

  @Mock private MembershipRepository membershipRepository;
  @Mock private GymRepository gymRepository;
  @Mock private MembershipMapper membershipMapper;
  @Mock private EnrollmentAttemptService enrollmentAttemptService;
  @Mock private EmailVerificationService emailVerificationService;
  @InjectMocks private MembershipService membershipService;

  @Captor private ArgumentCaptor<Membership> membershipCaptor;

  private static Gym activeGym(boolean autoSubscription) {
    return Gym.builder()
        .id(1L)
        .name("Box")
        .enrollmentCode("CODE")
        .isAutoSubscription(autoSubscription)
        .status(Gym.GymStatus.ACTIVE)
        .build();
  }

  private static User user(long id) {
    return User.builder().id(id).platformRole(PlatformRole.USER).build();
  }

  @Test
  @DisplayName("joinGym: Should succeed and set status to PENDING when auto-sub is false")
  void joinGym_Success_Pending() {
    // Given
    JoinGymRequest request = new JoinGymRequest(1L, " code ");
    when(gymRepository.findById(1L)).thenReturn(Optional.of(activeGym(false)));
    when(membershipRepository.findByUserIdAndGymId(10L, 1L)).thenReturn(Optional.empty());
    when(membershipRepository.save(any(Membership.class))).thenAnswer(i -> i.getArgument(0));

    // When
    membershipService.joinGym(request, user(10L));

    // Then
    verify(membershipRepository).save(membershipCaptor.capture());
    assertEquals(MembershipStatus.PENDING, membershipCaptor.getValue().getStatus());
    assertEquals(GymRole.ATHLETE, membershipCaptor.getValue().getGymRole());
  }

  @Test
  @DisplayName("joinGym: a former member is reactivated on the same membership, as athlete")
  void joinGym_Reactivates_Inactive() {
    Gym gym = activeGym(true);
    Membership former = membershipIn(gym, 5L, GymRole.COACH);
    former.setStatus(MembershipStatus.INACTIVE);
    former.setPermissions(Set.of(Permission.WOD_WRITE));
    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
    when(membershipRepository.findByUserIdAndGymId(10L, 1L)).thenReturn(Optional.of(former));
    when(membershipRepository.save(former)).thenReturn(former);

    membershipService.joinGym(new JoinGymRequest(1L, "CODE"), user(10L));

    assertEquals(MembershipStatus.ACTIVE, former.getStatus());
    assertEquals(GymRole.ATHLETE, former.getGymRole());
    assertTrue(former.getPermissions().isEmpty());
  }

  @Test
  @DisplayName("joinGym: Should fail if already a member, or banned")
  void joinGym_Fail_AlreadyExists_OrBanned() {
    Gym gym = activeGym(false);
    Membership existing = membershipIn(gym, 5L, GymRole.ATHLETE);
    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
    when(membershipRepository.findByUserIdAndGymId(10L, 1L)).thenReturn(Optional.of(existing));
    JoinGymRequest request = new JoinGymRequest(1L, "CODE");
    User user = user(10L);

    DataConflictException exists =
        assertThrows(DataConflictException.class, () -> membershipService.joinGym(request, user));
    assertEquals("error.membership.exists", exists.getMessageKey());

    existing.setStatus(MembershipStatus.BANNED);
    DataConflictException banned =
        assertThrows(DataConflictException.class, () -> membershipService.joinGym(request, user));
    assertEquals("error.membership.banned", banned.getMessageKey());
  }

  @Test
  @DisplayName("joinGym: 404 for an unknown gym or a gym that is not ACTIVE")
  void joinGym_GymNotFound() {
    when(gymRepository.findById(99L)).thenReturn(Optional.empty());
    Gym pending = activeGym(false);
    pending.setStatus(Gym.GymStatus.PENDING_APPROVAL);
    when(gymRepository.findById(1L)).thenReturn(Optional.of(pending));

    User user = user(10L);
    JoinGymRequest unknown = new JoinGymRequest(99L, "CODE");
    JoinGymRequest notActive = new JoinGymRequest(1L, "CODE");
    assertThrows(ResourceNotFoundException.class, () -> membershipService.joinGym(unknown, user));
    assertThrows(
        ResourceNotFoundException.class, () -> membershipService.joinGym(notActive, user));
  }

  @Test
  @DisplayName("joinGym: an invalid code is a bad request (400), not a forbidden one")
  void joinGym_InvalidCode() {
    when(gymRepository.findById(1L)).thenReturn(Optional.of(activeGym(false)));

    JoinGymRequest request = new JoinGymRequest(1L, "WRONG");
    User user = user(10L);
    BusinessRuleException ex =
        assertThrows(BusinessRuleException.class, () -> membershipService.joinGym(request, user));
    assertEquals("error.enrollment.code.invalid", ex.getMessageKey());
    verify(enrollmentAttemptService).recordFailure(10L);
  }

  @Test
  @DisplayName("joinGym: a right code is not counted; too many wrong codes refuse before any check")
  void joinGym_Throttling() {
    when(gymRepository.findById(1L)).thenReturn(Optional.of(activeGym(false)));
    when(membershipRepository.findByUserIdAndGymId(10L, 1L)).thenReturn(Optional.empty());
    when(membershipRepository.save(any(Membership.class))).thenAnswer(i -> i.getArgument(0));
    User user = user(10L);

    membershipService.joinGym(new JoinGymRequest(1L, "CODE"), user);
    verify(enrollmentAttemptService).checkAllowed(10L);
    verify(enrollmentAttemptService, never()).recordFailure(any());

    doThrow(new TooManyAttemptsException("error.enrollment.too.many", 60, 1))
        .when(enrollmentAttemptService)
        .checkAllowed(10L);
    JoinGymRequest request = new JoinGymRequest(1L, "CODE");
    assertThrows(TooManyAttemptsException.class, () -> membershipService.joinGym(request, user));
    verify(gymRepository, times(1)).findById(1L);
  }

  @Test
  @DisplayName("leaveGym: ACTIVE becomes INACTIVE, PENDING is withdrawn, holder and banned refused")
  void leaveGym() {
    Gym gym = activeGym(false);
    Membership active = membershipIn(gym, 2L, GymRole.ATHLETE);
    Membership pending = membershipIn(gym, 3L, GymRole.ATHLETE);
    pending.setStatus(MembershipStatus.PENDING);
    Membership owner = membershipIn(gym, 4L, GymRole.OWNER);
    Membership banned = membershipIn(gym, 5L, GymRole.ATHLETE);
    banned.setStatus(MembershipStatus.BANNED);
    when(membershipRepository.findByUserIdAndGymId(2L, 1L)).thenReturn(Optional.of(active));
    when(membershipRepository.findByUserIdAndGymId(3L, 1L)).thenReturn(Optional.of(pending));
    when(membershipRepository.findByUserIdAndGymId(4L, 1L)).thenReturn(Optional.of(owner));
    when(membershipRepository.findByUserIdAndGymId(5L, 1L)).thenReturn(Optional.of(banned));

    membershipService.leaveGym(1L, user(2L));
    assertEquals(MembershipStatus.INACTIVE, active.getStatus());
    verify(membershipRepository).save(active);

    membershipService.leaveGym(1L, user(3L));
    verify(membershipRepository).delete(pending);

    User ownerUser = user(4L);
    DataConflictException holder =
        assertThrows(DataConflictException.class, () -> membershipService.leaveGym(1L, ownerUser));
    assertEquals("error.membership.leave.holder", holder.getMessageKey());

    User bannedUser = user(5L);
    assertThrows(DataConflictException.class, () -> membershipService.leaveGym(1L, bannedUser));
    assertEquals(MembershipStatus.BANNED, banned.getStatus());
  }

  @Test
  @DisplayName("getMemberships: pending first, each with the requester's capabilities")
  void getMemberships_ShouldReturnList() {
    Gym gym = activeGym(false);
    Membership owner = membershipIn(gym, 1L, GymRole.OWNER);
    Membership athlete = membershipIn(gym, 2L, GymRole.ATHLETE);
    Membership pending = membershipIn(gym, 3L, GymRole.ATHLETE);
    pending.setStatus(MembershipStatus.PENDING);
    when(membershipRepository.findByGymId(1L)).thenReturn(List.of(owner, athlete, pending));
    when(membershipRepository.findByUserIdAndGymId(10L, 1L)).thenReturn(Optional.of(owner));

    membershipService.getMemberships(1L, null, owner.getUser());

    var order = org.mockito.Mockito.inOrder(membershipMapper);
    order.verify(membershipMapper).toResponse(eq(pending), any());
    order.verify(membershipMapper).toResponse(eq(owner), eq(MembershipCapabilities.NONE));
    order.verify(membershipMapper).toResponse(eq(athlete), any());
  }

  // --- Capabilities ---

  @Test
  @DisplayName("capabilities: holder manages everyone else, coach validates athletes only")
  void capabilities() {
    Gym box = activeGym(false);
    Membership owner = membershipIn(box, 1L, GymRole.OWNER);
    Membership coach = membershipIn(box, 2L, GymRole.COACH);
    coach.setPermissions(Set.of(Permission.MANAGE_MEMBERSHIPS));
    Membership athlete = membershipIn(box, 3L, GymRole.ATHLETE);
    Membership pending = membershipIn(box, 4L, GymRole.ATHLETE);
    pending.setStatus(MembershipStatus.PENDING);

    assertEquals(
        new MembershipCapabilities(true, true, true, true, true, true),
        membershipService.capabilities(owner.getUser(), owner, athlete));
    assertEquals(
        new MembershipCapabilities(true, true, true, true, true, false),
        membershipService.capabilities(owner.getUser(), owner, pending));
    assertEquals(
        MembershipCapabilities.NONE, membershipService.capabilities(owner.getUser(), owner, owner));
    assertEquals(
        new MembershipCapabilities(true, false, false, true, false, false),
        membershipService.capabilities(coach.getUser(), coach, athlete));
    assertEquals(
        MembershipCapabilities.NONE, membershipService.capabilities(coach.getUser(), coach, coach));

    // A coach without the delegation, or no longer ACTIVE, manages nothing
    coach.setStatus(MembershipStatus.INACTIVE);
    assertEquals(
        MembershipCapabilities.NONE,
        membershipService.capabilities(coach.getUser(), coach, athlete));

    // Virtual program: athletes only, so no role or permission change
    Gym program = Gym.builder().id(2L).isProgramming(true).status(Gym.GymStatus.ACTIVE).build();
    Membership member = membershipIn(program, 5L, GymRole.ATHLETE);
    assertEquals(
        new MembershipCapabilities(true, false, false, true, true, true),
        membershipService.capabilities(admin(), null, member));
  }

  @Test
  @DisplayName("updateMembership: Should throw ResourceNotFoundException if membership not found")
  void updateMembership_NotFound() {
    // Given
    when(membershipRepository.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(
        ResourceNotFoundException.class,
        () -> membershipService.updateMembership(99L, null, null, null));
  }

  @Test
  @DisplayName("updateMembership: Coach cannot change role nor ban (Access Denied)")
  void updateMembership_Coach_AccessDenied_ChangeRole() {
    Gym gym = activeGym(false);
    Membership requesterMem = membershipIn(gym, 1L, GymRole.COACH);
    requesterMem.setPermissions(Set.of(Permission.MANAGE_MEMBERSHIPS));
    Membership targetMem = membershipIn(gym, 2L, GymRole.ATHLETE);
    User requester = requesterMem.getUser();
    when(membershipRepository.findById(2L)).thenReturn(Optional.of(targetMem));

    MembershipUpdateRequest promote =
        new MembershipUpdateRequest(MembershipStatus.ACTIVE, GymRole.COACH, Set.of());
    assertThrows(
        AccessDeniedException.class,
        () -> membershipService.updateMembership(2L, promote, requester, requesterMem));

    MembershipUpdateRequest ban =
        new MembershipUpdateRequest(MembershipStatus.BANNED, GymRole.ATHLETE, Set.of());
    assertThrows(
        AccessDeniedException.class,
        () -> membershipService.updateMembership(2L, ban, requester, requesterMem));
  }

  @Test
  @DisplayName("updateMembership: Coach validates and deactivates an athlete")
  void updateMembership_Coach_ChangesStatus() {
    Gym gym = activeGym(false);
    Membership requesterMem = membershipIn(gym, 1L, GymRole.COACH);
    requesterMem.setPermissions(Set.of(Permission.MANAGE_MEMBERSHIPS));
    Membership targetMem = membershipIn(gym, 2L, GymRole.ATHLETE);
    targetMem.setStatus(MembershipStatus.PENDING);
    when(membershipRepository.findById(2L)).thenReturn(Optional.of(targetMem));
    when(membershipRepository.save(targetMem)).thenReturn(targetMem);

    membershipService.updateMembership(
        2L,
        new MembershipUpdateRequest(MembershipStatus.ACTIVE, GymRole.ATHLETE, Set.of()),
        requesterMem.getUser(),
        requesterMem);

    assertEquals(MembershipStatus.ACTIVE, targetMem.getStatus());
  }

  @Test
  @DisplayName("updateMembership: Cross-tenant update should fail")
  void updateMembership_CrossTenant_ShouldFail() {
    Membership requesterMem = membershipIn(activeGym(false), 1L, GymRole.OWNER);
    Gym gymB = Gym.builder().id(2L).status(Gym.GymStatus.ACTIVE).build();
    Membership targetMem = membershipIn(gymB, 99L, GymRole.ATHLETE);
    User requester = requesterMem.getUser();

    when(membershipRepository.findById(99L)).thenReturn(Optional.of(targetMem));
    MembershipUpdateRequest req =
        new MembershipUpdateRequest(MembershipStatus.ACTIVE, GymRole.ATHLETE, Set.of());

    // When / Then
    assertThrows(
        AccessDeniedException.class,
        () -> membershipService.updateMembership(99L, req, requester, requesterMem));
  }

  @Test
  @DisplayName("updateMembership: Owner should succeed updating Athlete")
  void updateMembership_Owner_Success() {
    Gym gym = activeGym(false);
    Membership requesterMem = membershipIn(gym, 1L, GymRole.OWNER);
    Membership targetMem = membershipIn(gym, 2L, GymRole.ATHLETE);

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(
            MembershipStatus.ACTIVE, GymRole.COACH, Set.of(Permission.WOD_WRITE));

    when(membershipRepository.findById(2L)).thenReturn(Optional.of(targetMem));
    when(membershipRepository.save(any())).thenReturn(targetMem);

    // When
    membershipService.updateMembership(2L, request, requesterMem.getUser(), requesterMem);

    // Then
    assertEquals(GymRole.COACH, targetMem.getGymRole());
    assertEquals(Set.of(Permission.WOD_WRITE), targetMem.getPermissions());
    verify(membershipRepository).save(targetMem);
  }

  @Test
  @DisplayName("updateMembership: the holder's status never changes here")
  void updateMembership_HolderStatus_ShouldBeRejected() {
    Gym gym = activeGym(false);
    Membership owner = membershipIn(gym, 1L, GymRole.OWNER);
    when(membershipRepository.findById(1L)).thenReturn(Optional.of(owner));

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(MembershipStatus.INACTIVE, GymRole.OWNER, Set.of());
    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> membershipService.updateMembership(1L, request, owner.getUser(), owner));
    assertEquals("error.membership.holder.status", ex.getMessageKey());
  }

  @Test
  @DisplayName("updateMembership: Global Admin should succeed without membership context")
  void updateMembership_GlobalAdmin_Success() {
    Membership targetMem = membershipIn(activeGym(false), 2L, GymRole.ATHLETE);

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(MembershipStatus.BANNED, GymRole.ATHLETE, Set.of());

    when(membershipRepository.findById(2L)).thenReturn(Optional.of(targetMem));
    when(membershipRepository.save(any())).thenReturn(targetMem);

    // When
    membershipService.updateMembership(2L, request, admin(), null);

    // Then
    assertEquals(MembershipStatus.BANNED, targetMem.getStatus());
  }

  @Test
  @DisplayName("updateMembership: Owner cannot modify Global Admin (Access Denied)")
  void updateMembership_ModifyAdmin_Forbidden() {
    Gym gym = activeGym(false);
    Membership ownerMem = membershipIn(gym, 1L, GymRole.OWNER);
    Membership targetMem = membershipIn(gym, 2L, GymRole.ATHLETE);
    targetMem.getUser().setPlatformRole(PlatformRole.ADMIN);
    User owner = ownerMem.getUser();

    when(membershipRepository.findById(2L)).thenReturn(Optional.of(targetMem));

    // When / Then
    assertThrows(
        AccessDeniedException.class,
        () -> membershipService.updateMembership(2L, null, owner, ownerMem));
  }

  @Test
  @DisplayName("getUserMemberships: Should return summaries")
  void getUserMemberships_ShouldReturnSummaries() {
    // Given
    User user = new User();
    user.setId(10L);

    Membership m1 = new Membership();
    m1.setId(1L);
    Membership m2 = new Membership();
    m2.setId(2L);

    when(membershipRepository.findByUserId(10L)).thenReturn(List.of(m1, m2));
    when(membershipMapper.toSummary(any())).thenReturn(mock(MembershipSummary.class));

    // When
    List<MembershipSummary> result = membershipService.getUserMemberships(user);

    // Then
    assertEquals(2, result.size());
    verify(membershipRepository).findByUserId(10L);
    verify(membershipMapper, times(2)).toSummary(any());
  }

  @Test
  @DisplayName("removeMembership: refuses (deletes) a request, deactivates a member")
  void removeMembership() {
    Gym gym = activeGym(false);
    Membership coach = membershipIn(gym, 1L, GymRole.COACH);
    coach.setPermissions(Set.of(Permission.MANAGE_MEMBERSHIPS));
    Membership pending = membershipIn(gym, 2L, GymRole.ATHLETE);
    pending.setStatus(MembershipStatus.PENDING);
    Membership active = membershipIn(gym, 3L, GymRole.ATHLETE);
    when(membershipRepository.findById(2L)).thenReturn(Optional.of(pending));
    when(membershipRepository.findById(3L)).thenReturn(Optional.of(active));

    membershipService.removeMembership(2L, coach.getUser(), coach);
    verify(membershipRepository).delete(pending);

    membershipService.removeMembership(3L, coach.getUser(), coach);
    assertEquals(MembershipStatus.INACTIVE, active.getStatus());
    verify(membershipRepository).save(active);
  }

  @Test
  @DisplayName("removeMembership: a coach never removes the owner nor another coach")
  void removeMembership_Refused() {
    Gym gym = activeGym(false);
    Membership coach = membershipIn(gym, 1L, GymRole.COACH);
    coach.setPermissions(Set.of(Permission.MANAGE_MEMBERSHIPS));
    Membership owner = membershipIn(gym, 2L, GymRole.OWNER);
    Membership otherCoach = membershipIn(gym, 3L, GymRole.COACH);
    when(membershipRepository.findById(2L)).thenReturn(Optional.of(owner));
    when(membershipRepository.findById(3L)).thenReturn(Optional.of(otherCoach));
    User requester = coach.getUser();

    assertThrows(
        DataConflictException.class,
        () -> membershipService.removeMembership(2L, requester, coach));
    assertThrows(
        AccessDeniedException.class,
        () -> membershipService.removeMembership(3L, requester, coach));
    verify(membershipRepository, never()).delete(any());
    verify(membershipRepository, never()).save(any());
  }

  // --- Role rules by gym type ---

  private static Membership membershipIn(Gym gym, long id, GymRole role) {
    Membership m = new Membership();
    m.setId(id);
    m.setGym(gym);
    m.setGymRole(role);
    m.setStatus(MembershipStatus.ACTIVE);
    // Distinct users: Membership equality is (userId, gymId)
    m.setUser(User.builder().id(id * 10).platformRole(PlatformRole.USER).build());
    return m;
  }

  private static User admin() {
    User admin = new User();
    admin.setPlatformRole(PlatformRole.ADMIN);
    return admin;
  }

  @Test
  @DisplayName("updateMembership: Role not allowed for the gym type should be rejected")
  void updateMembership_RoleNotAllowed_ShouldBeRejected() {
    Gym virtual = Gym.builder().id(1L).isProgramming(true).build();
    Membership target = membershipIn(virtual, 2L, GymRole.ATHLETE);
    when(membershipRepository.findById(2L)).thenReturn(Optional.of(target));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () ->
                membershipService.updateMembership(
                    2L,
                    new MembershipUpdateRequest(MembershipStatus.ACTIVE, GymRole.COACH, Set.of()),
                    admin(),
                    null));
    assertEquals("error.membership.role.not.allowed", ex.getMessageKey());
    verify(membershipRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateMembership: Assigning OWNER or changing the OWNER should be rejected")
  void updateMembership_HolderRole_ShouldBeRejected() {
    Gym box = Gym.builder().id(1L).isProgramming(false).build();
    Membership athlete = membershipIn(box, 2L, GymRole.ATHLETE);
    Membership owner = membershipIn(box, 3L, GymRole.OWNER);
    when(membershipRepository.findById(2L)).thenReturn(Optional.of(athlete));
    when(membershipRepository.findById(3L)).thenReturn(Optional.of(owner));

    BusinessRuleException assign =
        assertThrows(
            BusinessRuleException.class,
            () ->
                membershipService.updateMembership(
                    2L,
                    new MembershipUpdateRequest(MembershipStatus.ACTIVE, GymRole.OWNER, Set.of()),
                    admin(),
                    null));
    assertEquals("error.membership.role.holder.assign", assign.getMessageKey());

    BusinessRuleException change =
        assertThrows(
            BusinessRuleException.class,
            () ->
                membershipService.updateMembership(
                    3L,
                    new MembershipUpdateRequest(MembershipStatus.ACTIVE, GymRole.COACH, Set.of()),
                    admin(),
                    null));
    assertEquals("error.membership.role.holder.change", change.getMessageKey());
  }

  @Test
  @DisplayName("transferOwnership: Physical gym should swap OWNER and COACH, clearing permissions")
  void transferOwnership_Physical_ShouldSwapRoles() {
    Gym box = Gym.builder().id(1L).isProgramming(false).build();
    Membership owner = membershipIn(box, 2L, GymRole.OWNER);
    Membership coach = membershipIn(box, 3L, GymRole.COACH);
    coach.setPermissions(Set.of(Permission.WOD_WRITE));
    when(gymRepository.findById(1L)).thenReturn(Optional.of(box));
    when(membershipRepository.findById(3L)).thenReturn(Optional.of(coach));
    when(membershipRepository.findHoldersByGymId(1L)).thenReturn(List.of(owner));
    when(membershipRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    List<MembershipResponse> result = membershipService.transferOwnership(1L, 3L);

    assertEquals(2, result.size());
    assertEquals(GymRole.COACH, owner.getGymRole());
    assertEquals(GymRole.OWNER, coach.getGymRole());
    assertTrue(coach.getPermissions().isEmpty());
    // The former holder is demoted (and flushed) before the new one is promoted
    var order = inOrder(membershipRepository);
    order.verify(membershipRepository).saveAndFlush(owner);
    order.verify(membershipRepository).saveAndFlush(coach);
  }

  @Test
  @DisplayName("transferOwnership: Virtual gym demotes PROGRAMMER to ATHLETE")
  void transferOwnership_Virtual_ShouldDemoteToAthlete() {
    Gym track = Gym.builder().id(1L).isProgramming(true).build();
    Membership programmer = membershipIn(track, 2L, GymRole.PROGRAMMER);
    Membership athlete = membershipIn(track, 3L, GymRole.ATHLETE);
    when(gymRepository.findById(1L)).thenReturn(Optional.of(track));
    when(membershipRepository.findById(3L)).thenReturn(Optional.of(athlete));
    when(membershipRepository.findHoldersByGymId(1L)).thenReturn(List.of(programmer));
    when(membershipRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    membershipService.transferOwnership(1L, 3L);

    assertEquals(GymRole.ATHLETE, programmer.getGymRole());
    assertEquals(GymRole.PROGRAMMER, athlete.getGymRole());
  }

  @Test
  @DisplayName("transferOwnership: Pending or foreign member should be rejected")
  void transferOwnership_InvalidTarget_ShouldBeRejected() {
    Gym box = Gym.builder().id(1L).build();
    Gym other = Gym.builder().id(9L).build();
    Membership pending = membershipIn(box, 3L, GymRole.ATHLETE);
    pending.setStatus(MembershipStatus.PENDING);
    Membership foreign = membershipIn(other, 4L, GymRole.ATHLETE);
    when(gymRepository.findById(1L)).thenReturn(Optional.of(box));
    when(membershipRepository.findById(3L)).thenReturn(Optional.of(pending));
    when(membershipRepository.findById(4L)).thenReturn(Optional.of(foreign));

    assertThrows(BusinessRuleException.class, () -> membershipService.transferOwnership(1L, 3L));
    assertThrows(BusinessRuleException.class, () -> membershipService.transferOwnership(1L, 4L));
    verify(membershipRepository, never()).saveAndFlush(any());
  }
}
