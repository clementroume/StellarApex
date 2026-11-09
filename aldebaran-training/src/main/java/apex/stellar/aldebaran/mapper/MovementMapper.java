package apex.stellar.aldebaran.mapper;

import apex.stellar.aldebaran.dto.AcceptedMovementResponse;
import apex.stellar.aldebaran.dto.MovementComponentDto;
import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementMuscleResponse;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.dto.RoleAdjustmentDto;
import apex.stellar.aldebaran.dto.UsualNameDto;
import apex.stellar.aldebaran.dto.UsualNameOptionResponse;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementComponent;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment;
import apex.stellar.aldebaran.model.entities.MovementUsualName;
import apex.stellar.aldebaran.repository.projection.MovementSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for {@link Movement} and its biomechanical relationships.
 *
 * <p><b>Strategy:</b>
 *
 * <ul>
 *   <li><b>Muscle Linking:</b> The {@code targetedMuscles} list in requests contains references
 *       (Medical Names). The Mapper ignores these during entity creation (`ignore = true`) because
 *       the Service layer must resolve the actual {@link
 *       apex.stellar.aldebaran.model.entities.Muscle} entities from the database to establish the
 *       relationship.
 *   <li><b>DTO Structure:</b> Defines the nested structure for responses, delegating to {@link
 *       MuscleMapper} for the muscle details.
 * </ul>
 *
 * <p>Uses {@link MuscleMapper} to convert nested muscle objects in responses.
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {MuscleMapper.class})
public interface MovementMapper {

  // -------------------------------------------------------------------------
  // Movement Mappings
  // -------------------------------------------------------------------------

  /**
   * Converts a full Movement entity to a detailed Response DTO.
   *
   * @param movement The source entity.
   * @return The detailed DTO including anatomy and i18n content.
   */
  @Mapping(target = "targetedMuscles", source = "targetedMuscles")
  MovementResponse toResponse(Movement movement);

  /**
   * Maps a {@link MovementSummary} projection to a {@link MovementSummaryResponse} DTO.
   *
   * @param projection The source projection containing lightweight movement information.
   * @return A response DTO with id, name, abbreviation, category, and thumbnail URL for display
   *     purposes.
   */
  MovementSummaryResponse toSummary(MovementSummary projection);

  /**
   * Maps a creation request to a Movement entity.
   *
   * <p><b>Important:</b> The {@code targetedMuscles} collection is ignored here. The logic to link
   * muscles involves database lookups (by medical name) and is handled by the Service layer to
   * avoid injecting repositories into the Mapper.
   *
   * @param request The creation request.
   * @return The Movement entity structure (without relationships).
   */
  @Mapping(target = "id", ignore = true) // Generated Business Key
  @Mapping(target = "targetedMuscles", ignore = true) // Handled by Service
  @Mapping(target = "components", ignore = true) // Handled by Service
  @Mapping(target = "acceptedMovements", ignore = true) // Handled by Service
  @Mapping(target = "roleAdjustments", ignore = true) // Handled by Service
  @Mapping(target = "usualNames", ignore = true) // Handled by Service
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Movement toEntity(MovementRequest request);

  /**
   * Updates an existing Movement entity.
   *
   * @param request The update payload.
   * @param entity The target entity to update.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "targetedMuscles", ignore = true) // Handled by Service
  @Mapping(target = "components", ignore = true) // Handled by Service
  @Mapping(target = "acceptedMovements", ignore = true) // Handled by Service
  @Mapping(target = "roleAdjustments", ignore = true) // Handled by Service
  @Mapping(target = "usualNames", ignore = true) // Handled by Service
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void updateEntity(MovementRequest request, @MappingTarget Movement entity);

  // -------------------------------------------------------------------------
  // Nested: MovementMuscle Mappings
  // -------------------------------------------------------------------------

  /**
   * Converts the join entity {@link MovementMuscle} to its DTO representation.
   *
   * @param movementMuscle The source join entity.
   * @return The DTO including the muscle details and role.
   */
  @SuppressWarnings("unused")
  @Mapping(target = "muscle", source = "muscle")
  MovementMuscleResponse toMuscleResponse(MovementMuscle movementMuscle);

  /**
   * Converts a chain step to its DTO.
   *
   * @param component The step.
   * @return The DTO.
   */
  @SuppressWarnings("unused")
  @Mapping(target = "movementId", source = "component.id")
  @Mapping(target = "movementName", source = "component.name")
  MovementComponentDto toComponentDto(MovementComponent component);

  /**
   * Converts an accepted movement to its DTO.
   *
   * @param movement The accepted movement.
   * @return The DTO.
   */
  @SuppressWarnings("unused")
  AcceptedMovementResponse toAccepted(Movement movement);

  /**
   * Converts a role adjustment to its DTO.
   *
   * @param adjustment The adjustment.
   * @return The DTO.
   */
  @SuppressWarnings("unused")
  @Mapping(target = "muscleId", source = "muscle.id")
  @Mapping(target = "muscleName", source = "muscle.medicalName")
  RoleAdjustmentDto toAdjustmentDto(MovementRoleAdjustment adjustment);

  /**
   * Converts a usual name to its DTO.
   *
   * @param name The usual name.
   * @return The DTO.
   */
  @SuppressWarnings("unused")
  UsualNameDto toUsualNameDto(MovementUsualName name);

  /**
   * Converts a usual name to an entry of the movement search.
   *
   * @param name The usual name, with its movement.
   * @return The entry.
   */
  @Mapping(target = "movementId", source = "movement.id")
  @Mapping(target = "movementName", source = "movement.name")
  @Mapping(target = "category", source = "movement.category")
  @Mapping(target = "impliedEquipment", source = "movement.impliedEquipment")
  UsualNameOptionResponse toUsualNameOption(MovementUsualName name);

  /**
   * Maps a nested muscle request to the join entity.
   *
   * <p>The {@code muscle} and {@code movement} relationships are ignored here. The Service must set
   * the parent {@code Movement} and resolve the {@code Muscle} entity using the provided medical
   * name.
   *
   * @param request The nested muscle request.
   * @return The partial MovementMuscle entity (role and impact only).
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "movement", ignore = true) // Set by parent context in Service
  @Mapping(target = "muscle", ignore = true) // Resolved by Service via medicalName lookup
  MovementMuscle toMuscleEntity(MovementMuscleRequest request);
}
