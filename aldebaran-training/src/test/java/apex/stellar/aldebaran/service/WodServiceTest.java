package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.config.SecurityUtils;
import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.exception.WodLockedException;
import apex.stellar.aldebaran.mapper.WodMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WodServiceTest {

  @Mock private WodRepository wodRepository;
  @Mock private MovementRepository movementRepository;
  @Mock private WodScoreRepository wodScoreRepository;
  @Mock private WodMapper wodMapper;

  @InjectMocks private WodService wodService;

  private Wod wod;
  private Movement movement;
  private WodRequest wodRequest;

  @BeforeEach
  void setUp() {
    wod =
        Wod.builder()
            .id(1L)
            .title("Fran")
            .movements(new java.util.ArrayList<>())
            .modalities(new HashSet<>())
            .build();

    movement =
        Movement.builder()
            .id("GY-PU-001")
            .name("Pull-up")
            .category(Category.PULLING) // Modality = GYMNASTICS
            .build();

    // WodMovementRequest constructor matches the DTO definition
    WodMovementRequest movementRequest =
        new WodMovementRequest(
            "GY-PU-001", 1, "21-15-9", 0.0, null, 0, null, 0.0, null, 0, null, null);

    wodRequest =
        new WodRequest(
            "Fran",
            WodType.FOR_TIME,
            ScoreType.TIME,
            "Description",
            "Notes",
            true,
            600,
            0,
            0,
            "21-15-9",
            List.of(movementRequest));
  }

  @Test
  @DisplayName("getWods: should return projected summaries using Pageable")
  void testGetWods() {
    // Given
    WodSummary summaryProjection = mock(WodSummary.class);
    when(summaryProjection.getId()).thenReturn(1L);
    when(summaryProjection.getTitle()).thenReturn("Fran");
    when(summaryProjection.getWodType()).thenReturn(WodType.FOR_TIME);
    when(summaryProjection.getScoreType()).thenReturn(ScoreType.TIME);

    // Using unpaged for simplicity
    Pageable pageable = Pageable.unpaged();
    when(wodRepository.findAllProjectedBy(pageable)).thenReturn(List.of(summaryProjection));

    // When
    List<WodSummaryResponse> results = wodService.getWods(null, null, pageable);

    // Then
    assertEquals(1, results.size());
    assertEquals("Fran", results.get(0).title());
    verify(wodRepository).findAllProjectedBy(pageable);
  }

  @Test
  @DisplayName("getWodDetail: should return mapped response using optimized fetch")
  void testGetWodDetail_Success() {
    // Given
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    // When
    WodResponse response = wodService.getWodDetail(1L);

    // Then
    assertNotNull(response);
    verify(wodRepository).findByIdWithMovements(1L);
  }

  @Test
  @DisplayName("getWodDetail: should throw ResourceNotFoundException when not found")
  void testGetWodDetail_NotFound() {
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.empty());

    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> wodService.getWodDetail(1L));

    assertEquals("error.wod.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("createWod: should link movements, set creator, and aggregate modalities")
  void testCreateWod_Success() {
    // Given
    when(wodMapper.toEntity(wodRequest)).thenReturn(wod);
    // CORRECTION ICI: Utilisation de findAllById au lieu de findById
    when(movementRepository.findAllById(any())).thenReturn(List.of(movement));

    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn("100");

      // When
      WodResponse response = wodService.createWod(wodRequest);

      // Then
      assertNotNull(response);
      assertEquals(100L, wod.getCreatorId());
      assertEquals(1, wod.getMovements().size());
      assertTrue(wod.getModalities().contains(Modality.GYMNASTICS));

      verify(movementRepository).findAllById(any());
      verify(wodRepository).save(wod);
    }
  }

  @Test
  @DisplayName("createWod: should optimize DB calls by fetching all movements in batch")
  void testCreateWod_BatchOptimization() {
    // Given: A request with 2 distinct movements
    WodMovementRequest m1 = new WodMovementRequest("GY-PU-001", 1, "21", 0.0, null, 0, null, 0.0, null, 0, null, null);
    WodMovementRequest m2 = new WodMovementRequest("WL-SQ-001", 2, "15", 0.0, null, 0, null, 0.0, null, 0, null, null);
    
    WodRequest batchRequest = new WodRequest(
        "Batch WOD", WodType.FOR_TIME, ScoreType.TIME, null, null, true, 
        0, 0, 0, "21-15", List.of(m1, m2)
    );

    Movement move1 = Movement.builder().id("GY-PU-001").category(Category.PULLING).build();
    Movement move2 = Movement.builder().id("WL-SQ-001").category(Category.SQUAT).build();

    // Mock Mapper
    Wod newWod = new Wod();
    newWod.setMovements(new java.util.ArrayList<>());
    newWod.setModalities(new HashSet<>());
    when(wodMapper.toEntity(batchRequest)).thenReturn(newWod);
    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(any(Wod.class))).thenReturn(newWod);
    when(wodMapper.toResponse(any(Wod.class))).thenReturn(mock(WodResponse.class));

    // Mock Repository: Expect a single call with a Set containing both IDs
    when(movementRepository.findAllById(argThat(ids -> 
        ids instanceof Set && ((Set<?>) ids).size() == 2 && ((Set<?>) ids).containsAll(List.of("GY-PU-001", "WL-SQ-001"))
    ))).thenReturn(List.of(move1, move2));

    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn("100");

      // When
      wodService.createWod(batchRequest);

      // Then
      // Verify findAllById is called exactly once with the collection of IDs
      verify(movementRepository, times(1)).findAllById(any());
      verify(movementRepository, never()).findById(any()); // Ensure no N+1 individual lookups
    }
  }

  @Test
  @DisplayName("createWod: should throw exception if movement ID is invalid")
  void testCreateWod_InvalidMovement() {
    // Given
    when(wodMapper.toEntity(wodRequest)).thenReturn(wod);
    // CORRECTION ICI: On simule une liste vide retournée par findAllById pour déclencher l'erreur
    when(movementRepository.findAllById(any())).thenReturn(List.of());

    // When & Then
    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> wodService.createWod(wodRequest));

    assertEquals("error.movement.not.found", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateWod: should update when NO scores exist")
  void testUpdateWod_Success() {
    // Given
    when(wodScoreRepository.existsByWodId(1L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));

    // Mapper behavior
    doAnswer(
            invocation -> {
              WodRequest source = invocation.getArgument(0);
              Wod target = invocation.getArgument(1);
              target.setTitle(source.title());
              return null;
            })
        .when(wodMapper)
        .updateEntity(any(WodRequest.class), any(Wod.class));

    // CORRECTION ICI: Utilisation de findAllById
    when(movementRepository.findAllById(any())).thenReturn(List.of(movement));

    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    // When
    WodRequest updateRequest =
        new WodRequest(
            "Fran (Updated)",
            WodType.FOR_TIME,
            ScoreType.TIME,
            "New Desc",
            "New Notes",
            true,
            null,
            null,
            null,
            null,
            List.of(
                new WodMovementRequest(
                    "GY-PU-001", 1, "15-12-9", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    wodService.updateWod(1L, updateRequest);

    // Then
    verify(wodMapper).updateEntity(updateRequest, wod);
    assertEquals(1, wod.getMovements().size());
    verify(wodRepository).save(wod);
  }

  @Test
  @DisplayName("updateWod: should throw WodLockedException when scores exist")
  void testUpdateWod_Locked() {
    // Given
    when(wodScoreRepository.existsByWodId(1L)).thenReturn(true);

    // When & Then
    WodLockedException ex =
        assertThrows(WodLockedException.class, () -> wodService.updateWod(1L, wodRequest));

    assertEquals("error.wod.locked", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("deleteWod: should delete entity when found")
  void testDeleteWod_Success() {
    when(wodRepository.existsById(1L)).thenReturn(true);

    wodService.deleteWod(1L);

    verify(wodRepository).deleteById(1L);
  }

  @Test
  @DisplayName("deleteWod: should throw exception when ID not found")
  void testDeleteWod_NotFound() {
    when(wodRepository.existsById(99L)).thenReturn(false);

    assertThrows(ResourceNotFoundException.class, () -> wodService.deleteWod(99L));
    verify(wodRepository, never()).deleteById(any());
  }
}
