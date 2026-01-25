package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.dto.MembershipUpdateRequest;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.mapper.MembershipMapper;
import apex.stellar.antares.model.*;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.repository.GymRepository;
import apex.stellar.antares.repository.MembershipRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  void joinGym_Success_Pending() {
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

    membershipService.joinGym(request, user);

    verify(membershipRepository).save(membershipCaptor.capture());
    assertEquals(MembershipStatus.PENDING, membershipCaptor.getValue().getStatus());
  }

  @Test
  void joinGym_Fail_AlreadyExists() {
    Gym gym = new Gym();
    gym.setId(1L);
    gym.setEnrollmentCode("CODE");

    JoinGymRequest request = new JoinGymRequest(1L, "CODE");
    User user = new User();
    user.setId(10L);

    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
    when(membershipRepository.existsByUserIdAndGymId(10L, 1L)).thenReturn(true);

    assertThrows(DataConflictException.class, () -> membershipService.joinGym(request, user));
  }

  @Test
  void updateMembership_Coach_AccessDenied_ChangeRole() {
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

    assertThrows(
        AccessDeniedException.class,
        () -> membershipService.updateMembership(2L, request, requester, requesterMem));
  }

  @Test
  void updateMembership_CrossTenant_ShouldFail() {
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

    assertThrows(
        AccessDeniedException.class,
        () -> membershipService.updateMembership(99L, req, requester, requesterMem));
  }

  @Test
  void getUserMemberships_ShouldReturnSummaries() {
    User user = new User();
    user.setId(10L);

    Membership m1 = new Membership();
    m1.setId(1L);
    Membership m2 = new Membership();
    m2.setId(2L);

    when(membershipRepository.findByUserId(10L)).thenReturn(List.of(m1, m2));
    // Le mapper est un mock, donc on doit définir son comportement
    when(membershipMapper.toSummary(any())).thenReturn(mock(MembershipSummary.class));

    List<MembershipSummary> result = membershipService.getUserMemberships(user);

    assertEquals(2, result.size());
    verify(membershipRepository).findByUserId(10L);
    verify(membershipMapper, times(2)).toSummary(any());
  }
}
