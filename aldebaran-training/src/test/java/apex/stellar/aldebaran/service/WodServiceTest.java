package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.SecurityService;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WodServiceTest {

  @Mock private WodRepository wodRepository;
  @Mock private MovementRepository movementRepository;
  @Mock private WodScoreRepository wodScoreRepository;
  @Mock private WodMapper wodMapper;
  @Mock private SecurityService securityService;

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

    movement = Movement.builder().id("GY-PU-001").category(Category.PULLING).build();

    WodMovementRequest movementRequest =
        new WodMovementRequest(
            "GY-PU-001", 1, "21-15-9", 0.0, null, 0, null, 0.0, null, 0, null, null);

    wodRequest =
        new WodRequest(
            "Fran",
            WodType.FOR_TIME,
            ScoreType.TIME,
            "Desc",
            "Notes",
            null, // authorId
            null, // gymId
            true, // isPublic
            600, // timeCap
            0, // emomInterval
            0, // emomRounds
            "21-15-9", // repScheme
            List.of(movementRequest) // movements
            );
  }

  // =========================================================================
  // TEST: getWods (Security Context Passing)
  // =========================================================================

  @Test
  @DisplayName("getWods: Standard User should pass ID and isAdmin=false to repository")
  void testGetWods_StandardUser() {
    // Given
    AldebaranUserPrincipal user = new AldebaranUserPrincipal(100L, 50L, "ATHLETE", List.of());
    Pageable pageable = Pageable.unpaged();

    WodSummary projection = mock(WodSummary.class);
    when(wodRepository.findAllSecure(100L, 50L, false, pageable))
        .thenReturn(List.of(projection));

    when(securityService.isAdmin(user)).thenReturn(false);

    // When
    List<WodSummaryResponse> results = wodService.getWods(null, null, null, pageable, user);

    // Then
    assertNotNull(results);
    verify(wodRepository).findAllSecure(100L, 50L, false, pageable);
  }

  @Test
  @DisplayName("getWods: Admin User should pass isAdmin=true to repository")
  void testGetWods_AdminUser() {
    // Given
    AldebaranUserPrincipal admin = new AldebaranUserPrincipal(999L, null, "ADMIN", List.of());
    Pageable pageable = Pageable.unpaged();

    WodSummary projection = mock(WodSummary.class);
    // Note: userId/gymId don't matter much when isAdmin=true, but verify they are passed correctly
    when(wodRepository.findAllSecure(eq(999L), isNull(), eq(true), eq(pageable)))
        .thenReturn(List.of(projection));

    when(securityService.isAdmin(admin)).thenReturn(true);

    // When
    wodService.getWods(null, null, null, pageable, admin);

    // Then
    verify(wodRepository).findAllSecure(eq(999L), isNull(), eq(true), eq(pageable));
  }

  @Test
  @DisplayName("getWods: Search filter should use secure query")
  void testGetWods_Search() {
    // Given
    AldebaranUserPrincipal user = new AldebaranUserPrincipal(100L, null, "USER", List.of());

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findByTitleSecure(eq("Fran"), eq(100L), isNull(), eq(false)))
        .thenReturn(List.of());

    // When
    wodService.getWods("Fran", null, null, Pageable.unpaged(), user);

    // Then
    verify(wodRepository).findByTitleSecure(eq("Fran"), eq(100L), isNull(), eq(false));
  }

  @Test
  @DisplayName("getWods: Type filter should use secure query")
  void testGetWods_Type() {
    AldebaranUserPrincipal user = new AldebaranUserPrincipal(100L, null, "USER", List.of());
    Pageable pageable = Pageable.unpaged();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findByTypeSecure(
            eq(WodType.AMRAP), eq(100L), isNull(), eq(false), eq(pageable)))
        .thenReturn(List.of());

    // When
    wodService.getWods(null, WodType.AMRAP, null, pageable, user);

    // Then
    verify(wodRepository)
        .findByTypeSecure(eq(WodType.AMRAP), eq(100L), isNull(), eq(false), eq(pageable));
  }

  @Test
  @DisplayName("getWods: Movement filter should use secure query")
  void testGetWods_Movement() {
    AldebaranUserPrincipal user = new AldebaranUserPrincipal(100L, null, "USER", List.of());
    Pageable pageable = Pageable.unpaged();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findByMovementSecure(
            eq("GY-PU-001"), eq(100L), isNull(), eq(false), eq(pageable)))
        .thenReturn(List.of());

    // When
    wodService.getWods(null, null, "GY-PU-001", pageable, user);

    // Then
    verify(wodRepository)
        .findByMovementSecure(eq("GY-PU-001"), eq(100L), isNull(), eq(false), eq(pageable));
  }

  // =========================================================================
  // TEST: Operations standard (Create/Update/Delete/Detail)
  // =========================================================================

  @Test
  @DisplayName("getWodDetail: should return mapped response using optimized fetch")
  void testGetWodDetail_Success() {
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    WodResponse response = wodService.getWodDetail(1L);

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
    when(wodMapper.toEntity(wodRequest)).thenReturn(wod);
    when(movementRepository.findAllById(any())).thenReturn(List.of(movement));
    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    when(securityService.getCurrentUserId()).thenReturn(100L);

    WodResponse response = wodService.createWod(wodRequest);

    assertNotNull(response);
    assertEquals(100L, wod.getAuthorId()); // Vérifie AuthorId
    assertEquals(1, wod.getMovements().size());
    assertTrue(wod.getModalities().contains(Modality.GYMNASTICS));

    verify(movementRepository).findAllById(any());
    verify(wodRepository).save(wod);
  }

  @Test
  @DisplayName("createWod: should optimize DB calls by fetching all movements in batch")
  void testCreateWod_BatchOptimization() {
    WodMovementRequest m1 =
        new WodMovementRequest("GY-PU-001", 1, "21", 0.0, null, 0, null, 0.0, null, 0, null, null);
    WodMovementRequest m2 =
        new WodMovementRequest("WL-SQ-001", 2, "15", 0.0, null, 0, null, 0.0, null, 0, null, null);

    WodRequest batchRequest =
        new WodRequest(
            "Batch WOD",
            WodType.FOR_TIME,
            ScoreType.TIME,
            null,
            null,
            null, // authorId
            null, // gymId
            true,
            0,
            0,
            0,
            "21-15",
            List.of(m1, m2));

    Movement move1 = Movement.builder().id("GY-PU-001").category(Category.PULLING).build();
    Movement move2 = Movement.builder().id("WL-SQ-001").category(Category.SQUAT).build();

    Wod newWod = new Wod();
    newWod.setMovements(new java.util.ArrayList<>());
    newWod.setModalities(new HashSet<>());
    when(wodMapper.toEntity(batchRequest)).thenReturn(newWod);
    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(any(Wod.class))).thenReturn(newWod);
    when(wodMapper.toResponse(any(Wod.class))).thenReturn(mock(WodResponse.class));

    when(movementRepository.findAllById(any())).thenReturn(List.of(move1, move2));

    when(securityService.getCurrentUserId()).thenReturn(100L);

    wodService.createWod(batchRequest);

    verify(movementRepository, times(1)).findAllById(any());
    verify(movementRepository, never()).findById(any());
  }

  @Test
  @DisplayName("createWod: should throw exception if movement ID is invalid")
  void testCreateWod_InvalidMovement() {
    when(wodMapper.toEntity(wodRequest)).thenReturn(wod);
    when(movementRepository.findAllById(any())).thenReturn(List.of()); // Liste vide = non trouvé

    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> wodService.createWod(wodRequest));

    assertEquals("error.movement.not.found", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateWod: should update when NO scores exist")
  void testUpdateWod_Success() {
    when(wodScoreRepository.existsByWodId(1L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));

    doAnswer(
            invocation -> {
              WodRequest source = invocation.getArgument(0);
              Wod target = invocation.getArgument(1);
              target.setTitle(source.title());
              return null;
            })
        .when(wodMapper)
        .updateEntity(any(WodRequest.class), any(Wod.class));

    when(movementRepository.findAllById(any())).thenReturn(List.of(movement));
    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    WodRequest updateRequest =
        new WodRequest(
            "Fran (Updated)",
            WodType.FOR_TIME,
            ScoreType.TIME,
            "New Desc",
            "New Notes",
            null,
            null,
            true,
            null,
            null,
            null,
            null,
            List.of(
                new WodMovementRequest(
                    "GY-PU-001", 1, "15-12-9", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    wodService.updateWod(1L, updateRequest);

    verify(wodMapper).updateEntity(updateRequest, wod);
    assertEquals(1, wod.getMovements().size());
    verify(wodRepository).save(wod);
  }

  @Test
  @DisplayName("updateWod: Smart Merge should preserve existing entities if order matches")
  void testUpdateWod_SmartMerge_PreservesEntities() {
    // Given: Existing WOD with one movement (ID 500)
    WodMovement existingMovement = new WodMovement();
    existingMovement.setId(500L);
    existingMovement.setOrderIndex(1);
    existingMovement.setRepsScheme("21-15-9");
    existingMovement.setMovement(movement);
    existingMovement.setWod(wod);

    wod.getMovements().add(existingMovement);

    when(wodScoreRepository.existsByWodId(1L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));
    when(movementRepository.findAllById(any())).thenReturn(List.of(movement));
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    WodMovement tempMovement = new WodMovement();
    tempMovement.setRepsScheme("15-12-9"); // New value
    tempMovement.setOrderIndex(1);
    when(wodMapper.toWodMovementEntity(any())).thenReturn(tempMovement);

    WodRequest updateRequest =
        new WodRequest(
            "Fran", WodType.FOR_TIME, ScoreType.TIME, null, null, null, null, true, null, null, null, null,
            List.of(new WodMovementRequest("GY-PU-001", 1, "15-12-9", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    // When
    wodService.updateWod(1L, updateRequest);

    // Then
    assertEquals(1, wod.getMovements().size());
    WodMovement resultMovement = wod.getMovements().getFirst();
    assertEquals(500L, resultMovement.getId(), "Should preserve entity ID");
    assertEquals("15-12-9", resultMovement.getRepsScheme(), "Should update fields");
  }

  @Test
  @DisplayName("updateWod: should throw WodLockedException when scores exist")
  void testUpdateWod_Locked() {
    when(wodScoreRepository.existsByWodId(1L)).thenReturn(true);

    WodLockedException ex =
        assertThrows(WodLockedException.class, () -> wodService.updateWod(1L, wodRequest));

    assertEquals("error.wod.locked", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateWod: should throw ResourceNotFoundException when WOD not found")
  void testUpdateWod_NotFound() {
    when(wodScoreRepository.existsByWodId(99L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(99L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> wodService.updateWod(99L, wodRequest));
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
