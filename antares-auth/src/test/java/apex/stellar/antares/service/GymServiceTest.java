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
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.GymRepository;
import apex.stellar.antares.repository.MembershipRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
  void createGym_shouldSucceed_PhysicalBox() {
    GymRequest request = new GymRequest("Spartacus", "Desc", false, "SECRET");
    User user = new User();
    Gym gym = new Gym();
    gym.setId(1L);

    when(gymRepository.existsByName("Spartacus")).thenReturn(false);
    when(gymMapper.toEntity(request)).thenReturn(gym);
    when(gymRepository.save(any(Gym.class))).thenReturn(gym);
    when(gymMapper.toResponse(gym)).thenReturn(mock(GymResponse.class));

    gymService.createGym(request, user);
    assertEquals(GymStatus.PENDING_APPROVAL, gym.getStatus());

    verify(membershipRepository).save(membershipCaptor.capture());
    Membership m = membershipCaptor.getValue();
    assertEquals(GymRole.OWNER, m.getGymRole());
    assertEquals(Membership.MembershipStatus.ACTIVE, m.getStatus());
  }

  @Test
  void createGym_shouldFail_DuplicateName() {
    // GIVEN
    GymRequest request = new GymRequest("Existing", "Desc", false, "SECRET");
    User user = new User();
    when(gymRepository.existsByName("Existing")).thenReturn(true);

    // WHEN / THEN
    assertThrows(DataConflictException.class, () -> gymService.createGym(request, user));
  }

  @Test
  void createGym_shouldFail_InvalidToken() {
    // GIVEN
    GymRequest request = new GymRequest("A", "B", false, "WRONG");
    User user = new User();

    // WHEN / THEN
    assertThrows(AccessDeniedException.class, () -> gymService.createGym(request, user));
  }

  @Test
  void updateSettings_ShouldUpdateFields() {
    Gym gym = new Gym();
    gym.setId(1L);
    gym.setEnrollmentCode("OLD");

    GymSettingsRequest request = new GymSettingsRequest("NEW-CODE", true);

    when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));

    gymService.updateSettings(1L, request);

    assertEquals("NEW-CODE", gym.getEnrollmentCode());
    assertTrue(gym.isAutoSubscription());
    verify(gymRepository).save(gym);
  }

  @Test
  void deleteGym_ShouldThrow_IfNotFound() {
    when(gymRepository.existsById(99L)).thenReturn(false);
    assertThrows(ResourceNotFoundException.class, () -> gymService.deleteGym(99L));
  }
}
