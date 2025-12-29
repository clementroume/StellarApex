package apex.stellar.aldebaran.mapper;

import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodMovementResponse;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for {@link Wod} definitions and their prescribed movements.
 *
 * <p>Uses {@link MovementMapper} to embed movement details in the WOD response.
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {MovementMapper.class})
public interface WodMapper {

  // -------------------------------------------------------------------------
  // WOD Mappings
  // -------------------------------------------------------------------------

  /**
   * Converts a Wod entity to a full Response DTO.
   *
   * @param wod The source entity.
   * @return The complete WOD definition.
   */
  WodResponse toResponse(Wod wod);

  /**
   * Converts a Wod entity to a summary DTO.
   *
   * @param wod The source entity.
   * @return A lightweight summary for lists.
   */
  WodSummaryResponse toSummary(Wod wod);

  /**
   * Maps a creation request to a Wod entity.
   *
   * <p><b>Note:</b> The {@code movements} list is ignored here. The Service layer is responsible
   * for converting the list of {@code WodMovementRequest}, resolving the corresponding {@code
   * Movement} entities, and establishing the parent-child relationship.
   *
   * @param request The creation request.
   * @return The Wod entity.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "movements", ignore = true) // Handled by Service
  @Mapping(target = "modalities", ignore = true) // Computed by Service based on movements
  @Mapping(target = "creatorId", ignore = true) // Set from Security Context
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Wod toEntity(WodRequest request);

  /**
   * Updates an existing Wod entity.
   *
   * @param request The update payload.
   * @param entity The target Wod entity.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "movements", ignore = true) // Handled by Service
  @Mapping(target = "modalities", ignore = true) // Re-computed by Service
  @Mapping(target = "creatorId", ignore = true) // Immutable
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void updateEntity(WodRequest request, @MappingTarget Wod entity);

  // -------------------------------------------------------------------------
  // Nested: WodMovement Mappings
  // -------------------------------------------------------------------------

  /**
   * Converts a {@link WodMovement} to its DTO representation.
   *
   * @param wodMovement The source entity.
   * @return The DTO including the prescribed details and the movement definition.
   */
  @Mapping(target = "movement", source = "movement") // Delegates to MovementMapper
  WodMovementResponse toWodMovementResponse(WodMovement wodMovement);

  /**
   * Maps a movement prescription request to a WodMovement entity.
   *
   * <p>The {@code movement} relationship is ignored because the DTO only provides an ID. The
   * Service must look up the {@code Movement} entity and set it manually.
   *
   * @param request The prescription request.
   * @return The WodMovement entity (prescribed values only).
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "wod", ignore = true) // Set by parent context
  @Mapping(target = "movement", ignore = true) // Resolved by Service via movementId
  WodMovement toWodMovementEntity(WodMovementRequest request);
}
