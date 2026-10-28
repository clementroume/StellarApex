package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.GymResponse;
import apex.stellar.antares.dto.GymSettingsRequest;
import apex.stellar.antares.dto.GymUpdateRequest;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.GymMapper;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.GymType;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class GymServiceTest {

  @Mock private GymRepository gymRepository;
  @Mock private MembershipRepository membershipRepository;
  @Mock private GymMapper gymMapper;
  @Mock private UserMapper userMapper;
  @Mock private EmailVerificationService emailVerificationService;
  @InjectMocks private GymService gymService;

  @Captor private ArgumentCaptor<Membership> membershipCaptor;

  @Test
  @DisplayName("createGym: Should succeed for Physical Box")
  void createGym_shouldSucceed_PhysicalBox() {
    // Given
    GymRequest request = new GymRequest(" Spartacus ", "Desc", false);
    User user = new User();
    user.setId(7L);
    Gym gym = new Gym();
    gym.setId(1L);

    when(gymRepository.existsByNameIgnoreCase("Spartacus")).thenReturn(false);
    when(gymMapper.toEntity(request)).thenReturn(gym);
    when(gymRepository.save(any(Gym.class))).thenReturn(gym);
    when(gymMapper.toResponse(gym)).thenReturn(mock(GymResponse.class));

    // When
    gymService.createGym(request, user);

    // Then
    assertEquals(GymStatus.PENDING_APPROVAL, gym.getStatus());
    assertEquals("Spartacus", gym.getName());
    assertEquals(8, gym.getEnrollmentCode().length());

    verify(membershipRepository).save(membershipCaptor.capture());
    Membership m = membershipCaptor.getValue();
    assertEquals(GymRole.OWNER, m.getGymRole());
    assertEquals(Membership.MembershipStatus.ACTIVE, m.getStatus());
  }

  @Test
  @DisplayName("createGym: a virtual program makes its creator PROGRAMMER")
  void createGym_shouldSucceed_Program() {
    GymRequest request = new GymRequest("Zeus", null, true);
    User user = new User();
    user.setId(7L);
    Gym gym = Gym.builder().id(2L).isProgramming(true).build();
    when(gymMapper.toEntity(request)).thenReturn(gym);
    when(gymRepository.save(any(Gym.class))).thenReturn(gym);

    gymService.createGym(request, user);

    verify(membershipRepository).save(membershipCaptor.capture());
    assertEquals(GymRole.PROGRAMMER, membershipCaptor.getValue().getGymRole());
  }

  @Test
  @DisplayName("createGym: Should fail when name is duplicate")
  void createGym_shouldFail_DuplicateName() {
    // Given
    GymRequest request = new GymRequest("Existing", "Desc", false);
    User user = new User();
    when(gymRepository.existsByNameIgnoreCase("Existing")).thenReturn(true);

    // When / Then
    assertThrows(DataConflictException.class, () -> gymService.createGym(request, user));
  }

  @Test
  @DisplayName("createGym: at most one request waiting for approval per user")
  void createGym_shouldFail_PendingRequest() {
    GymRequest request = new GymRequest("Another", "B", false);
    User user = new User();
    user.setId(7L);
    when(membershipRepository.existsPendingGymRequest(7L)).thenReturn(true);

    assertThrows(DataConflictException.class, () -> gymService.createGym(request, user));
    verifyNoInteractions(gymRepository);
  }

  @Test
  @DisplayName("updateGym: renames, clears a blank description, refuses a taken name")
  void updateGym() {
    Gym gym = Gym.builder().id(1L).name("Old").description("Desc").build();
    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
    when(gymRepository.save(gym)).thenReturn(gym);

    gymService.updateGym(1L, new GymUpdateRequest(" New ", " "));
    assertEquals("New", gym.getName());
    assertNull(gym.getDescription());

    when(gymRepository.existsByNameIgnoreCaseAndIdNot("Taken", 1L)).thenReturn(true);
    GymUpdateRequest taken = new GymUpdateRequest("Taken", null);
    assertThrows(DataConflictException.class, () -> gymService.updateGym(1L, taken));
  }

  @Test
  @DisplayName("regenerateEnrollmentCode: replaces the code with a new random one")
  void regenerateEnrollmentCode() {
    Gym gym = Gym.builder().id(1L).enrollmentCode("OLD").build();
    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
    when(gymRepository.save(gym)).thenReturn(gym);

    gymService.regenerateEnrollmentCode(1L);

    assertNotEquals("OLD", gym.getEnrollmentCode());
    assertEquals(8, gym.getEnrollmentCode().length());
  }

  @Test
  @DisplayName("updateSettings: Should update fields successfully")
  void updateSettings_ShouldUpdateFields() {
    // Given
    Gym gym = new Gym();
    gym.setId(1L);
    gym.setEnrollmentCode("OLD");

    GymSettingsRequest request = new GymSettingsRequest("NEW-CODE", true);

    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));

    // When
    gymService.updateSettings(1L, request);

    // Then
    assertEquals("NEW-CODE", gym.getEnrollmentCode());
    assertTrue(gym.isAutoSubscription());
    verify(gymRepository).save(gym);
  }

  @Test
  @DisplayName("updateSettings: Should throw if gym not found")
  void updateSettings_ShouldThrow_IfNotFound() {
    // Given
    when(gymRepository.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    GymSettingsRequest request = new GymSettingsRequest("CODE", true);
    assertThrows(ResourceNotFoundException.class, () -> gymService.updateSettings(99L, request));
  }

  @Test
  @DisplayName("getSettings: Should map the gym to its settings view")
  void getSettings_ShouldReturnSettings() {
    // Given
    Gym gym = new Gym();
    gym.setId(1L);
    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));

    // When
    gymService.getSettings(1L);

    // Then
    verify(gymMapper).toSettingsResponse(gym);
  }

  @Test
  @DisplayName("getSettings: Should throw if gym not found")
  void getSettings_ShouldThrow_IfNotFound() {
    // Given
    when(gymRepository.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(ResourceNotFoundException.class, () -> gymService.getSettings(99L));
  }

  @Test
  @DisplayName("searchActiveGyms: ACTIVE gyms only, escaped pattern, type filter, capped page")
  void searchActiveGyms() {
    when(gymRepository.search(any(), any(), any(), any()))
        .thenReturn(new SliceImpl<>(List.of(new Gym())));

    gymService.searchActiveGyms(" 50%_Box ", GymType.PROGRAM, PageRequest.of(0, 500));

    verify(gymRepository)
        .search("%50\\%\\_box%", true, GymStatus.ACTIVE, PageRequest.of(0, 50));
  }

  @Test
  @DisplayName("getActiveGym: 404 for a gym that is not ACTIVE")
  void getActiveGym_NotActive() {
    when(gymRepository.findById(1L))
        .thenReturn(Optional.of(Gym.builder().id(1L).status(GymStatus.PENDING_APPROVAL).build()));

    assertThrows(ResourceNotFoundException.class, () -> gymService.getActiveGym(1L));
  }

  @Test
  @DisplayName("updateStatus: Should update status")
  void updateStatus_ShouldUpdate() {
    // Given
    Gym gym = new Gym();
    gym.setId(1L);
    gym.setStatus(GymStatus.PENDING_APPROVAL);

    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
    when(gymRepository.save(any(Gym.class))).thenReturn(gym);
    when(gymMapper.toResponse(any())).thenReturn(mock(GymResponse.class));

    // When
    gymService.updateStatus(1L, GymStatus.ACTIVE);

    // Then
    assertEquals(GymStatus.ACTIVE, gym.getStatus());
    verify(gymRepository).save(gym);
  }

  @Test
  @DisplayName("updateStatus: Should throw if gym not found")
  void updateStatus_ShouldThrow_IfNotFound() {
    // Given
    when(gymRepository.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(ResourceNotFoundException.class, () -> gymService.updateStatus(99L, GymStatus.ACTIVE));
  }

  @Test
  @DisplayName("deleteGym: Should throw if not found")
  void deleteGym_ShouldThrow_IfNotFound() {
    // Given
    when(gymRepository.existsById(99L)).thenReturn(false);

    // When / Then
    assertThrows(ResourceNotFoundException.class, () -> gymService.deleteGym(99L));
  }

  @Test
  @DisplayName("deleteGym: Should delete if found")
  void deleteGym_ShouldDelete_IfFound() {
    // Given
    when(gymRepository.existsById(1L)).thenReturn(true);

    // When
    gymService.deleteGym(1L);

    // Then
    verify(gymRepository).deleteById(1L);
  }

  // --- Competition support: usable members ---

  private static Membership membership(
      long userId, String first, String last, Membership.MembershipStatus status, Gym gym) {
    User user = new User();
    user.setId(userId);
    user.setFirstName(first);
    user.setLastName(last);
    return Membership.builder()
        .user(user)
        .gym(gym)
        .gymRole(GymRole.ATHLETE)
        .status(status)
        .build();
  }

  @Test
  @DisplayName("getAthletes: usable members only, sorted by display name")
  void getAthletes_UsableMembersSorted() {
    Gym gym = Gym.builder().id(1L).status(GymStatus.ACTIVE).build();
    when(membershipRepository.findByGymId(1L))
        .thenReturn(
            List.of(
                membership(3L, "Zoé", "Martin", Membership.MembershipStatus.ACTIVE, gym),
                membership(4L, "Paul", "Durand", Membership.MembershipStatus.PENDING, gym),
                membership(5L, "Anne", "Leroy", Membership.MembershipStatus.ACTIVE, gym)));

    var athletes = gymService.getAthletes(1L);

    assertEquals(2, athletes.size());
    assertEquals("Anne L.", athletes.get(0).displayName());
    assertEquals(3L, athletes.get(1).id());
  }

  @Test
  @DisplayName("isUsableMember: true only for a usable membership")
  void isUsableMember() {
    Gym active = Gym.builder().id(1L).status(GymStatus.ACTIVE).build();
    when(membershipRepository.findByUserIdAndGymId(3L, 1L))
        .thenReturn(
            Optional.of(membership(3L, "Zoé", "Martin", Membership.MembershipStatus.ACTIVE, active)));
    when(membershipRepository.findByUserIdAndGymId(4L, 1L))
        .thenReturn(
            Optional.of(membership(4L, "Paul", "Durand", Membership.MembershipStatus.BANNED, active)));
    when(membershipRepository.findByUserIdAndGymId(5L, 1L)).thenReturn(Optional.empty());

    assertTrue(gymService.isUsableMember(1L, 3L));
    assertFalse(gymService.isUsableMember(1L, 4L));
    assertFalse(gymService.isUsableMember(1L, 5L));
  }
}
