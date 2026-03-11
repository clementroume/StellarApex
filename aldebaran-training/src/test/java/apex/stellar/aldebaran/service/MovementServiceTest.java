package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.MovementMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.projection.MovementSummary;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovementServiceTest {

  @Mock private MuscleRepository muscleRepository;
  @Mock private MuscleService muscleService;
  @Mock private MovementRepository movementRepository;
  @Mock private MovementMapper movementMapper;
  @InjectMocks private MovementService movementService;

  private Movement movement;
  private MovementRequest request;
  private Muscle muscle;

  @BeforeEach
  void setUp() {
    movement =
        Movement.builder()
            .id(1L)
            .name("Back Squat")
            .category(Category.SQUAT)
            .targetedMuscles(new HashSet<>())
            .build();

    muscle = Muscle.builder().id(1L).medicalName("Quadriceps").build();

    Set<MovementMuscleRequest> muscleRequests =
        Set.of(new MovementMuscleRequest(1L, MovementMuscle.MuscleRole.AGONIST, 1.0));

    request =
        new MovementRequest(
            "Back Squat",
            "BS",
            Category.SQUAT,
            Collections.emptySet(),
            Collections.emptySet(),
            muscleRequests,
            null,
            null,
            null,
            null,
            null,
            null);
  }

  @Test
  @DisplayName("searchMovements: should return mapped summaries from projection")
  void testSearchMovements() {
    // Given
    MovementSummary projection = mock(MovementSummary.class);

    MovementSummaryResponse expectedResponse =
        new MovementSummaryResponse(1L, "Back Squat", "BS", Category.SQUAT, null);

    when(movementMapper.toSummary(projection)).thenReturn(expectedResponse);

    when(movementRepository.findProjectedByNameContainingIgnoreCase("Squat"))
        .thenReturn(List.of(projection));

    // When
    List<MovementSummaryResponse> results = movementService.searchMovements("Squat");

    // Then
    assertEquals(1, results.size());
    assertEquals(1L, results.getFirst().id());

    verify(movementRepository).findProjectedByNameContainingIgnoreCase("Squat");
    verify(movementMapper).toSummary(projection);
  }

  @Test
  @DisplayName("searchMovements: should return all when query is empty")
  void testSearchMovements_EmptyQuery() {
    // Given
    MovementSummary projection = mock(MovementSummary.class);
    when(movementRepository.findAllProjectedBy()).thenReturn(List.of(projection));

    // When
    List<MovementSummaryResponse> results = movementService.searchMovements("");

    // Then
    assertEquals(1, results.size());
    verify(movementRepository).findAllProjectedBy();
  }

  @Test
  @DisplayName("getMovement: should return details when found")
  void testGetMovement_Success() {
    // Given
    when(movementRepository.findById(1L)).thenReturn(Optional.of(movement));
    when(movementMapper.toResponse(movement)).thenReturn(mock(MovementResponse.class));

    // When
    MovementResponse result = movementService.getMovement(1L);

    // Then
    assertNotNull(result);
    verify(movementRepository).findById(1L);
  }

  @Test
  @DisplayName("getMovement: should throw exception when ID not found")
  void testGetMovement_NotFound() {
    when(movementRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> movementService.getMovement(999L));
  }

  @Test
  @DisplayName("createMovement: should generate ID and link muscles")
  void testCreateMovement_Success() {
    // Mocks
    when(movementMapper.toEntity(request)).thenReturn(movement);
    when(muscleRepository.findById(1L)).thenReturn(Optional.of(muscle));
    when(movementMapper.toMuscleEntity(any())).thenReturn(new MovementMuscle());
    when(movementRepository.save(any(Movement.class))).thenReturn(movement);
    when(movementMapper.toResponse(movement)).thenReturn(mock(MovementResponse.class));

    // Execute
    MovementResponse response = movementService.createMovement(request);

    // Verify Muscle Linking
    verify(muscleRepository).findById(1L);
    assertEquals(1, movement.getTargetedMuscles().size(), "Should have linked 1 muscle");

    assertNotNull(response);
  }

  @Test
  @DisplayName("createMovement: should throw exception if muscle name is invalid")
  void testCreateMovement_InvalidMuscle() {
    when(movementMapper.toEntity(request)).thenReturn(movement);

    when(muscleRepository.findById(1L)).thenReturn(Optional.empty());

    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class, () -> movementService.createMovement(request));

    assertEquals("error.muscle.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("createMovement: should throw DataConflictException if name already exists")
  void testCreateMovement_DuplicateName() {
    // GIVEN
    when(movementRepository.existsByNameIgnoreCase(request.name())).thenReturn(true);

    // WHEN / THEN
    DataConflictException ex =
        assertThrows(DataConflictException.class, () -> movementService.createMovement(request));

    assertEquals("error.movement.duplicate", ex.getMessageKey());
    verify(movementRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateMovement: should clear and re-link muscles")
  void testUpdateMovement_Success() {
    Long id = 1L;

    // Simulate existing movement having old muscles
    movement.getTargetedMuscles().add(new MovementMuscle());

    when(movementRepository.findById(id)).thenReturn(Optional.of(movement));
    when(muscleRepository.findById(1L)).thenReturn(Optional.of(muscle));
    when(movementMapper.toMuscleEntity(any())).thenReturn(new MovementMuscle());
    when(movementRepository.save(movement)).thenReturn(movement);
    when(movementMapper.toResponse(movement)).thenReturn(mock(MovementResponse.class));

    movementService.updateMovement(id, request);

    // Verify update flow
    verify(movementMapper).updateEntity(request, movement);
    // Logic check: The list size is 1 because we cleared the old one and added the new request one
    assertEquals(1, movement.getTargetedMuscles().size());
    verify(movementRepository).save(movement);
  }

  @Test
  @DisplayName("updateMovement: should throw exception when ID not found")
  void testUpdateMovement_NotFound() {
    when(movementRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> movementService.updateMovement(999L, request));
  }

  @Test
  @DisplayName("updateMovement: should throw exception if muscle name is invalid")
  void testUpdateMovement_InvalidMuscle() {
    Long id = 1L;
    when(movementRepository.findById(id)).thenReturn(Optional.of(movement));
    when(muscleRepository.findById(1L)).thenReturn(Optional.empty());

    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class, () -> movementService.updateMovement(id, request));

    assertEquals("error.muscle.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("updateMovement: should allow renaming if new name is unique")
  void testUpdateMovement_RenameSuccess() {
    // Given
    Long id = 1L;
    // Le mouvement en base a un ancien nom
    Movement existingMovement =
        Movement.builder().id(id).name("Old Squat").targetedMuscles(new HashSet<>()).build();

    when(movementRepository.findById(id)).thenReturn(Optional.of(existingMovement));

    // Le request demande à le renommer en "Back Squat" (défini dans le setUp).
    // On simule que ce nom n'existe pas encore
    when(movementRepository.existsByNameIgnoreCase(request.name())).thenReturn(false);

    // Mocks standards pour la sauvegarde et les muscles
    when(muscleRepository.findById(1L)).thenReturn(Optional.of(muscle));
    when(movementMapper.toMuscleEntity(any())).thenReturn(new MovementMuscle());

    when(movementRepository.save(existingMovement)).thenReturn(existingMovement);
    when(movementMapper.toResponse(existingMovement)).thenReturn(mock(MovementResponse.class));

    // When
    MovementResponse result = movementService.updateMovement(id, request);

    // Then
    assertNotNull(result);
    verify(movementRepository).existsByNameIgnoreCase(request.name());
    verify(movementRepository).save(existingMovement);
  }

  @Test
  @DisplayName("updateMovement: should throw DataConflictException when renaming to existing name")
  void testUpdateMovement_Conflict() {
    // Given
    Long id = 1L;
    Movement existingMovement = Movement.builder().id(id).name("Old Squat").build();
    when(movementRepository.findById(id)).thenReturn(Optional.of(existingMovement));
    when(movementRepository.existsByNameIgnoreCase(request.name())).thenReturn(true);

    // When & Then
    DataConflictException ex =
        assertThrows(
            DataConflictException.class, () -> movementService.updateMovement(id, request));

    assertEquals("error.movement.duplicate", ex.getMessageKey());
    verify(movementRepository, never()).save(any());
  }
}
