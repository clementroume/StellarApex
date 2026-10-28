package apex.stellar.antares.mapper;

import apex.stellar.antares.dto.MembershipCapabilities;
import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.model.Membership;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper interface for converting between Membership entities and their corresponding DTOs. Uses
 * MapStruct for automated mapping implementation.
 */
@Mapper(
    componentModel = "spring",
    imports = List.class,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MembershipMapper {

  /**
   * Converts a Membership entity to a MembershipResponse DTO with the current user's capabilities.
   *
   * @param membership the Membership entity to convert
   * @param capabilities what the current user may do with it
   * @return the converted MembershipResponse DTO
   */
  @Mapping(target = "id", source = "membership.id")
  @Mapping(target = "user", source = "membership.user")
  @Mapping(target = "gymRole", source = "membership.gymRole")
  @Mapping(target = "status", source = "membership.status")
  @Mapping(target = "permissions", source = "membership.permissions")
  @Mapping(target = "createdAt", source = "membership.createdAt")
  @Mapping(target = "capabilities", source = "capabilities")
  MembershipResponse toResponse(Membership membership, MembershipCapabilities capabilities);

  /**
   * Converts a Membership entity to a MembershipResponse DTO without capabilities (null), for
   * answers the client reloads anyway (e.g. after an ownership transfer).
   *
   * @param membership the Membership entity to convert
   * @return the converted MembershipResponse DTO
   */
  default MembershipResponse toResponse(Membership membership) {
    return toResponse(membership, null);
  }

  /**
   * Converts a Membership entity to a MembershipSummary DTO, including flattened gym details and
   * the permissions it grants (see {@link Membership#activePermissions()}: none unless ACTIVE).
   *
   * @param membership the Membership entity to convert
   * @return the converted MembershipSummary DTO
   */
  @Mapping(target = "gymId", source = "gym.id")
  @Mapping(target = "gymName", source = "gym.name")
  @Mapping(target = "gymStatus", source = "gym.status")
  @Mapping(target = "gymProgramming", source = "gym.programming")
  @Mapping(target = "gymRole", source = "gymRole")
  @Mapping(target = "status", source = "status")
  @Mapping(
      target = "permissions",
      expression = "java(List.copyOf(membership.activePermissions()))")
  @Mapping(target = "usable", expression = "java(membership.isUsable())")
  MembershipSummary toSummary(Membership membership);
}
