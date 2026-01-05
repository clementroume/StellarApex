package apex.stellar.aldebaran.mapper;

import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementMuscleResponse;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
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
   * Converts a Movement entity to a lightweight Summary DTO.
   *
   * <p>Used for list views and autocomplete where full details are not required.
   *
   * @param movement The source entity.
   * @return The summary DTO.
   */
  MovementSummaryResponse toSummary(Movement movement);

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
  @Mapping(target = "muscle", source = "muscle") // Delegates to MuscleMapper
  MovementMuscleResponse toMuscleResponse(MovementMuscle movementMuscle);

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
