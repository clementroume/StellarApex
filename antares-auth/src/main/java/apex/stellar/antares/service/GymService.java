package apex.stellar.antares.service;

import apex.stellar.antares.dto.AdminGymResponse;
import apex.stellar.antares.dto.DisplayNameResponse;
import apex.stellar.antares.dto.GymPublicResponse;
import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.GymResponse;
import apex.stellar.antares.dto.GymSettingsRequest;
import apex.stellar.antares.dto.GymSettingsResponse;
import apex.stellar.antares.dto.GymUpdateRequest;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.EmailNotVerifiedException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.GymMapper;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymType;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class responsible for managing gym-related operations such as creating, updating,
 * retrieving, and deleting gyms, as well as handling gym settings and memberships.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GymService {

  private static final String ERROR_GYM_NOT_FOUND = "error.gym.not.found";
  private static final int SEARCH_MAX_PAGE_SIZE = 50;
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
  private final UserMapper userMapper;
  private final EmailVerificationService emailVerificationService;

  /**
   * Creates a gym creation request and makes the requester its holder.
   *
   * <p>Any user may ask for a box or program: the gym starts {@code PENDING_APPROVAL} (a platform
   * admin approves, rejects or later suspends it), with a random enrollment code, and the requester
   * becomes its ACTIVE OWNER (physical) or PROGRAMMER (virtual). A user may have at most one
   * request waiting for approval.
   *
   * @param request The {@link GymRequest} containing the details of the gym to be created.
   * @param creator The {@link User} who is creating the gym and will be assigned as the owner.
   * @return A {@link GymResponse} object representing the newly created gym.
   * @throws DataConflictException If the user already has a pending request, or the name is taken.
   * @throws EmailNotVerifiedException If the requester's address is not verified (403).
   */
  @Transactional
  public GymResponse createGym(GymRequest request, User creator) {
    // 0. Only verified addresses may request a gym (no throwaway accounts)
    emailVerificationService.requireVerified(creator);

    // 1. At most one request waiting for approval per user
    if (membershipRepository.existsPendingGymRequest(creator.getId())) {
      throw new DataConflictException("error.gym.request.pending");
    }

    // 2. Uniqueness Check (Name only, ignoring case)
    String name = request.name().trim();
    if (gymRepository.existsByNameIgnoreCase(name)) {
      throw new DataConflictException("error.gym.name.exists", name);
    }

    // 3. Entity Creation
    Gym gym = gymMapper.toEntity(request);
    gym.setName(name);
    gym.setDescription(blankToNull(request.description()));
    gym.setEnrollmentCode(newEnrollmentCode());
    Gym savedGym = gymRepository.save(gym);

    // 4. Holder membership: OWNER (physical) or PROGRAMMER (virtual)
    Membership ownerMembership =
        Membership.builder()
            .gym(savedGym)
            .user(creator)
            .gymRole(savedGym.holderRole())
            .status(MembershipStatus.ACTIVE)
            .permissions(new HashSet<>(FULL_ADMIN_PERMISSIONS))
            .build();

    membershipRepository.save(ownerMembership);
    log.info("Gym creation requested: {} by userId={}", savedGym, creator.getId());

    return gymMapper.toResponse(savedGym);
  }

  /**
   * Renames a gym or changes its description.
   *
   * @param gymId The gym to update.
   * @param request The new name and description.
   * @return The updated settings view.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   * @throws DataConflictException If another gym already has this name.
   */
  @Transactional
  public GymSettingsResponse updateGym(Long gymId, GymUpdateRequest request) {
    Gym gym = findGym(gymId);
    String name = request.name().trim();
    if (gymRepository.existsByNameIgnoreCaseAndIdNot(name, gymId)) {
      throw new DataConflictException("error.gym.name.exists", name);
    }
    gym.setName(name);
    gym.setDescription(blankToNull(request.description()));
    return gymMapper.toSettingsResponse(gymRepository.save(gym));
  }

  /**
   * Replaces the enrollment code of a gym with a new random one (e.g. after it leaked). Pending
   * invitation links stop working.
   *
   * @param gymId The gym.
   * @return The updated settings view.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   */
  @Transactional
  public GymSettingsResponse regenerateEnrollmentCode(Long gymId) {
    Gym gym = findGym(gymId);
    gym.setEnrollmentCode(newEnrollmentCode());
    return gymMapper.toSettingsResponse(gymRepository.save(gym));
  }

  /**
   * Updates the settings for a specific gym.
   *
   * @param gymId The unique identifier of the gym whose settings are being updated.
   * @param request The {@link GymSettingsRequest} containing the new settings.
   * @return The updated settings view.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   */
  @Transactional
  public GymSettingsResponse updateSettings(Long gymId, GymSettingsRequest request) {
    Gym gym = findGym(gymId);
    gym.setEnrollmentCode(request.enrollmentCode().trim());
    gym.setAutoSubscription(request.isAutoSubscription());
    return gymMapper.toSettingsResponse(gymRepository.save(gym));
  }

  /**
   * Retrieves the current settings for a specific gym.
   *
   * @param gymId The unique identifier of the gym.
   * @return The gym's name, description, status, enrollment code and auto-subscription flag.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   */
  @Transactional(readOnly = true)
  public GymSettingsResponse getSettings(Long gymId) {
    return gymMapper.toSettingsResponse(findGym(gymId));
  }

  /**
   * Lists the members of a gym whose membership is usable ({@link Membership#isUsable()}), with
   * their display name, sorted by name. Feeds the athlete selector of the staff entering
   * competition scores.
   *
   * @param gymId The unique identifier of the gym.
   * @return The usable members of the gym (empty for an unknown gym).
   */
  @Transactional(readOnly = true)
  public List<DisplayNameResponse> getAthletes(Long gymId) {
    return membershipRepository.findByGymId(gymId).stream()
        .filter(Membership::isUsable)
        .map(m -> new DisplayNameResponse(m.getUserId(), UserService.displayName(m.getUser())))
        .sorted(
            Comparator.comparing(DisplayNameResponse::displayName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /**
   * Tells whether a gym is a virtual programming (internal API for Aldebaran).
   *
   * @param gymId The unique identifier of the gym.
   * @return {@code true} for a virtual programming, {@code false} for a physical gym.
   * @throws ResourceNotFoundException If no gym is found with the provided ID.
   */
  @Transactional(readOnly = true)
  public boolean isProgramming(Long gymId) {
    return gymRepository
        .findById(gymId)
        .map(Gym::isProgramming)
        .orElseThrow(() -> new ResourceNotFoundException(ERROR_GYM_NOT_FOUND, gymId));
  }

  /**
   * Checks whether a user holds a usable membership in a gym ({@link Membership#isUsable()}).
   * Called by Aldebaran (internal API) before letting gym staff log a score for an athlete.
   *
   * @param gymId The unique identifier of the gym.
   * @param userId The unique identifier of the user.
   * @return {@code true} if the user is a usable member of the gym.
   */
  @Transactional(readOnly = true)
  public boolean isUsableMember(Long gymId, Long userId) {
    return membershipRepository
        .findByUserIdAndGymId(userId, gymId)
        .map(Membership::isUsable)
        .orElse(false);
  }

  /**
   * Searches the ACTIVE gyms a user may join, by name and type.
   *
   * @param search text matched against the name (case-insensitive); blank matches every gym.
   * @param type the type to keep, or null for both.
   * @param pageable the requested page (size capped at {@value #SEARCH_MAX_PAGE_SIZE}).
   * @return a slice of public gym views, ordered by name.
   */
  @Transactional(readOnly = true)
  public Slice<GymPublicResponse> searchActiveGyms(
      String search, GymType type, Pageable pageable) {
    Pageable page = capped(pageable);
    Slice<Gym> gyms =
        gymRepository.search(likePattern(search), programmingOf(type), GymStatus.ACTIVE, page);
    return gyms.map(gymMapper::toPublicResponse);
  }

  /**
   * Returns the public view of an ACTIVE gym (e.g. the gym of an invitation link).
   *
   * @param gymId The gym.
   * @return The public view.
   * @throws ResourceNotFoundException If the gym does not exist or is not ACTIVE.
   */
  @Transactional(readOnly = true)
  public GymPublicResponse getActiveGym(Long gymId) {
    return gymRepository
        .findById(gymId)
        .filter(g -> g.getStatus() == GymStatus.ACTIVE)
        .map(gymMapper::toPublicResponse)
        .orElseThrow(() -> new ResourceNotFoundException(ERROR_GYM_NOT_FOUND, gymId));
  }

  /**
   * Searches every gym for platform admins (approval queue, suspension), with its holder and its
   * number of ACTIVE members.
   *
   * @param search text matched against the name (case-insensitive); blank matches every gym.
   * @param status the status to keep, or null for any.
   * @param pageable the requested page (size capped at {@value #SEARCH_MAX_PAGE_SIZE}).
   * @return a slice of admin gym views, ordered by name.
   */
  @Transactional(readOnly = true)
  public Slice<AdminGymResponse> searchGymsForAdmin(
      String search, GymStatus status, Pageable pageable) {
    Pageable page = capped(pageable);
    Slice<Gym> gyms = gymRepository.search(likePattern(search), null, status, page);
    List<Long> ids = gyms.getContent().stream().map(Gym::getId).toList();
    if (ids.isEmpty()) {
      return new SliceImpl<>(List.of(), page, false);
    }
    Map<Long, User> holders =
        membershipRepository.findHoldersByGymIdIn(ids).stream()
            .collect(Collectors.toMap(Membership::getGymId, Membership::getUser, (a, b) -> a));
    Map<Long, Long> counts =
        membershipRepository.countByGymIdInAndStatus(ids, MembershipStatus.ACTIVE).stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    return gyms.map(
        g ->
            new AdminGymResponse(
                g.getId(),
                g.getName(),
                g.getDescription(),
                g.isProgramming(),
                g.getStatus(),
                g.getCreatedAt(),
                holders.containsKey(g.getId())
                    ? userMapper.toSummary(holders.get(g.getId()))
                    : null,
                counts.getOrDefault(g.getId(), 0L)));
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
    log.info("Gym {} status set to {}", id, status);
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

  // --- Helpers ---

  private Gym findGym(Long gymId) {
    return gymRepository
        .findById(gymId)
        .orElseThrow(() -> new ResourceNotFoundException(ERROR_GYM_NOT_FOUND, gymId));
  }

  /** A random 8-character enrollment code (upper-case hexadecimal). */
  private static String newEnrollmentCode() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }

  private static Pageable capped(Pageable pageable) {
    return PageRequest.of(
        pageable.getPageNumber(), Math.min(pageable.getPageSize(), SEARCH_MAX_PAGE_SIZE));
  }

  /** Lower-case {@code LIKE} pattern matching the text anywhere, with wildcards escaped. */
  private static String likePattern(String search) {
    String text = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  private static Boolean programmingOf(GymType type) {
    return type == null ? null : type == GymType.PROGRAM;
  }
}
