package apex.stellar.aldebaran.mapper;

import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.model.entities.WodScore;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for {@link WodScore} entities.
 *
 * <p>Uses {@link WodMapper} to embed a summary of the WOD in the score response context.
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {WodMapper.class})
public interface WodScoreMapper {

  /**
   * Converts a Score entity to a Response DTO.
   *
   * @param score The source entity.
   * @return The response DTO including the WOD summary.
   */
  @Mapping(target = "wodSummary", source = "wod") // Uses WodMapper.toSummary
  @Mapping(target = "userId", source = "userId")
  WodScoreResponse toResponse(WodScore score);

  /**
   * Maps a score submission request to an entity.
   *
   * <p><b>Security Note:</b> Sensitive fields like {@code userId} (ownership) and {@code
   * personalRecord} (logic) are ignored here. The Service layer is responsible for setting the user
   * from the security context and calculating PR status.
   *
   * @param request The score submission.
   * @return The WodScore entity.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "wod", ignore = true) // Resolved by Service via wodId
  @Mapping(target = "userId", ignore = true) // Set from Security Context
  @Mapping(target = "loggedAt", ignore = true)
  @Mapping(target = "personalRecord", ignore = true) // Calculated by Service logic
  WodScore toEntity(WodScoreRequest request);

  /**
   * Updates an existing Score entity.
   *
   * @param request The update payload.
   * @param entity The target Score entity.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "wod", ignore = true) // WOD cannot be changed after logging
  @Mapping(target = "userId", ignore = true) // User cannot be changed
  @Mapping(target = "loggedAt", ignore = true)
  @Mapping(target = "personalRecord", ignore = true) // Re-calculated by Service
  void updateEntity(WodScoreRequest request, @MappingTarget WodScore entity);
}
