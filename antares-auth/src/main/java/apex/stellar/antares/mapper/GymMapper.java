package apex.stellar.antares.mapper;

import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.GymResponse;
import apex.stellar.antares.model.Gym;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Mapper interface for converting between the {@code Gym} entity and its corresponding DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GymMapper {

  /** Converts a {@link Gym} entity to a {@link GymResponse} DTO. */
  GymResponse toResponse(Gym gym);

  /**
   * Converts a {@link GymRequest} DTO to a {@link Gym} entity.
   *
   * <p>Configuration:
   *
   * <ul>
   *   <li>{@code isAutoSubscription} defaults to {@code false} (Manual approval by default).
   *   <li>{@code status} defaults to {@code PENDING_APPROVAL}.
   * </ul>
   *
   * @param request The request containing gym details.
   * @return A new Gym entity ready for persistence.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "enrollmentCode", ignore = true)
  @Mapping(target = "status", constant = "PENDING_APPROVAL")
  @Mapping(target = "isAutoSubscription", constant = "false")
  Gym toEntity(GymRequest request);
}
