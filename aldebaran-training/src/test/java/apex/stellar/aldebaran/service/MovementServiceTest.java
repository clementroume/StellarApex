package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.dto.MuscleResponse;
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
import java.util.Collections;
import java.util.HashSet;
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
    // Set up basic entities
    // Use SQUAT category which belongs to WEIGHTLIFTING modality (Prefix WL-SQ)
    movement =
        Movement.builder()
            .id("WL-SQ-1234")
            .name("Back Squat")
            .category(Category.SQUAT)
            .targetedMuscles(new HashSet<>())
            .build();

    muscle = Muscle.builder().id(1L).medicalName("Quadriceps").build();

    // Setup Request DTO
    List<MovementMuscleRequest> muscleRequests =
        List.of(new MovementMuscleRequest("Quadriceps", MovementMuscle.MuscleRole.AGONIST, 1.0));

    request =
        new MovementRequest(
            "Back Squat",
            "BS",
            Category.SQUAT,
            Collections.emptySet(),
            Collections.emptySet(),
            muscleRequests,
            true,
            1.0,
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
        new MovementSummaryResponse("WL-SQ-1234", "Back Squat", "BS", Category.SQUAT, null);

    when(movementMapper.toSummary(projection)).thenReturn(expectedResponse);

    when(movementRepository.findProjectedByNameContainingIgnoreCase("Squat"))
        .thenReturn(List.of(projection));

    // When
    List<MovementSummaryResponse> results = movementService.searchMovements("Squat");

    // Then
    assertEquals(1, results.size());
    assertEquals("WL-SQ-1234", results.getFirst().id());

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
  @DisplayName("getMovementsByCategory: should return filtered list")
  void testGetMovementsByCategory() {
    // Given
    MovementSummary projection = mock(MovementSummary.class);
    when(movementRepository.findProjectedByCategory(Category.SQUAT))
        .thenReturn(List.of(projection));

    // When
    List<MovementSummaryResponse> results = movementService.getMovementsByCategory(Category.SQUAT);

    // Then
    assertEquals(1, results.size());
    verify(movementRepository).findProjectedByCategory(Category.SQUAT);
  }

  @Test
  @DisplayName("getMovement: should return details when found")
  void testGetMovement_Success() {
    // Given
    when(movementRepository.findById("WL-SQ-1234")).thenReturn(Optional.of(movement));
    when(movementMapper.toResponse(movement)).thenReturn(mock(MovementResponse.class));

    // When
    MovementResponse result = movementService.getMovement("WL-SQ-1234");

    // Then
    assertNotNull(result);
    verify(movementRepository).findById("WL-SQ-1234");
  }

  @Test
  @DisplayName("getMovement: should throw exception when ID not found")
  void testGetMovement_NotFound() {
    when(movementRepository.findById("INVALID")).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> movementService.getMovement("INVALID"));
  }

  @Test
  @DisplayName("createMovement: should generate ID and link muscles")
  void testCreateMovement_Success() {
    // Mocks
    when(movementMapper.toEntity(request)).thenReturn(movement);
    MuscleResponse muscleResponse = mock(MuscleResponse.class);
    when(muscleResponse.id()).thenReturn(1L);
    when(muscleService.getMuscle("Quadriceps")).thenReturn(muscleResponse);
    when(muscleRepository.getReferenceById(1L)).thenReturn(muscle);
    when(movementMapper.toMuscleEntity(any())).thenReturn(new MovementMuscle());
    when(movementRepository.save(any(Movement.class))).thenReturn(movement);
    when(movementMapper.toResponse(movement)).thenReturn(mock(MovementResponse.class));

    // Execute
    MovementResponse response = movementService.createMovement(request);

    // Verify ID Generation logic
    // Category SQUAT -> Modality WEIGHTLIFTING (WL) + Code (SQ) -> Prefix "WL-SQ"
    assertTrue(
        movement.getId().startsWith("WL-SQ"),
        "ID should start with correct semantic prefix (WL-SQ)");

    // Verify Muscle Linking
    verify(muscleService).getMuscle("Quadriceps");
    assertEquals(1, movement.getTargetedMuscles().size(), "Should have linked 1 muscle");

    assertNotNull(response);
  }

  @Test
  @DisplayName("createMovement: should generate semantic ID based on category")
  void testCreateMovement_GeneratesSemanticId() {
    // Given: A movement entity from mapper (ID is null or temporary)
    Movement newMovement =
        Movement.builder()
            .name("Deadlift")
            .category(
                Category.DEADLIFT) // Should generate prefix "WL-DL" (Weightlifting - Deadlift)
            .targetedMuscles(new HashSet<>())
            .build();

    when(movementMapper.toEntity(request)).thenReturn(newMovement);
    MuscleResponse muscleResponse = mock(MuscleResponse.class);
    when(muscleResponse.id()).thenReturn(1L);
    when(muscleService.getMuscle(anyString())).thenReturn(muscleResponse);
    when(muscleRepository.getReferenceById(1L)).thenReturn(muscle);
    when(movementMapper.toMuscleEntity(any())).thenReturn(new MovementMuscle());
    when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> inv.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(mock(MovementResponse.class));

    // When
    movementService.createMovement(request);

    // Then
    assertNotNull(newMovement.getId());
    assertTrue(
        newMovement.getId().startsWith("WL-DL-"),
        "ID should start with semantic prefix 'WL-DL-' but was " + newMovement.getId());
  }

  @Test
  @DisplayName("createMovement: should throw exception if muscle name is invalid")
  void testCreateMovement_InvalidMuscle() {
    when(movementMapper.toEntity(request)).thenReturn(movement);

    when(muscleService.getMuscle("Quadriceps"))
        .thenThrow(new ResourceNotFoundException("error.muscle.not.found"));

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
    String id = "WL-SQ-1234";

    // Simulate existing movement having old muscles
    movement.getTargetedMuscles().add(new MovementMuscle());

    when(movementRepository.findById(id)).thenReturn(Optional.of(movement));
    MuscleResponse muscleResponse = mock(MuscleResponse.class);
    when(muscleResponse.id()).thenReturn(1L);
    when(muscleService.getMuscle("Quadriceps")).thenReturn(muscleResponse);
    when(muscleRepository.getReferenceById(1L)).thenReturn(muscle);
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
    when(movementRepository.findById("INVALID")).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> movementService.updateMovement("INVALID", request));
  }

  @Test
  @DisplayName("updateMovement: should throw exception if muscle name is invalid")
  void testUpdateMovement_InvalidMuscle() {
    String id = "WL-SQ-1234";
    when(movementRepository.findById(id)).thenReturn(Optional.of(movement));
    when(muscleService.getMuscle("Quadriceps"))
        .thenThrow(new ResourceNotFoundException("error.muscle.not.found"));

    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class, () -> movementService.updateMovement(id, request));

    assertEquals("error.muscle.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("updateMovement: should allow renaming if new name is unique")
  void testUpdateMovement_RenameSuccess() {
    // Given
    String id = "WL-SQ-1234";
    // Le mouvement en base a un ancien nom
    Movement existingMovement =
        Movement.builder().id(id).name("Old Squat").targetedMuscles(new HashSet<>()).build();

    when(movementRepository.findById(id)).thenReturn(Optional.of(existingMovement));

    // Le request demande à le renommer en "Back Squat" (défini dans le setUp).
    // On simule que ce nom n'existe pas encore
    when(movementRepository.existsByNameIgnoreCase(request.name())).thenReturn(false);

    // Mocks standards pour la sauvegarde et les muscles
    MuscleResponse muscleResponse = mock(MuscleResponse.class);
    when(muscleResponse.id()).thenReturn(1L);
    when(muscleService.getMuscle("Quadriceps")).thenReturn(muscleResponse);
    when(muscleRepository.getReferenceById(1L)).thenReturn(muscle);
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
    String id = "WL-SQ-1234";
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
