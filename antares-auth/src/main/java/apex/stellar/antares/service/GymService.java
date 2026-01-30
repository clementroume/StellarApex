package apex.stellar.antares.service;

import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.GymResponse;
import apex.stellar.antares.dto.GymSettingsRequest;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.GymMapper;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.GymRepository;
import apex.stellar.antares.repository.MembershipRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class responsible for managing gym-related operations such as creating, updating,
 * retrieving, and deleting gyms, as well as handling gym settings and memberships.
 */
@Service
@RequiredArgsConstructor
public class GymService {

  private static final String ERROR_GYM_NOT_FOUND = "error.gym.not.found";
  // Explicitly defining the full permission set for Gym Creators (Owners/Programmers)
  private static final Set<Permission> FULL_ADMIN_PERMISSIONS =
      Set.of(
          Permission.MANAGE_SETTINGS,
          Permission.MANAGE_MEMBERSHIPS,
          Permission.WOD_WRITE,
          Permission.SCORE_VERIFY);
  private final GymRepository gymRepository;
  private final MembershipRepository membershipRepository;
  private final GymMapper gymMapper;

  @Value("${application.gym.creation-secret}")
  private String creationSecret;

  /**
   * Creates a new gym and assigns the creator as the owner.
   *
   * <p>The creation process involves validating a secret token, checking for duplicate gym names,
   * initializing the gym status to pending approval, and generating an initial enrollment code. A
   * membership with the role of an owner is also created for the user who initiated the request.
   *
   * @param request The {@link GymRequest} containing the details of the gym to be created.
   * @param creator The {@link User} who is creating the gym and will be assigned as the owner.
   * @return A {@link GymResponse} object representing the newly created gym.
   * @throws AccessDeniedException If the provided creation token is invalid.
   * @throws DataConflictException If a gym with the same name already exists.
   */
  @Transactional
  public GymResponse createGym(GymRequest request, User creator) {
    // 1. Security Check (Anti-Spam)
    if (!request.creationToken().equals(creationSecret)) {
      throw new AccessDeniedException("error.creation.token.invalid");
    }

    // 2. Uniqueness Check (Name only)
    if (gymRepository.existsByName(request.name())) {
      throw new DataConflictException("error.gym.name.exists", request.name());
    }

    // 3. Entity Creation
    Gym gym = gymMapper.toEntity(request);

    // Initial enrollment code is random (8 chars)
    gym.setEnrollmentCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    Gym savedGym = gymRepository.save(gym);

    // 4. Assign Owner/Programmer Role
    GymRole initialRole = request.isProgramming() ? GymRole.PROGRAMMER : GymRole.OWNER;

    // 5. Create Owner Membership
    Membership ownerMembership =
        Membership.builder()
            .gym(savedGym)
            .user(creator)
            .gymRole(initialRole)
            .status(MembershipStatus.ACTIVE)
            .permissions(FULL_ADMIN_PERMISSIONS)
            .build();

    membershipRepository.save(ownerMembership);

    return gymMapper.toResponse(savedGym);
  }

  /**
   * Updates the settings for a specific gym.
   *
   * @param gymId The unique identifier of the gym whose settings are being updated.
   * @param request The {@link GymSettingsRequest} containing the new settings.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   */
  @Transactional
  public void updateSettings(Long gymId, GymSettingsRequest request) {
    Gym gym =
        gymRepository
            .findById(gymId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_GYM_NOT_FOUND, gymId));

    gym.setEnrollmentCode(request.enrollmentCode());
    gym.setAutoSubscription(request.isAutoSubscription());

    gymRepository.save(gym);
  }

  /**
   * Retrieves the current settings for a specific gym.
   *
   * @param gymId The unique identifier of the gym.
   * @return A {@link GymSettingsRequest} containing the gym's current settings.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   */
  @Transactional(readOnly = true)
  public GymSettingsRequest getSettings(Long gymId) {
    Gym gym =
        gymRepository
            .findById(gymId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_GYM_NOT_FOUND, gymId));

    return new GymSettingsRequest(gym.getEnrollmentCode(), gym.isAutoSubscription());
  }

  /**
   * Retrieves a list of gyms based on the requester's role and an optional status filter.
   *
   * <p>If the requester has the admin role and a status filter is provided, gyms matching the
   * specified status are returned. Otherwise, only gyms with an active status are returned.
   *
   * @param requesterRole The role of the requester. Determines the level of access for filtering
   *     gyms.
   * @param statusFilter An optional filter to retrieve gyms with a specific status. This parameter
   *     is considered only if the requester has an admin role.
   * @return A list of {@link GymResponse} objects representing the gyms that match the provided
   *     criteria.
   */
  @Transactional(readOnly = true)
  public List<GymResponse> getAllGyms(PlatformRole requesterRole, GymStatus statusFilter) {
    if (requesterRole == PlatformRole.ADMIN && statusFilter != null) {
      return gymRepository.findByStatus(statusFilter).stream().map(gymMapper::toResponse).toList();
    }
    return gymRepository.findByStatus(GymStatus.ACTIVE).stream()
        .map(gymMapper::toResponse)
        .toList();
  }

  /**
   * Updates the status of a specific gym.
   *
   * @param id The unique identifier of the gym to update.
   * @param status The new status to be assigned to the gym.
   * @return A {@link GymResponse} object representing the updated gym.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   */
  @Transactional
  public GymResponse updateStatus(Long id, GymStatus status) {
    Gym gym =
        gymRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_GYM_NOT_FOUND, id));

    gym.setStatus(status);
    return gymMapper.toResponse(gymRepository.save(gym));
  }

  /**
   * Deletes a gym from the system.
   *
   * @param id The unique identifier of the gym to be deleted.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   */
  @Transactional
  public void deleteGym(Long id) {
    if (!gymRepository.existsById(id)) {
      throw new ResourceNotFoundException(ERROR_GYM_NOT_FOUND, id);
    }
    gymRepository.deleteById(id);
  }
}
