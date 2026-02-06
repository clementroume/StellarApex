package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.dto.MembershipUpdateRequest;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
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
  @InjectMocks private MembershipService membershipService;

  @Captor private ArgumentCaptor<Membership> membershipCaptor;

  @Test
  @DisplayName("joinGym: Should succeed and set status to PENDING when auto-sub is false")
  void joinGym_Success_Pending() {
    // Given
    Gym gym = new Gym();
    gym.setId(1L);
    gym.setEnrollmentCode("CODE");
    gym.setAutoSubscription(false); // Should be PENDING

    JoinGymRequest request = new JoinGymRequest(1L, "CODE");
    User user = new User();
    user.setId(10L);

    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
    when(membershipRepository.existsByUserIdAndGymId(10L, 1L)).thenReturn(false);
    when(membershipRepository.save(any(Membership.class))).thenAnswer(i -> i.getArgument(0));
    when(membershipMapper.toResponse(any())).thenReturn(mock(MembershipResponse.class));

    // When
    membershipService.joinGym(request, user);

    // Then
    verify(membershipRepository).save(membershipCaptor.capture());
    assertEquals(MembershipStatus.PENDING, membershipCaptor.getValue().getStatus());
  }

  @Test
  @DisplayName("joinGym: Should fail if membership already exists")
  void joinGym_Fail_AlreadyExists() {
    // Given
    Gym gym = new Gym();
    gym.setId(1L);
    gym.setEnrollmentCode("CODE");

    JoinGymRequest request = new JoinGymRequest(1L, "CODE");
    User user = new User();
    user.setId(10L);

    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
    when(membershipRepository.existsByUserIdAndGymId(10L, 1L)).thenReturn(true);

    // When / Then
    assertThrows(DataConflictException.class, () -> membershipService.joinGym(request, user));
  }

  @Test
  @DisplayName("joinGym: Should throw ResourceNotFoundException if gym not found")
  void joinGym_GymNotFound() {
    // Given
    when(gymRepository.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    JoinGymRequest request = new JoinGymRequest(99L, "CODE");
    User user = new User();
    assertThrows(ResourceNotFoundException.class, () -> membershipService.joinGym(request, user));
  }

  @Test
  @DisplayName("joinGym: Should throw AccessDeniedException if code is invalid")
  void joinGym_InvalidCode() {
    // Given
    Gym gym = new Gym();
    gym.setEnrollmentCode("CORRECT");
    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));

    // When / Then
    JoinGymRequest request = new JoinGymRequest(1L, "WRONG");
    User user = new User();
    assertThrows(AccessDeniedException.class, () -> membershipService.joinGym(request, user));
  }

  @Test
  @DisplayName("getMemberships: Should return list filtered by status")
  void getMemberships_ShouldReturnList() {
    // Given
    when(membershipRepository.findByGymIdAndStatus(1L, MembershipStatus.ACTIVE))
        .thenReturn(List.of(new Membership()));
    when(membershipMapper.toResponse(any())).thenReturn(mock(MembershipResponse.class));

    // When
    List<MembershipResponse> result = membershipService.getMemberships(1L, MembershipStatus.ACTIVE);

    // Then
    assertEquals(1, result.size());
    verify(membershipRepository).findByGymIdAndStatus(1L, MembershipStatus.ACTIVE);
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
  @DisplayName("updateMembership: Coach cannot change role (Access Denied)")
  void updateMembership_Coach_AccessDenied_ChangeRole() {
    // Given
    // Coach tries to promote Athlete to Coach -> Should Fail
    User requester = new User();
    requester.setPlatformRole(PlatformRole.USER);

    Gym gym = new Gym();
    gym.setId(1L);

    Membership requesterMem = new Membership();
    requesterMem.setGymRole(GymRole.COACH);
    requesterMem.setGym(gym);
    requesterMem.setPermissions(Set.of(Permission.MANAGE_MEMBERSHIPS));

    Membership targetMem = new Membership();
    targetMem.setId(2L);
    targetMem.setGym(gym);
    targetMem.setGymRole(GymRole.ATHLETE);
    targetMem.setUser(new User());

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(
            MembershipStatus.ACTIVE,
            GymRole.COACH, // Trying to change the role
            Set.of());

    when(membershipRepository.findById(2L)).thenReturn(Optional.of(targetMem));

    // When / Then
    assertThrows(
        AccessDeniedException.class,
        () -> membershipService.updateMembership(2L, request, requester, requesterMem));
  }

  @Test
  @DisplayName("updateMembership: Cross-tenant update should fail")
  void updateMembership_CrossTenant_ShouldFail() {
    // Given
    // Owner of Gym A tries to update Member of Gym B
    User requester = new User();
    requester.setPlatformRole(PlatformRole.USER);

    Gym gymA = new Gym();
    gymA.setId(1L);
    Gym gymB = new Gym();
    gymB.setId(2L);

    Membership requesterMem = new Membership();
    requesterMem.setGymRole(GymRole.OWNER);
    requesterMem.setGym(gymA);

    Membership targetMem = new Membership();
    targetMem.setId(99L);
    targetMem.setGym(gymB); // Different Gym

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
    // Given
    User requester = new User();
    requester.setPlatformRole(PlatformRole.USER);
    Gym gym = new Gym();
    gym.setId(1L);

    Membership requesterMem = new Membership();
    requesterMem.setGymRole(GymRole.OWNER);
    requesterMem.setGym(gym);

    Membership targetMem = new Membership();
    targetMem.setId(2L);
    targetMem.setGym(gym);
    targetMem.setGymRole(GymRole.ATHLETE);
    targetMem.setUser(new User());

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(MembershipStatus.ACTIVE, GymRole.COACH, Set.of());

    when(membershipRepository.findById(2L)).thenReturn(Optional.of(targetMem));
    when(membershipRepository.save(any())).thenReturn(targetMem);
    when(membershipMapper.toResponse(any())).thenReturn(mock(MembershipResponse.class));

    // When
    membershipService.updateMembership(2L, request, requester, requesterMem);

    // Then
    assertEquals(GymRole.COACH, targetMem.getGymRole());
    verify(membershipRepository).save(targetMem);
  }

  @Test
  @DisplayName("updateMembership: Global Admin should succeed without membership context")
  void updateMembership_GlobalAdmin_Success() {
    // Given
    User admin = new User();
    admin.setPlatformRole(PlatformRole.ADMIN);

    Membership targetMem = new Membership();
    targetMem.setId(2L);
    targetMem.setUser(new User());

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(MembershipStatus.BANNED, GymRole.ATHLETE, Set.of());

    when(membershipRepository.findById(2L)).thenReturn(Optional.of(targetMem));
    when(membershipRepository.save(any())).thenReturn(targetMem);
    when(membershipMapper.toResponse(any())).thenReturn(mock(MembershipResponse.class));

    // When
    membershipService.updateMembership(2L, request, admin, null); // requesterMembership is null

    // Then
    assertEquals(MembershipStatus.BANNED, targetMem.getStatus());
  }

  @Test
  @DisplayName("updateMembership: Owner cannot modify Global Admin (Access Denied)")
  void updateMembership_ModifyAdmin_Forbidden() {
    // Given
    User owner = new User();
    owner.setPlatformRole(PlatformRole.USER);

    Gym gym = new Gym();
    gym.setId(1L);

    Membership ownerMem = new Membership();
    ownerMem.setGymRole(GymRole.OWNER);
    ownerMem.setGym(gym);

    User globalAdmin = new User();
    globalAdmin.setPlatformRole(PlatformRole.ADMIN);
    Membership targetMem = new Membership();
    targetMem.setUser(globalAdmin); // Target is ADMIN
    targetMem.setGym(gym); // Same Gym

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
  @DisplayName("deleteMembership: Should delete if exists")
  void deleteMembership_Success() {
    // Given
    when(membershipRepository.existsById(1L)).thenReturn(true);

    // When
    membershipService.deleteMembership(1L);

    // Then
    verify(membershipRepository).deleteById(1L);
  }

  @Test
  @DisplayName("deleteMembership: Should throw if not found")
  void deleteMembership_NotFound() {
    // Given
    when(membershipRepository.existsById(1L)).thenReturn(false);

    // When / Then
    assertThrows(ResourceNotFoundException.class, () -> membershipService.deleteMembership(1L));
  }
}
