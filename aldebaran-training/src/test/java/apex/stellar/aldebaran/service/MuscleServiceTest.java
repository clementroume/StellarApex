package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.dto.MuscleRequest;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.MuscleMapper;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.repository.MuscleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MuscleServiceTest {

  @Mock private MuscleRepository muscleRepository;
  @Mock private MuscleMapper muscleMapper;

  @InjectMocks private MuscleService muscleService;

  private Muscle muscleEntity;
  private MuscleRequest muscleRequest;
  private MuscleResponse muscleResponse;

  @BeforeEach
  void setUp() {
    // Initialize common objects
    muscleEntity =
        Muscle.builder()
            .id(1L)
            .medicalName("Pectoralis Major")
            .muscleGroup(MuscleGroup.CHEST)
            .build();

    muscleRequest =
        new MuscleRequest(
            "Pectoralis Major", "Chest", "Pectoraux", "Desc EN", "Desc FR", MuscleGroup.CHEST);

    muscleResponse =
        new MuscleResponse(
            1L, "Pectoralis Major", "Chest", "Pectoraux", "Desc EN", "Desc FR", MuscleGroup.CHEST);
  }

  @Test
  @DisplayName("getAllMuscles: should return list of mapped responses")
  void testGetAllMuscles() {
    // Given
    when(muscleRepository.findAll()).thenReturn(List.of(muscleEntity));
    when(muscleMapper.toResponse(muscleEntity)).thenReturn(muscleResponse);

    // When
    List<MuscleResponse> result = muscleService.getAllMuscles();

    // Then
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("Pectoralis Major", result.get(0).medicalName());
    verify(muscleRepository).findAll();
  }

  @Test
  @DisplayName("getMusclesByGroup: should return filtered list")
  void testGetMusclesByGroup() {
    // Given
    when(muscleRepository.findByMuscleGroup(MuscleGroup.CHEST)).thenReturn(List.of(muscleEntity));
    when(muscleMapper.toResponse(muscleEntity)).thenReturn(muscleResponse);

    // When
    List<MuscleResponse> result = muscleService.getMusclesByGroup(MuscleGroup.CHEST);

    // Then
    assertNotNull(result);
    assertEquals(1, result.size());
    verify(muscleRepository).findByMuscleGroup(MuscleGroup.CHEST);
  }

  @Test
  @DisplayName("getMuscle: should return muscle when found")
  void testGetMuscle_Success() {
    // Given
    String name = "Pectoralis Major";
    when(muscleRepository.findByMedicalName(name)).thenReturn(Optional.of(muscleEntity));
    when(muscleMapper.toResponse(muscleEntity)).thenReturn(muscleResponse);

    // When
    MuscleResponse result = muscleService.getMuscle(name);

    // Then
    assertNotNull(result);
    assertEquals(name, result.medicalName());
  }

  @Test
  @DisplayName("getMuscle: should throw ResourceNotFoundException when not found")
  void testGetMuscle_NotFound() {
    // Given
    String name = "Unknown";
    when(muscleRepository.findByMedicalName(name)).thenReturn(Optional.empty());

    // When & Then
    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> muscleService.getMuscle(name));
    assertEquals("error.muscle.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("createMuscle: should create and return muscle when name is unique")
  void testCreateMuscle_Success() {
    // Given
    when(muscleRepository.findByMedicalName(muscleRequest.medicalName()))
        .thenReturn(Optional.empty());
    when(muscleMapper.toEntity(muscleRequest)).thenReturn(muscleEntity);
    when(muscleRepository.save(muscleEntity)).thenReturn(muscleEntity);
    when(muscleMapper.toResponse(muscleEntity)).thenReturn(muscleResponse);

    // When
    MuscleResponse result = muscleService.createMuscle(muscleRequest);

    // Then
    assertNotNull(result);
    assertEquals(1L, result.id());
    verify(muscleRepository).save(muscleEntity);
  }

  @Test
  @DisplayName("createMuscle: should throw DataConflictException when name exists")
  void testCreateMuscle_Conflict() {
    // Given
    when(muscleRepository.findByMedicalName(muscleRequest.medicalName()))
        .thenReturn(Optional.of(muscleEntity));

    // When & Then
    DataConflictException ex =
        assertThrows(DataConflictException.class, () -> muscleService.createMuscle(muscleRequest));

    assertEquals("error.muscle.name.exists", ex.getMessageKey());
    verify(muscleRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateMuscle: should update and return muscle when found")
  void testUpdateMuscle_Success() {
    // Given
    Long id = 1L;
    when(muscleRepository.findById(id)).thenReturn(Optional.of(muscleEntity));
    // Name hasn't changed or is same as current, so no conflict check failure simulated here
    // If name changed, we would need to mock findByMedicalName returning empty

    when(muscleRepository.save(muscleEntity)).thenReturn(muscleEntity);
    when(muscleMapper.toResponse(muscleEntity)).thenReturn(muscleResponse);

    // When
    MuscleResponse result = muscleService.updateMuscle(id, muscleRequest);

    // Then
    assertNotNull(result);
    verify(muscleMapper).updateEntity(muscleRequest, muscleEntity);
    verify(muscleRepository).save(muscleEntity);
  }

  @Test
  @DisplayName("updateMuscle: should throw ResourceNotFoundException when id not found")
  void testUpdateMuscle_NotFound() {
    // Given
    Long id = 99L;
    when(muscleRepository.findById(id)).thenReturn(Optional.empty());

    // When & Then
    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class, () -> muscleService.updateMuscle(id, muscleRequest));

    assertEquals("error.muscle.not.found", ex.getMessageKey());
    verify(muscleRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateMuscle: should allow renaming if new name is unique")
  void testUpdateMuscle_RenameSuccess() {
    // Given
    Long id = 1L;
    Muscle existing = Muscle.builder().id(id).medicalName("Old Name").build();
    MuscleRequest renameRequest =
        new MuscleRequest("New Name", "Common", "Commun", "Desc", "Desc", MuscleGroup.CHEST);

    when(muscleRepository.findById(id)).thenReturn(Optional.of(existing));
    // Simulate that "New Name" does not exist
    when(muscleRepository.findByMedicalName("New Name")).thenReturn(Optional.empty());

    when(muscleRepository.save(existing)).thenReturn(existing);
    when(muscleMapper.toResponse(existing)).thenReturn(mock(MuscleResponse.class));

    // When
    muscleService.updateMuscle(id, renameRequest);

    // Then
    verify(muscleMapper).updateEntity(renameRequest, existing);
    verify(muscleRepository).save(existing);
  }

  @Test
  @DisplayName("updateMuscle: should throw DataConflictException when renaming to existing name")
  void testUpdateMuscle_Conflict() {
    // Given
    Long id = 1L;
    // Existing muscle in DB
    Muscle existingMuscle = Muscle.builder().id(id).medicalName("Old Name").build();

    // Request tries to rename it to "Pectoralis Major"
    // But "Pectoralis Major" already belongs to another muscle
    when(muscleRepository.findById(id)).thenReturn(Optional.of(existingMuscle));
    when(muscleRepository.findByMedicalName(muscleRequest.medicalName()))
        .thenReturn(Optional.of(new Muscle())); // Another muscle

    // When & Then
    assertThrows(DataConflictException.class, () -> muscleService.updateMuscle(id, muscleRequest));
    verify(muscleRepository, never()).save(any());
  }
}
