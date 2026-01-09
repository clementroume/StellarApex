package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
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

  @Mock private MovementRepository movementRepository;
  @Mock private MuscleRepository muscleRepository;
  @Mock private MovementMapper movementMapper;

  @InjectMocks private MovementService movementService;

  private Movement movement;
  private MovementRequest request;
  private Muscle muscle;

  @BeforeEach
  void setUp() {
    // Setup basic entities
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
    when(projection.getId()).thenReturn("WL-SQ-1234");
    when(projection.getName()).thenReturn("Back Squat");
    when(projection.getNameAbbreviation()).thenReturn("BS");
    when(projection.getCategory()).thenReturn(Category.SQUAT);
    when(projection.getImageUrl()).thenReturn(null);

    when(movementRepository.findProjectedByNameContainingIgnoreCase("Squat"))
        .thenReturn(List.of(projection));

    // When
    List<MovementSummaryResponse> results = movementService.searchMovements("Squat");

    // Then
    assertEquals(1, results.size());
    assertEquals("WL-SQ-1234", results.get(0).id());

    // Verify we called the Optimized Projection method, not the full entity search
    verify(movementRepository).findProjectedByNameContainingIgnoreCase("Squat");
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
    when(muscleRepository.findByMedicalName("Quadriceps")).thenReturn(Optional.of(muscle));
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
    verify(muscleRepository).findByMedicalName("Quadriceps");
    assertEquals(1, movement.getTargetedMuscles().size(), "Should have linked 1 muscle");

    assertNotNull(response);
  }

  @Test
  @DisplayName("createMovement: should generate semantic ID based on category")
  void testCreateMovement_GeneratesSemanticId() {
    // Given: A movement entity from mapper (ID is null or temporary)
    Movement newMovement = Movement.builder()
        .name("Deadlift")
        .category(Category.DEADLIFT) // Should generate prefix "WL-DL" (Weightlifting - Deadlift)
        .targetedMuscles(new HashSet<>())
        .build();

    when(movementMapper.toEntity(request)).thenReturn(newMovement);
    when(muscleRepository.findByMedicalName(any())).thenReturn(Optional.of(muscle));
    when(movementMapper.toMuscleEntity(any())).thenReturn(new MovementMuscle());
    when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> inv.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(mock(MovementResponse.class));

    // When
    movementService.createMovement(request);

    // Then
    assertNotNull(newMovement.getId());
    assertTrue(newMovement.getId().startsWith("WL-DL-"), "ID should start with semantic prefix 'WL-DL-' but was " + newMovement.getId());
  }

  @Test
  @DisplayName("createMovement: should throw exception if muscle name is invalid")
  void testCreateMovement_InvalidMuscle() {
    when(movementMapper.toEntity(request)).thenReturn(movement);
    when(muscleRepository.findByMedicalName("Quadriceps")).thenReturn(Optional.empty());

    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class, () -> movementService.createMovement(request));

    assertEquals("error.muscle.name.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("updateMovement: should clear and re-link muscles")
  void testUpdateMovement_Success() {
    String id = "WL-SQ-1234";

    // Simulate existing movement having old muscles
    movement.getTargetedMuscles().add(new MovementMuscle());

    when(movementRepository.findById(id)).thenReturn(Optional.of(movement));
    when(muscleRepository.findByMedicalName("Quadriceps")).thenReturn(Optional.of(muscle));
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
}
