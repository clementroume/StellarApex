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
            "Pectoralis Major",
            MuscleGroup.CHEST,
            null,
            "Chest",
            "Pectoraux",
            "Desc EN",
            "Desc FR",
            null);

    muscleResponse =
        new MuscleResponse(
            1L,
            "Pectoralis Major",
            MuscleGroup.CHEST,
            null,
            List.of(),
            "Chest",
            "Pectoraux",
            "Desc EN",
            "Desc FR",
            null);
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
    assertEquals("Pectoralis Major", result.getFirst().medicalName());
    verify(muscleRepository).findAll();
  }

  @Test
  @DisplayName("getMuscle: should return muscle when found")
  void testGetMuscle_Success() {
    // Given
    Long id = 1L;
    when(muscleRepository.findById(id)).thenReturn(Optional.of(muscleEntity));
    when(muscleMapper.toResponse(muscleEntity)).thenReturn(muscleResponse);

    // When
    MuscleResponse result = muscleService.getMuscle(id);

    // Then
    assertNotNull(result);
    assertEquals(id, result.id());
  }

  @Test
  @DisplayName("getMuscle: should throw ResourceNotFoundException when not found")
  void testGetMuscle_NotFound() {
    // Given
    Long id = 99L;
    when(muscleRepository.findById(id)).thenReturn(Optional.empty());

    // When & Then
    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> muscleService.getMuscle(id));
    assertEquals("error.muscle.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("createMuscle: should create and return muscle when name is unique")
  void testCreateMuscle_Success() {
    // Given
    when(muscleRepository.existsByMedicalNameIgnoreCase(muscleRequest.medicalName()))
        .thenReturn(false);
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
    when(muscleRepository.existsByMedicalNameIgnoreCase(muscleRequest.medicalName()))
        .thenReturn(true);

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
        new MuscleRequest("New Name", MuscleGroup.CHEST, null, "Common", "Commun", "Desc", "Desc", null);
    when(muscleRepository.findById(id)).thenReturn(Optional.of(existing));
    when(muscleRepository.existsByMedicalNameIgnoreCase("New Name")).thenReturn(false);

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
    Muscle existingMuscle = Muscle.builder().id(id).medicalName("Old Name").build();
    when(muscleRepository.findById(id)).thenReturn(Optional.of(existingMuscle));
    when(muscleRepository.existsByMedicalNameIgnoreCase(muscleRequest.medicalName()))
        .thenReturn(true);

    // When & Then
    assertThrows(DataConflictException.class, () -> muscleService.updateMuscle(id, muscleRequest));
    verify(muscleRepository, never()).save(any());
  }

  @Test
  @DisplayName("deleteMuscle: should delete muscle when found")
  void testDeleteMuscle_Success() {
    // Given
    Long id = 1L;
    when(muscleRepository.existsById(id)).thenReturn(true);

    // When
    muscleService.deleteMuscle(id);

    // Then
    verify(muscleRepository).deleteById(id);
  }

  @Test
  @DisplayName("deleteMuscle: should throw ResourceNotFoundException when muscle not found")
  void testDeleteMuscle_NotFound() {
    // Given
    Long id = 99L;
    when(muscleRepository.existsById(id)).thenReturn(false);

    // When & Then
    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> muscleService.deleteMuscle(id));
    assertEquals("error.muscle.not.found", ex.getMessageKey());
    verify(muscleRepository, never()).deleteById(any());
  }

  // =========================================================================
  // Functional groups (parent / members)
  // =========================================================================

  private static MuscleRequest memberRequest(MuscleGroup region, Long parentId) {
    return new MuscleRequest("Vastus Lateralis", region, parentId, null, null, null, null, null);
  }

  private static Muscle quadriceps() {
    return Muscle.builder().id(10L).medicalName("Quadriceps Femoris").muscleGroup(MuscleGroup.LEGS).build();
  }

  @Test
  @DisplayName("createMuscle: should link the muscle to its functional group")
  void testCreateMuscle_WithParent() {
    Muscle vastus = Muscle.builder().medicalName("Vastus Lateralis").muscleGroup(MuscleGroup.LEGS).build();
    Muscle quads = quadriceps();
    MuscleRequest request = memberRequest(MuscleGroup.LEGS, 10L);
    when(muscleRepository.existsByMedicalNameIgnoreCase("Vastus Lateralis")).thenReturn(false);
    when(muscleMapper.toEntity(request)).thenReturn(vastus);
    when(muscleRepository.findById(10L)).thenReturn(Optional.of(quads));
    when(muscleRepository.save(vastus)).thenReturn(vastus);
    when(muscleMapper.toResponse(vastus)).thenReturn(muscleResponse);

    muscleService.createMuscle(request);

    assertSame(quads, vastus.getParent());
  }

  @Test
  @DisplayName("createMuscle: should return 404 when the functional group does not exist")
  void testCreateMuscle_ParentNotFound() {
    MuscleRequest request = memberRequest(MuscleGroup.LEGS, 99L);
    when(muscleRepository.existsByMedicalNameIgnoreCase("Vastus Lateralis")).thenReturn(false);
    when(muscleMapper.toEntity(request)).thenReturn(new Muscle());
    when(muscleRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> muscleService.createMuscle(request));
    verify(muscleRepository, never()).save(any());
  }

  @Test
  @DisplayName("createMuscle: should reject a parent that already belongs to a group (no nesting)")
  void testCreateMuscle_NestedParent() {
    Muscle rectusFemoris =
        Muscle.builder().id(11L).medicalName("Rectus Femoris").muscleGroup(MuscleGroup.LEGS)
            .parent(quadriceps()).build();
    MuscleRequest request = memberRequest(MuscleGroup.LEGS, 11L);
    when(muscleRepository.existsByMedicalNameIgnoreCase("Vastus Lateralis")).thenReturn(false);
    when(muscleMapper.toEntity(request))
        .thenReturn(Muscle.builder().muscleGroup(MuscleGroup.LEGS).build());
    when(muscleRepository.findById(11L)).thenReturn(Optional.of(rectusFemoris));

    DataConflictException ex =
        assertThrows(DataConflictException.class, () -> muscleService.createMuscle(request));

    assertEquals("error.muscle.parent.nested", ex.getMessageKey());
    verify(muscleRepository, never()).save(any());
  }

  @Test
  @DisplayName("createMuscle: should reject a functional group in another region")
  void testCreateMuscle_ParentOtherRegion() {
    MuscleRequest request = memberRequest(MuscleGroup.ARMS, 10L);
    when(muscleRepository.existsByMedicalNameIgnoreCase("Vastus Lateralis")).thenReturn(false);
    when(muscleMapper.toEntity(request))
        .thenReturn(Muscle.builder().muscleGroup(MuscleGroup.ARMS).build());
    when(muscleRepository.findById(10L)).thenReturn(Optional.of(quadriceps()));

    DataConflictException ex =
        assertThrows(DataConflictException.class, () -> muscleService.createMuscle(request));

    assertEquals("error.muscle.parent.region", ex.getMessageKey());
  }

  @Test
  @DisplayName("updateMuscle: should reject a muscle as its own functional group")
  void testUpdateMuscle_SelfParent() {
    Muscle quads = quadriceps();
    MuscleRequest request =
        new MuscleRequest("Quadriceps Femoris", MuscleGroup.LEGS, 10L, null, null, null, null, null);
    when(muscleRepository.findById(10L)).thenReturn(Optional.of(quads));

    DataConflictException ex =
        assertThrows(DataConflictException.class, () -> muscleService.updateMuscle(10L, request));

    assertEquals("error.muscle.parent.self", ex.getMessageKey());
  }

  @Test
  @DisplayName("updateMuscle: should reject a group with muscles joining another group")
  void testUpdateMuscle_GroupCannotJoinGroup() {
    Muscle quads = quadriceps();
    quads.getMembers().add(Muscle.builder().id(11L).medicalName("Rectus Femoris").build());
    Muscle legs = Muscle.builder().id(20L).medicalName("Legs").muscleGroup(MuscleGroup.LEGS).build();
    MuscleRequest request =
        new MuscleRequest("Quadriceps Femoris", MuscleGroup.LEGS, 20L, null, null, null, null, null);
    when(muscleRepository.findById(10L)).thenReturn(Optional.of(quads));
    when(muscleRepository.findById(20L)).thenReturn(Optional.of(legs));

    DataConflictException ex =
        assertThrows(DataConflictException.class, () -> muscleService.updateMuscle(10L, request));

    assertEquals("error.muscle.parent.group", ex.getMessageKey());
    verify(muscleRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateMuscle: should reject a region change on a group with muscles")
  void testUpdateMuscle_GroupRegionChange() {
    Muscle quads = quadriceps();
    quads.getMembers().add(Muscle.builder().id(11L).medicalName("Rectus Femoris").build());
    MuscleRequest request =
        new MuscleRequest("Quadriceps Femoris", MuscleGroup.CORE, null, null, null, null, null, null);
    when(muscleRepository.findById(10L)).thenReturn(Optional.of(quads));

    DataConflictException ex =
        assertThrows(DataConflictException.class, () -> muscleService.updateMuscle(10L, request));

    assertEquals("error.muscle.group.region", ex.getMessageKey());
    verify(muscleMapper, never()).updateEntity(any(), any());
  }
}
