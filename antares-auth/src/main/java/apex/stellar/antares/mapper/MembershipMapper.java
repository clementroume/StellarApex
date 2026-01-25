package apex.stellar.antares.mapper;

import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.model.Membership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper interface for converting between Membership entities and their corresponding DTOs. Uses
 * MapStruct for automated mapping implementation.
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {UserMapper.class})
public interface MembershipMapper {

  /**
   * Converts a Membership entity to a MembershipResponse DTO.
   *
   * @param membership the Membership entity to convert
   * @return the converted MembershipResponse DTO
   */
  @Mapping(target = "user", source = "user")
  @Mapping(target = "gymRole", source = "gymRole")
  MembershipResponse toResponse(Membership membership);

  /**
   * Converts a Membership entity to a MembershipSummary DTO, including flattened gym details.
   *
   * @param membership the Membership entity to convert
   * @return the converted MembershipSummary DTO
   */
  @Mapping(target = "gymId", source = "gym.id")
  @Mapping(target = "gymName", source = "gym.name")
  @Mapping(target = "gymStatus", source = "gym.status")
  @Mapping(target = "gymRole", source = "gymRole")
  @Mapping(target = "status", source = "status")
  MembershipSummary toSummary(Membership membership);
}
