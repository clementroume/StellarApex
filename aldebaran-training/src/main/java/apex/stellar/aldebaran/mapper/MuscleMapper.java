package apex.stellar.aldebaran.mapper;

import apex.stellar.aldebaran.dto.MuscleRequest;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.model.entities.Muscle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper interface for converting between {@link Muscle} entities and DTOs.
 *
 * <p><b>Strategy:</b> Direct field-to-field mapping. ID and Audit fields are ignored during
 * creation/update to prevent overwriting system-managed data.
 *
 * <p>MapStruct generates an implementation of this interface at compile time. The {@code
 * componentModel = "spring"} attribute makes the generated mapper a Spring Bean, allowing it to be
 * injected into services.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MuscleMapper {

  /**
   * Converts a Muscle entity to a public Response DTO.
   *
   * @param muscle The Muscle entity source.
   * @return The corresponding MuscleResponse DTO.
   */
  MuscleResponse toResponse(Muscle muscle);

  /**
   * Creates a new Muscle entity from a Request DTO.
   *
   * <p><b>Note:</b> The {@code id}, {@code createdAt}, and {@code updatedAt} fields are
   * automatically ignored as they are managed by the database or JPA auditing.
   *
   * @param request The creation request DTO.
   * @return A new transient Muscle entity.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Muscle toEntity(MuscleRequest request);

  /**
   * Updates an existing Muscle entity with data from a Request DTO.
   *
   * <p>This method performs an in-place update of the provided entity.
   *
   * @param request The DTO containing updated data.
   * @param entity The existing Muscle entity to be modified.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void updateEntity(MuscleRequest request, @MappingTarget Muscle entity);
}
