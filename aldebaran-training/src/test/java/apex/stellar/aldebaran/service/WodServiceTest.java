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
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

    // Using unpaged for simplicity, but the service calls findAllProjectedBy(pageable)
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
    when(movementRepository.findById("GY-PU-001")).thenReturn(Optional.of(movement));
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

      // Verify Modality Aggregation: Pull-up is GYMNASTICS
      assertTrue(wod.getModalities().contains(Modality.GYMNASTICS));

      verify(movementRepository).findById("GY-PU-001");
      verify(wodRepository).save(wod);
    }
  }

  @Test
  @DisplayName("createWod: should throw exception if movement ID is invalid")
  void testCreateWod_InvalidMovement() {
    // Given
    when(wodMapper.toEntity(wodRequest)).thenReturn(wod);
    when(movementRepository.findById("GY-PU-001")).thenReturn(Optional.empty());

    // When & Then
    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> wodService.createWod(wodRequest));

    assertEquals("error.movement.not.found", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateWod: should replace movements and update metadata")
  void testUpdateWod_Success() {
    // Given
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));
    // Mapper behavior for update (void method)
    doAnswer(
            invocation -> {
              Wod target = invocation.getArgument(1);
              WodRequest source = invocation.getArgument(0);
              target.setTitle(source.title()); // Simulate mapper update
              return null;
            })
        .when(wodMapper)
        .updateEntity(any(WodRequest.class), any(Wod.class));

    when(movementRepository.findById("GY-PU-001")).thenReturn(Optional.of(movement));
    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    // When
    WodRequest updateRequest =
        new WodRequest(
            "Fran (Updated)", // Title changed
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
    // Verify movements were cleared and re-added (size should be 1 from the new request)
    assertEquals(1, wod.getMovements().size());
    verify(wodRepository).save(wod);
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
