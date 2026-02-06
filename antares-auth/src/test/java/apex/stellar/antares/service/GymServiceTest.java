package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.GymResponse;
import apex.stellar.antares.dto.GymSettingsRequest;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.GymMapper;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GymServiceTest {

  @Mock private GymRepository gymRepository;
  @Mock private MembershipRepository membershipRepository;
  @Mock private GymMapper gymMapper;
  @InjectMocks private GymService gymService;

  @Captor private ArgumentCaptor<Membership> membershipCaptor;

  @BeforeEach
  void setUp() {
    // Inject the secret property
    ReflectionTestUtils.setField(gymService, "creationSecret", "SECRET");
  }

  @Test
  @DisplayName("createGym: Should succeed for Physical Box")
  void createGym_shouldSucceed_PhysicalBox() {
    // Given
    GymRequest request = new GymRequest("Spartacus", "Desc", false, "SECRET");
    User user = new User();
    Gym gym = new Gym();
    gym.setId(1L);

    when(gymRepository.existsByName("Spartacus")).thenReturn(false);
    when(gymMapper.toEntity(request)).thenReturn(gym);
    when(gymRepository.save(any(Gym.class))).thenReturn(gym);
    when(gymMapper.toResponse(gym)).thenReturn(mock(GymResponse.class));

    // When
    gymService.createGym(request, user);

    // Then
    assertEquals(GymStatus.PENDING_APPROVAL, gym.getStatus());

    verify(membershipRepository).save(membershipCaptor.capture());
    Membership m = membershipCaptor.getValue();
    assertEquals(GymRole.OWNER, m.getGymRole());
    assertEquals(Membership.MembershipStatus.ACTIVE, m.getStatus());
  }

  @Test
  @DisplayName("createGym: Should fail when name is duplicate")
  void createGym_shouldFail_DuplicateName() {
    // Given
    GymRequest request = new GymRequest("Existing", "Desc", false, "SECRET");
    User user = new User();
    when(gymRepository.existsByName("Existing")).thenReturn(true);

    // When / Then
    assertThrows(DataConflictException.class, () -> gymService.createGym(request, user));
  }

  @Test
  @DisplayName("createGym: Should fail when token is invalid")
  void createGym_shouldFail_InvalidToken() {
    // Given
    GymRequest request = new GymRequest("A", "B", false, "WRONG");
    User user = new User();

    // When / Then
    assertThrows(AccessDeniedException.class, () -> gymService.createGym(request, user));
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
    GymSettingsRequest request = new GymSettingsRequest("C", true);
    assertThrows(ResourceNotFoundException.class, () -> gymService.updateSettings(99L, request));
  }

  @Test
  @DisplayName("getSettings: Should return settings")
  void getSettings_ShouldReturnSettings() {
    // Given
    Gym gym = new Gym();
    gym.setId(1L);
    gym.setEnrollmentCode("CODE");
    gym.setAutoSubscription(true);

    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));

    // When
    GymSettingsRequest result = gymService.getSettings(1L);

    // Then
    assertEquals("CODE", result.enrollmentCode());
    assertTrue(result.isAutoSubscription());
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
  @DisplayName("getAllGyms: Admin with filter should return filtered list")
  void getAllGyms_AdminWithFilter() {
    // Given
    when(gymRepository.findByStatus(GymStatus.PENDING_APPROVAL)).thenReturn(List.of(new Gym()));
    when(gymMapper.toResponse(any())).thenReturn(mock(GymResponse.class));

    // When
    List<GymResponse> result = gymService.getAllGyms(PlatformRole.ADMIN, GymStatus.PENDING_APPROVAL);

    // Then
    assertEquals(1, result.size());
    verify(gymRepository).findByStatus(GymStatus.PENDING_APPROVAL);
  }

  @Test
  @DisplayName("getAllGyms: User should only see ACTIVE gyms regardless of filter")
  void getAllGyms_UserIgnoresFilter() {
    // Given
    when(gymRepository.findByStatus(GymStatus.ACTIVE)).thenReturn(List.of(new Gym()));
    when(gymMapper.toResponse(any())).thenReturn(mock(GymResponse.class));

    // When
    List<GymResponse> result = gymService.getAllGyms(PlatformRole.USER, GymStatus.PENDING_APPROVAL);

    // Then
    assertEquals(1, result.size());
    verify(gymRepository).findByStatus(GymStatus.ACTIVE);
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
}
