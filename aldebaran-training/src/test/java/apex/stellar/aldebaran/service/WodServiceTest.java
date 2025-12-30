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
    wod = Wod.builder()
        .id(1L)
        .title("Fran")
        .movements(new java.util.ArrayList<>())
        .modalities(new HashSet<>())
        .build();

    movement = Movement.builder()
        .id("GY-PU-001")
        .name("Pull-up")
        .category(Category.PULLING) // Modality = GYMNASTICS
        .build();

    WodMovementRequest movementRequest = new WodMovementRequest(
        "GY-PU-001", 1, "21-15-9", 0.0, null, 0, null, 0.0, null, 0, null, null
    );

    wodRequest = new WodRequest(
        "Fran",
        Wod.WodType.FOR_TIME,
        Wod.ScoreType.TIME,
        "Description",
        "Notes",
        true,
        600,
        0, 0,
        "21-15-9",
        List.of(movementRequest)
    );
  }

  @Test
  @DisplayName("findAllWods: should return projected summaries")
  void testFindAllWods() {
    // Mock Projection
    WodSummary summaryProjection = mock(WodSummary.class);
    when(summaryProjection.getId()).thenReturn(1L);
    when(summaryProjection.getTitle()).thenReturn("Fran");
    when(summaryProjection.getWodType()).thenReturn(Wod.WodType.FOR_TIME);
    when(summaryProjection.getScoreType()).thenReturn(Wod.ScoreType.TIME);

    when(wodRepository.findAllProjectedBy()).thenReturn(List.of(summaryProjection));

    List<WodSummaryResponse> results = wodService.findAllWods();

    assertEquals(1, results.size());
    assertEquals("Fran", results.get(0).title());
    verify(wodRepository).findAllProjectedBy();
  }

  @Test
  @DisplayName("getWod: should return mapped response when found")
  void testGetWod_Success() {
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    WodResponse response = wodService.getWod(1L);

    assertNotNull(response);
    verify(wodRepository).findByIdWithMovements(1L);
  }

  @Test
  @DisplayName("getWod: should throw ResourceNotFoundException when not found")
  void testGetWod_NotFound() {
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.empty());

    ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
        () -> wodService.getWod(1L));

    assertEquals("error.wod.not.found", ex.getMessageKey());
  }

  @Test
  @DisplayName("createWod: should link movements, set creator, and aggregate modalities")
  void testCreateWod_Success() {
    // Setup Mock behavior
    when(wodMapper.toEntity(wodRequest)).thenReturn(wod);
    when(movementRepository.findById("GY-PU-001")).thenReturn(Optional.of(movement));
    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    // Mock Static SecurityUtils
    try (MockedStatic<SecurityUtils> utilities = mockStatic(SecurityUtils.class)) {
      utilities.when(SecurityUtils::isAuthenticated).thenReturn(true);
      utilities.when(SecurityUtils::getCurrentUserId).thenReturn("100");

      WodResponse response = wodService.createWod(wodRequest);

      // Assertions
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
    when(wodMapper.toEntity(wodRequest)).thenReturn(wod);
    when(movementRepository.findById("GY-PU-001")).thenReturn(Optional.empty());

    ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
        () -> wodService.createWod(wodRequest));

    assertEquals("error.movement.not.found", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }
}