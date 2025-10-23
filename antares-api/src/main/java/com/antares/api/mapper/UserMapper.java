package com.antares.api.mapper;

import com.antares.api.dto.PreferencesUpdateRequest;
import com.antares.api.dto.ProfileUpdateRequest;
import com.antares.api.dto.UserResponse;
import com.antares.api.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper interface for converting between the {@link User} entity and its DTOs.
 *
 * <p>MapStruct will generate an implementation of this interface at compile time. The {@code
 * componentModel = "spring"} attribute makes the generated mapper a Spring Bean, allowing it to be
 * injected into services. The {@code unmappedTargetPolicy = ReportingPolicy.IGNORE} prevents
 * compilation errors if not all fields from the source are mapped to the target.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

  /**
   * Converts a User entity to a UserResponse DTO.
   *
   * @param user The User entity to convert.
   * @return The corresponding UserResponse DTO, containing only public-facing data.
   */
  UserResponse toUserResponse(User user);

  /**
   * Updates an existing User entity from a ProfileUpdateRequest DTO.
   *
   * <p>The {@link MappingTarget} annotation tells MapStruct to update the provided {@code user}
   * instance in-place, rather than creating a new one.
   *
   * @param dto The DTO containing the new profile data.
   * @param user The existing User entity to be updated.
   */
  void updateFromProfile(ProfileUpdateRequest dto, @MappingTarget User user);

  /**
   * Updates an existing User entity from a PreferencesUpdateRequest DTO.
   *
   * @param dto The DTO containing the new preferences data.
   * @param user The existing User entity to be updated.
   */
  void updateFromPreferences(PreferencesUpdateRequest dto, @MappingTarget User user);
}
