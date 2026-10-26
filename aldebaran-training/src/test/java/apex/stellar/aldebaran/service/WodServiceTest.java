package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodFacetsResponse;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.exception.InvalidWodMovementException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.exception.WodLockedException;
import apex.stellar.aldebaran.mapper.WodMapper;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.model.enums.WodScope;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import apex.stellar.aldebaran.security.SecurityService;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class WodServiceTest {

  @Mock private WodRepository wodRepository;
  @Mock private MovementRepository movementRepository;
  @Mock private MovementService movementService;
  @Mock private ScoreRepository scoreRepository;
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

    movement = Movement.builder().id(1L).category(Category.PULLING).build();

    WodMovementRequest movementRequest =
        new WodMovementRequest(
            1L, 1, "21-15-9", 0.0, null, null, 0, null, 0.0, null, 0, null, null, null, null, null, null);
    wodRequest =
        new WodRequest(
            "Fran",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null, // authorId
            null, // gymId
            true, // isPublic
            "Desc",
            null,
            "Notes",
            null,
            600, // timeCap
            0, // emomInterval
            0, // emomRounds
            "21-15-9", // repScheme
            List.of(movementRequest) // movements
            );
  }

  // =========================================================================
  // TEST: getWods (Single Secure Query with combinable filters)
  // =========================================================================

  @Test
  @DisplayName("getWods: No filters should query scope ALL with the security context")
  void testGetWods_NoFilters() {
    // Given
    Pageable pageable = Pageable.unpaged();
    Slice<WodSummary> slice = new SliceImpl<>(List.of(mock(WodSummary.class)));
    when(securityService.getCurrentUserId()).thenReturn(5L);
    when(securityService.getCurrentUserMemberGymIds()).thenReturn(Set.of(101L));
    when(securityService.isCurrentUserAdmin()).thenReturn(false);
    when(wodRepository.findSecure(
            null, null, null, null, "ALL", null, null, 5L, Set.of(101L), false, pageable))
        .thenReturn(slice);

    // When
    Slice<WodSummaryResponse> results =
        wodService.getWods(null, null, null, null, null, null, null, pageable);

    // Then
    assertNotNull(results);
    assertEquals(1, results.getContent().size());
  }

  @Test
  @DisplayName("getWods: Search, type, movement, scope and gym filters are combined")
  void testGetWods_CombinedFilters() {
    // Given
    Pageable pageable = Pageable.unpaged();
    when(securityService.getCurrentUserId()).thenReturn(5L);
    when(securityService.getCurrentUserMemberGymIds()).thenReturn(Set.of(101L, 202L));
    when(securityService.isCurrentUserAdmin()).thenReturn(false);
    when(wodRepository.findSecure(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(new SliceImpl<>(List.of()));

    // When
    wodService.getWods(" FrAn ", WodType.AMRAP, null, 1L, WodScope.GYM, 202L, 9L, pageable);

    // Then: search is trimmed and lower-cased into a LIKE pattern
    verify(wodRepository)
        .findSecure(
            "%fran%",
            WodType.AMRAP,
            null,
            1L,
            "GYM",
            202L,
            9L,
            5L,
            Set.of(101L, 202L),
            false,
            pageable);
  }

  @Test
  @DisplayName("getFacets: types follow the filters, gyms and authors ignore scope, gym and author")
  void testGetFacets() {
    when(securityService.getCurrentUserId()).thenReturn(5L);
    when(securityService.getCurrentUserMemberGymIds()).thenReturn(Set.of(101L));
    when(securityService.isCurrentUserAdmin()).thenReturn(true);
    when(wodRepository.findSecureWodTypes(
            "%fran%", null, null, null, "GYM", 101L, null, 5L, Set.of(101L), true))
        .thenReturn(List.of(WodType.STRENGTH, WodType.AMRAP));
    when(wodRepository.findSecureCategories(
            "%fran%", null, null, null, "GYM", 101L, null, 5L, Set.of(101L), true))
        .thenReturn(List.of(WodCategory.OPEN, WodCategory.GIRLS));
    when(wodRepository.findSecureGymIds(
            "%fran%", null, null, null, "ALL", null, null, 5L, Set.of(101L), true))
        .thenReturn(List.of(3L, 1L));
    when(wodRepository.findSecureAuthorIds(
            "%fran%", null, null, null, "ALL", null, null, 5L, Set.of(101L), true))
        .thenReturn(List.of(9L, 4L));

    WodFacetsResponse facets = wodService.getFacets("Fran", WodScope.GYM, 101L, null);

    // Types are returned in enum order, ids sorted
    assertEquals(List.of(WodType.AMRAP, WodType.STRENGTH), facets.wodTypes());
    assertEquals(List.of(WodCategory.GIRLS, WodCategory.OPEN), facets.categories());
    assertEquals(List.of(1L, 3L), facets.gymIds());
    assertEquals(List.of(4L, 9L), facets.authorIds());
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
  @DisplayName("createWod: should link movements and set creator using cached MovementService")
  void testCreateWod_Success() {
    when(securityService.getCurrentUserId()).thenReturn(100L);

    // Mock Redis Hit
    apex.stellar.aldebaran.dto.MovementResponse mockMovementDto =
        mock(apex.stellar.aldebaran.dto.MovementResponse.class);
    when(mockMovementDto.category()).thenReturn(Category.PULLING);
    when(movementService.getMovement(1L)).thenReturn(mockMovementDto);

    // Mock Proxy JPA
    when(movementRepository.getReferenceById(1L)).thenReturn(movement);

    when(wodMapper.toEntity(wodRequest)).thenReturn(wod);
    WodMovement wmStub = new WodMovement();
    when(wodMapper.toWodMovementEntity(any())).thenReturn(wmStub);
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    // Execution
    WodResponse response = wodService.createWod(wodRequest);

    // Assertions
    assertNotNull(response);
    assertEquals(100L, wod.getAuthorId());
    assertEquals(1, wod.getMovements().size());
    assertTrue(wod.getModalities().contains(Modality.GYMNASTICS)); // Verified from Category.PULLING

    verify(movementService).getMovement(1L);
    verify(movementRepository).getReferenceById(1L);
    verify(wodRepository).save(wod);
  }

  @Test
  @DisplayName("createWod: should correctly aggregate multiple distinct modalities via DTOs")
  void testCreateWod_ModalityAggregation() {
    // Requests with 2 distinct movements
    WodRequest multiModalityRequest =
        new WodRequest(
            "Diane",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            null,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "21-15-9",
            List.of(
                new WodMovementRequest(
                    1L, 1, "21", null, null, null, 0, null, 225.0, null, 0, null, null, null, null, null, null),
                new WodMovementRequest(
                    2L, 2, "21", null, null, null, 0, null, 0.0, null, 0, null, null, null, null, null, null)));

    when(securityService.getCurrentUserId()).thenReturn(100L);

    // DTO Mocks
    apex.stellar.aldebaran.dto.MovementResponse mockGymDto =
        mock(apex.stellar.aldebaran.dto.MovementResponse.class);
    when(mockGymDto.category()).thenReturn(Category.PULLING);
    apex.stellar.aldebaran.dto.MovementResponse mockWlDto =
        mock(apex.stellar.aldebaran.dto.MovementResponse.class);
    when(mockWlDto.category()).thenReturn(Category.DEADLIFT);

    when(movementService.getMovement(1L)).thenReturn(mockGymDto);
    when(movementService.getMovement(2L)).thenReturn(mockWlDto);

    when(movementRepository.getReferenceById(anyLong())).thenReturn(new Movement());

    when(wodMapper.toEntity(any())).thenReturn(wod);
    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(wod)).thenReturn(wod);

    // When
    wodService.createWod(multiModalityRequest);

    // Then
    assertEquals(2, wod.getModalities().size(), "Should aggregate exactly 2 distinct modalities");
    assertTrue(wod.getModalities().contains(Modality.GYMNASTICS));
    assertTrue(wod.getModalities().contains(Modality.WEIGHTLIFTING));
  }

  @Test
  @DisplayName("createWod: should throw exception if movement ID is invalid (Fail Fast)")
  void testCreateWod_InvalidMovement() {
    when(securityService.getCurrentUserId()).thenReturn(100L);

    when(wodMapper.toEntity(any())).thenReturn(new Wod());

    when(movementService.getMovement(any()))
        .thenThrow(new ResourceNotFoundException("error.movement.not.found"));

    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> wodService.createWod(wodRequest));

    assertEquals("error.movement.not.found", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("createWod: should reject an equipment that the movement does not allow")
  void testCreateWod_EquipmentNotAllowed() {
    when(securityService.getCurrentUserId()).thenReturn(100L);
    when(wodMapper.toEntity(any())).thenReturn(wod);
    MovementResponse dip = dipDto();
    when(movementService.getMovement(1L)).thenReturn(dip);
    when(movementRepository.getReferenceById(1L)).thenReturn(movement);
    when(wodMapper.toWodMovementEntity(any()))
        .thenReturn(WodMovement.builder().equipment(Set.of("RINGS", "BARBELL")).build());

    InvalidWodMovementException ex =
        assertThrows(InvalidWodMovementException.class, () -> wodService.createWod(wodRequest));

    assertEquals("error.wod.movement.equipment.invalid", ex.getMessageKey());
    assertArrayEquals(new Object[] {"BARBELL", "Dip"}, ex.getArgs());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("createWod: should accept equipment and techniques allowed by the movement")
  void testCreateWod_AllowedVariants() {
    when(securityService.getCurrentUserId()).thenReturn(100L);
    when(wodMapper.toEntity(any())).thenReturn(wod);
    MovementResponse dip = dipDto();
    when(movementService.getMovement(1L)).thenReturn(dip);
    when(movementRepository.getReferenceById(1L)).thenReturn(movement);
    when(wodMapper.toWodMovementEntity(any()))
        .thenReturn(
            WodMovement.builder().equipment(Set.of("RINGS")).techniques(Set.of("STRICT")).build());
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    assertNotNull(wodService.createWod(wodRequest));
    verify(wodRepository).save(wod);
  }

  private static MovementResponse dipDto() {
    MovementResponse dto = mock(MovementResponse.class);
    when(dto.name()).thenReturn("Dip");
    when(dto.equipment()).thenReturn(Set.of(Equipment.RINGS, Equipment.BOX));
    when(dto.techniques()).thenReturn(Set.of(Technique.STRICT, Technique.KIPPING));
    return dto;
  }

  @Test
  @DisplayName("updateWod: should update when NO scores exist")
  void testUpdateWod_Success() {
    // 1. Check Locks
    when(scoreRepository.existsByWodId(1L)).thenReturn(false);

    // 2. Fetch Existing
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));

    // 3. Mock Updates
    // Simulate mapper update
    doAnswer(
            inv -> {
              Wod t = inv.getArgument(1);
              t.setTitle("Updated Title");
              return null;
            })
        .when(wodMapper)
        .updateEntity(any(), any());

    MovementResponse mockMovementDto = mock(MovementResponse.class);
    when(movementService.getMovement(any())).thenReturn(mockMovementDto);
    when(movementRepository.getReferenceById(any())).thenReturn(movement);

    when(wodMapper.toWodMovementEntity(any())).thenReturn(new WodMovement());
    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    // Execution
    wodService.updateWod(1L, wodRequest);

    verify(wodRepository).save(wod);
    assertEquals("Updated Title", wod.getTitle());
  }

  @Test
  @DisplayName("updateWod: Smart Merge should preserve existing entities if order matches")
  void testUpdateWod_SmartMerge_PreservesEntities() {
    WodMovement existingMovement = new WodMovement();
    existingMovement.setId(500L);
    existingMovement.setOrderIndex(1);
    existingMovement.setRepsScheme("21-15-9");
    existingMovement.setMovement(movement);
    existingMovement.setWod(wod);

    wod.getMovements().add(existingMovement);

    when(scoreRepository.existsByWodId(1L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));

    apex.stellar.aldebaran.dto.MovementResponse mockMovementDto =
        mock(apex.stellar.aldebaran.dto.MovementResponse.class);
    when(movementService.getMovement(1L)).thenReturn(mockMovementDto);
    when(movementRepository.getReferenceById(1L)).thenReturn(movement);

    when(wodRepository.save(wod)).thenReturn(wod);
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));

    WodMovement tempMovement = new WodMovement();
    tempMovement.setRepsScheme("15-12-9");
    tempMovement.setOrderIndex(1);
    when(wodMapper.toWodMovementEntity(any())).thenReturn(tempMovement);

    WodRequest updateRequest =
        new WodRequest(
            "Fran",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            null,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                new WodMovementRequest(
                    1L, 1, "15-12-9", 0.0, null, null, 0, null, 0.0, null, 0, null, null, null, null, null, null)));

    wodService.updateWod(1L, updateRequest);

    assertEquals(1, wod.getMovements().size());
    WodMovement resultMovement = wod.getMovements().getFirst();
    assertEquals(500L, resultMovement.getId(), "Should preserve entity ID");
    assertEquals("15-12-9", resultMovement.getRepsScheme(), "Should update fields");
  }

  @Test
  @DisplayName("updateWod: should throw WodLockedException when scores exist")
  void testUpdateWod_Locked() {
    when(scoreRepository.existsByWodId(1L)).thenReturn(true);

    WodLockedException ex =
        assertThrows(WodLockedException.class, () -> wodService.updateWod(1L, wodRequest));

    assertEquals("error.wod.locked", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateWod: should reject a technique that the movement does not allow")
  void testUpdateWod_TechniqueNotAllowed() {
    when(scoreRepository.existsByWodId(1L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));
    MovementResponse dip = dipDto();
    when(movementService.getMovement(1L)).thenReturn(dip);
    when(movementRepository.getReferenceById(1L)).thenReturn(movement);
    when(wodMapper.toWodMovementEntity(any()))
        .thenReturn(WodMovement.builder().techniques(Set.of("BUTTERFLY")).build());

    InvalidWodMovementException ex =
        assertThrows(
            InvalidWodMovementException.class, () -> wodService.updateWod(1L, wodRequest));

    assertEquals("error.wod.movement.technique.invalid", ex.getMessageKey());
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateWod: should throw ResourceNotFoundException when WOD not found")
  void testUpdateWod_NotFound() {
    when(scoreRepository.existsByWodId(99L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(99L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> wodService.updateWod(99L, wodRequest));
    verify(wodRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateWod: Smart Merge should remove orphans when a movement is deleted")
  void testUpdateWod_SmartMerge_OrphanRemoval() {
    // Given: Existing WOD with movement Order 1
    WodMovement existingOrphan = new WodMovement();
    existingOrphan.setId(500L);
    existingOrphan.setOrderIndex(1); // Mouvement qui va être supprimé
    wod.getMovements().add(existingOrphan);

    when(scoreRepository.existsByWodId(1L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));

    // On simule l'ajout d'un nouveau mouvement Order 2.
    MovementResponse mockMovementDto = mock(MovementResponse.class);
    when(movementService.getMovement(any())).thenReturn(mockMovementDto);
    when(movementRepository.getReferenceById(any())).thenReturn(movement);

    WodMovement newMovement = new WodMovement();
    newMovement.setOrderIndex(2);
    when(wodMapper.toWodMovementEntity(any())).thenReturn(newMovement);

    when(wodRepository.save(wod)).thenReturn(wod);

    // Update Request contains ONLY Order 2 (Order 1 is omitted, thus deleted)
    WodRequest updateRequest =
        new WodRequest(
            "Fran",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            null,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                new WodMovementRequest(
                    1L, 2, "15", null, null, null, 0, null, null, null, 0, null, null, null, null, null, null)));
    // When
    wodService.updateWod(1L, updateRequest);

    // Then
    assertEquals(1, wod.getMovements().size(), "Should contain exactly 1 movement after merge");
    assertEquals(
        2,
        wod.getMovements().getFirst().getOrderIndex(),
        "The remaining movement must be the new one (Order 2)");
    assertFalse(
        wod.getMovements().contains(existingOrphan),
        "The orphan movement (Order 1) should be removed");
  }

  @Test
  @DisplayName("updateWod: should handle update with empty movements gracefully")
  void testUpdateWod_EmptyMovements() {
    // Given
    when(scoreRepository.existsByWodId(1L)).thenReturn(false);
    when(wodRepository.findByIdWithMovements(1L)).thenReturn(Optional.of(wod));
    when(wodMapper.toResponse(wod)).thenReturn(mock(WodResponse.class));
    when(wodRepository.save(wod)).thenReturn(wod);

    WodRequest emptyMovementsRequest =
        new WodRequest(
            "Empty",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            null,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of()); // Liste vide !

    // When
    assertDoesNotThrow(() -> wodService.updateWod(1L, emptyMovementsRequest));

    // Then
    verify(movementService, never()).getMovement(anyLong());
    verify(movementRepository, never()).getReferenceById(anyLong());
  }

  @Test
  @DisplayName("deleteWod: should delete entity when found and unlocked")
  void testDeleteWod_Success() {
    when(wodRepository.existsById(1L)).thenReturn(true);
    when(scoreRepository.existsByWodId(1L)).thenReturn(false);

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

  @Test
  @DisplayName("deleteWod: should throw WodLockedException when scores exist")
  void testDeleteWod_Locked() {
    when(wodRepository.existsById(1L)).thenReturn(true);
    when(scoreRepository.existsByWodId(1L)).thenReturn(true);

    WodLockedException ex = assertThrows(WodLockedException.class, () -> wodService.deleteWod(1L));

    assertEquals("error.wod.locked", ex.getMessageKey());
    verify(wodRepository, never()).deleteById(any());
  }
}
