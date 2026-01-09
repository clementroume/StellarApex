package apex.stellar.antares.mapper;

import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.dto.PreferencesUpdateRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper interface for converting between the {@link User} entity and its DTOs.
 *
 * <p>MapStruct generates an implementation of this interface at compile time. The {@code
 * componentModel = "spring"} attribute makes the generated mapper a Spring Bean, allowing it to be
 * injected into services. {@code unmappedTargetPolicy = ReportingPolicy.IGNORE} Prevents
 * compilation errors if not all source fields are mapped to the target (e.g., during partial
 * updates).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

  /**
   * Converts a User entity to a UserResponse DTO.
   *
   * @param user The User entity to convert.
   * @return The corresponding UserResponse DTO, containing only public data.
   */
  @Mapping(target = "memberships", source = "memberships")
  UserResponse toUserResponse(User user);

  /**
   * Converts a Membership entity to a MembershipSummary DTO.
   *
   * @param membership The Membership entity to convert.
   * @return The corresponding MembershipSummary DTO.
   */
  MembershipSummary toMembershipSummary(Membership membership);

  /**
   * Converts a list of Membership entities to a list of MembershipSummary DTOs.
   *
   * @param memberships The list of Membership entities.
   * @return The list of MembershipSummary DTOs.
   */
  List<MembershipSummary> toMembershipSummaryList(List<Membership> memberships);

  /**
   * Updates an existing User entity from a ProfileUpdateRequest DTO.
   *
   * <p>The {@link MappingTarget} annotation instructs MapStruct to update the provided {@code user}
   * instance in-place rather than creating a new one.
   *
   * @param dto The DTO containing the new profile data.
   * @param user The existing User entity to be updated.
   */
  void updateFromProfile(ProfileUpdateRequest dto, @MappingTarget User user);

  /**
   * Updates an existing User entity from a PreferencesUpdateRequest DTO.
   *
   * @param dto The DTO containing the new preferences' data.
   * @param user The existing User entity to be updated.
   */
  void updateFromPreferences(PreferencesUpdateRequest dto, @MappingTarget User user);
}
