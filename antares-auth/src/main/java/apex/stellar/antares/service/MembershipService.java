package apex.stellar.antares.service;

import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.dto.MembershipCapabilities;
import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.dto.MembershipUpdateRequest;
import apex.stellar.antares.exception.BusinessRuleException;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.EmailNotVerifiedException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.exception.TooManyAttemptsException;
import apex.stellar.antares.mapper.MembershipMapper;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for managing the lifecycle of {@link Membership} entities.
 *
 * <p>This service handles the business logic for:
 *
 * <ul>
 *   <li>Users joining a Gym (Enrollment).
 *   <li>Retrieving membership lists for Gym Staff.
 *   <li>Updating membership details with strict hierarchical access control.
 *   <li>Removing members from a Gym.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

  /** Pending requests first, then ACTIVE, INACTIVE, BANNED; by last then first name. */
  private static final Comparator<Membership> MEMBER_ORDER =
      Comparator.comparing(Membership::getStatus)
          .thenComparing(
              m -> m.getUser().getLastName(),
              Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
          .thenComparing(
              m -> m.getUser().getFirstName(),
              Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

  private final MembershipRepository membershipRepository;
  private final GymRepository gymRepository;
  private final MembershipMapper membershipMapper;
  private final EnrollmentAttemptService enrollmentAttemptService;
  private final EmailVerificationService emailVerificationService;

  /**
   * Helper method to validate the security context for membership updates.
   *
   * <p>Ensures that a non-admin requester has a valid membership in the <b>same</b> Gym (Tenant) as
   * the target membership. This prevents cross-tenant unauthorized access.
   *
   * @param requester The user attempting the action.
   * @param requesterMembership The requester's membership details (can be null for Global Admins).
   * @param target The membership being modified.
   * @return {@code true} if the requester is a Global Admin, {@code false} otherwise.
   * @throws AccessDeniedException if the context is invalid or violates tenant isolation.
   */
  private boolean validateTenantAccess(
      User requester, Membership requesterMembership, Membership target) {
    boolean isGlobalAdmin = requester.getPlatformRole() == PlatformRole.ADMIN;

    if (!isGlobalAdmin) {
      if (requesterMembership == null) {
        // A non-admin user must have a membership to act on another membership
        throw new AccessDeniedException("error.access.denied");
      }
      if (!requesterMembership.getGymId().equals(target.getGymId())) {
        // Cross-Tenant violation: Owner of Gym A cannot touch Gym B
        throw new AccessDeniedException("error.access.denied");
      }
      // Lower roles cannot modify platform admins
      if (target.getUser().getPlatformRole() == PlatformRole.ADMIN) {
        throw new AccessDeniedException("error.access.denied");
      }
    }
    return isGlobalAdmin;
  }

  /**
   * Applies the gym-type role rules to a role change requested through {@link #updateMembership}.
   */
  private void validateRoleChange(Membership target, GymRole requestedRole) {
    GymRole currentRole = target.getGymRole();
    if (requestedRole == currentRole) {
      return;
    }
    if (currentRole != null && currentRole.isHolder()) {
      throw new BusinessRuleException("error.membership.role.holder.change");
    }
    Gym gym = target.getGym();
    if (gym != null && !gym.allowsRole(requestedRole)) {
      throw new BusinessRuleException("error.membership.role.not.allowed", requestedRole);
    }
    if (requestedRole.isHolder()) {
      throw new BusinessRuleException("error.membership.role.holder.assign");
    }
  }

  /**
   * Lets a user join an ACTIVE gym with its enrollment code.
   *
   * <ul>
   *   <li>New member: an ATHLETE membership, PENDING (or ACTIVE with auto-subscription).
   *   <li>Former member (INACTIVE): the same membership is reactivated the same way, as ATHLETE
   *       without permissions, so history and scores stay attached to it.
   *   <li>BANNED member: refused (409); PENDING or ACTIVE member: already a member (409).
   * </ul>
   *
   * @param request The {@link JoinGymRequest} containing the target Gym ID and the enrollment code.
   * @param user The authenticated {@link User} attempting to join.
   * @return The membership as the user sees it in their own list.
   * @throws ResourceNotFoundException If the gym does not exist or is not ACTIVE.
   * @throws BusinessRuleException If the enrollment code is wrong (400), counted by {@link
   *     EnrollmentAttemptService}.
   * @throws TooManyAttemptsException After too many wrong codes in a short time (429).
   * @throws DataConflictException If the user is already a member, or banned.
   * @throws EmailNotVerifiedException If the user's address is not verified (403).
   */
  @Transactional
  public MembershipSummary joinGym(JoinGymRequest request, User user) {
    // Only verified addresses may join (throwaway accounts would multiply code guesses)
    emailVerificationService.requireVerified(user);

    // 0. Too many wrong codes recently: refuse before looking at this one
    enrollmentAttemptService.checkAllowed(user.getId());

    Gym gym =
        gymRepository
            .findById(request.gymId())
            .filter(g -> g.getStatus() == Gym.GymStatus.ACTIVE)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.gym.not.found", request.gymId()));

    // 1. Validate Enrollment Code (case-insensitive, constant time)
    if (!codeMatches(gym.getEnrollmentCode(), request.enrollmentCode())) {
      enrollmentAttemptService.recordFailure(user.getId());
      throw new BusinessRuleException("error.enrollment.code.invalid");
    }

    MembershipStatus joinStatus =
        gym.isAutoSubscription() ? MembershipStatus.ACTIVE : MembershipStatus.PENDING;

    // 2. Existing membership: reactivate a former member, refuse the others
    Membership membership =
        membershipRepository.findByUserIdAndGymId(user.getId(), gym.getId()).orElse(null);
    if (membership != null) {
      switch (membership.getStatus()) {
        case BANNED -> throw new DataConflictException("error.membership.banned");
        case PENDING, ACTIVE -> throw new DataConflictException("error.membership.exists");
        case INACTIVE -> {
          membership.setGymRole(GymRole.ATHLETE);
          membership.setPermissions(new HashSet<>());
          membership.setStatus(joinStatus);
        }
      }
    } else {
      // 3. Create Membership
      membership =
          Membership.builder()
              .user(user)
              .gym(gym)
              .gymRole(GymRole.ATHLETE)
              .status(joinStatus)
              .build();
    }

    return membershipMapper.toSummary(membershipRepository.save(membership));
  }

  /**
   * Lets a member leave a gym. An ACTIVE membership becomes INACTIVE (history and scores are kept,
   * joining again reactivates it); a PENDING request is withdrawn (deleted); leaving twice changes
   * nothing.
   *
   * @param gymId The gym to leave.
   * @param user The member.
   * @throws ResourceNotFoundException If the user has no membership in this gym.
   * @throws DataConflictException If the user holds the gym (transfer it first) or is banned.
   */
  @Transactional
  public void leaveGym(Long gymId, User user) {
    Membership membership =
        membershipRepository
            .findByUserIdAndGymId(user.getId(), gymId)
            .orElseThrow(() -> new ResourceNotFoundException("error.membership.not.found"));
    if (membership.getGymRole().isHolder()) {
      throw new DataConflictException("error.membership.leave.holder");
    }
    switch (membership.getStatus()) {
      case BANNED -> throw new DataConflictException("error.membership.banned");
      case PENDING -> membershipRepository.delete(membership);
      case ACTIVE -> {
        membership.setStatus(MembershipStatus.INACTIVE);
        membershipRepository.save(membership);
      }
      case INACTIVE -> {
        // Already left
      }
    }
    log.info("userId={} left gym {}", user.getId(), gymId);
  }

  /**
   * Retrieves a list of memberships for a specific Gym, optionally filtered by status.
   *
   * <p>Used by gym staff to manage their members: pending requests first, then ACTIVE, INACTIVE
   * and BANNED members, each group by name; every entry carries what the requester may do with it.
   *
   * @param gymId The unique identifier of the Gym.
   * @param status An optional {@link MembershipStatus} filter. If null, all memberships are
   *     returned.
   * @param requester The authenticated user listing the members.
   * @return A list of {@link MembershipResponse} objects matching the criteria.
   */
  @Transactional(readOnly = true)
  public List<MembershipResponse> getMemberships(
      Long gymId, MembershipStatus status, User requester) {
    List<Membership> memberships =
        (status != null)
            ? membershipRepository.findByGymIdAndStatus(gymId, status)
            : membershipRepository.findByGymId(gymId);
    Membership requesterMembership = requesterMembership(requester, gymId);

    return memberships.stream()
        .sorted(MEMBER_ORDER)
        .map(m -> membershipMapper.toResponse(m, capabilities(requester, requesterMembership, m)))
        .toList();
  }

  /**
   * Computes what a user may do with a membership. This is the single source of the membership
   * management rules, enforced by {@link #updateMembership} and {@link #removeMembership} and sent
   * to clients as {@link MembershipResponse#capabilities()}.
   *
   * <ul>
   *   <li>Nobody but a platform admin acts on a platform admin's membership, nor outside their own
   *       gym, nor without an ACTIVE membership there.
   *   <li>The holder (OWNER/PROGRAMMER) and platform admins manage every other member: status,
   *       role and permissions (physical box only: a virtual program has athletes only), removal,
   *       ban, and ownership transfer to an ACTIVE member.
   *   <li>A COACH with {@code MANAGE_MEMBERSHIPS} validates, refuses, deactivates or reactivates
   *       ATHLETES only: no role, permission or ban change.
   *   <li>The holder's own membership changes only through an ownership transfer.
   * </ul>
   *
   * @param requester The user asking.
   * @param requesterMembership The requester's membership in the target's gym (null if none).
   * @param target The membership acted upon.
   * @return The allowed actions.
   */
  public MembershipCapabilities capabilities(
      User requester, Membership requesterMembership, Membership target) {
    boolean admin = requester.getPlatformRole() == PlatformRole.ADMIN;
    if (!admin) {
      boolean sameGym =
          requesterMembership != null
              && requesterMembership.getStatus() == MembershipStatus.ACTIVE
              && Objects.equals(requesterMembership.getGymId(), target.getGymId());
      if (!sameGym || target.getUser().getPlatformRole() == PlatformRole.ADMIN) {
        return MembershipCapabilities.NONE;
      }
    }
    if (target.getGymRole().isHolder()) {
      return MembershipCapabilities.NONE;
    }
    if (admin || requesterMembership.getGymRole().isHolder()) {
      boolean physical = !target.getGym().isProgramming();
      return new MembershipCapabilities(
          true, physical, physical, true, true, target.getStatus() == MembershipStatus.ACTIVE);
    }
    boolean delegated =
        requesterMembership.activePermissions().contains(Permission.MANAGE_MEMBERSHIPS)
            && target.getGymRole() == GymRole.ATHLETE;
    return new MembershipCapabilities(delegated, false, false, delegated, false, false);
  }

  /**
   * Retrieves the list of Gyms the authenticated user belongs to.
   *
   * <p>This method returns a lightweight summary used for the "Gym Switcher" in the UI. It
   * leverages the unused {@code findByUserId} repository method and {@code toSummary} mapper.
   *
   * @param user The authenticated user.
   * @return A list of {@link MembershipSummary} objects.
   */
  @Transactional(readOnly = true)
  public List<MembershipSummary> getUserMemberships(User user) {
    return membershipRepository.findByUserId(user.getId()).stream()
        .map(membershipMapper::toSummary)
        .toList();
  }

  /**
   * Updates the status, role, and permissions of an existing membership, following {@link
   * #capabilities}.
   *
   * <ul>
   *   <li><b>Cross-Tenant Protection:</b> requesters cannot modify memberships in gyms they do not
   *       belong to; only platform admins modify a platform admin.
   *   <li><b>Coach</b> ({@code MANAGE_MEMBERSHIPS}): status of Athletes only, never BANNED.
   *   <li><b>Owner/Programmer, platform admin:</b> everything below.
   *   <li><b>Gym type:</b> the new role must exist in the gym type (physical: COACH/ATHLETE,
   *       virtual: ATHLETE); OWNER/PROGRAMMER is never assigned here and the current holder's
   *       membership never changes here (see {@link #transferOwnership}).
   * </ul>
   *
   * @param targetMembershipId The ID of the membership to be updated.
   * @param request The DTO containing the desired state (Role, Status, Permissions).
   * @param requester The authenticated user performing the action.
   * @param requesterMembership The membership of the requester within the <b>same</b> context
   *     (Gym). Can be null if the requester is a Global Admin.
   * @return The updated {@link MembershipResponse}.
   * @throws ResourceNotFoundException If the target membership does not exist.
   * @throws AccessDeniedException If the requester lacks the necessary permissions or violates
   *     hierarchy rules.
   * @throws BusinessRuleException If the change breaks the gym-type or holder rules.
   */
  @Transactional
  public MembershipResponse updateMembership(
      Long targetMembershipId,
      MembershipUpdateRequest request,
      User requester,
      Membership requesterMembership) {

    Membership target = findMembership(targetMembershipId);

    // --- 1. Validate Context (Tenant Security, platform admins untouchable) ---
    boolean isGlobalAdmin = validateTenantAccess(requester, requesterMembership, target);
    boolean isHolder =
        requesterMembership != null
            && requesterMembership.getStatus() == MembershipStatus.ACTIVE
            && requesterMembership.getGymRole().isHolder();

    MembershipCapabilities caps = capabilities(requester, requesterMembership, target);
    boolean statusChanged = request.status() != target.getStatus();
    boolean roleChanged = request.gymRole() != target.getGymRole();
    boolean permissionsChanged = !request.permissions().equals(target.getPermissions());

    // --- 2. Role and permissions: holder and platform admins only, within the gym-type rules ---
    if (roleChanged || permissionsChanged) {
      if (!isGlobalAdmin && !isHolder) {
        throw new AccessDeniedException("error.access.denied");
      }
      validateRoleChange(target, request.gymRole());
      if (permissionsChanged && !caps.canChangePermissions()) {
        throw new BusinessRuleException("error.membership.permissions.not.allowed");
      }
    }

    // --- 3. Status: the holder never changes here; banning needs more than validating ---
    if (statusChanged) {
      if (target.getGymRole().isHolder()) {
        throw new BusinessRuleException("error.membership.holder.status");
      }
      boolean banChange =
          request.status() == MembershipStatus.BANNED
              || target.getStatus() == MembershipStatus.BANNED;
      if (banChange ? !caps.canBan() : !caps.canChangeStatus()) {
        throw new AccessDeniedException("error.access.denied");
      }
    }

    // --- 4. Apply Updates ---
    target.setStatus(request.status());
    target.setGymRole(request.gymRole());
    target.setPermissions(new HashSet<>(request.permissions()));

    Membership saved = membershipRepository.save(target);
    return membershipMapper.toResponse(
        saved, capabilities(requester, requesterMembership, saved));
  }

  /**
   * Removes a member from a gym ({@link MembershipCapabilities#canRemove()}): a PENDING request is
   * refused and deleted (the user may ask again), an ACTIVE member becomes INACTIVE (history and
   * scores are kept). INACTIVE and BANNED memberships are left as they are.
   *
   * @param targetMembershipId The membership to remove.
   * @param requester The authenticated user performing the action.
   * @param requesterMembership The requester's membership in the same gym (null for a platform
   *     admin without one).
   * @throws ResourceNotFoundException If the membership does not exist.
   * @throws DataConflictException If the target holds the gym (transfer it first).
   * @throws AccessDeniedException If the requester may not remove this member.
   */
  @Transactional
  public void removeMembership(
      Long targetMembershipId, User requester, Membership requesterMembership) {
    Membership target = findMembership(targetMembershipId);
    validateTenantAccess(requester, requesterMembership, target);
    if (target.getGymRole().isHolder()) {
      throw new DataConflictException("error.membership.leave.holder");
    }
    if (!capabilities(requester, requesterMembership, target).canRemove()) {
      throw new AccessDeniedException("error.access.denied");
    }
    switch (target.getStatus()) {
      case PENDING -> membershipRepository.delete(target);
      case ACTIVE -> {
        target.setStatus(MembershipStatus.INACTIVE);
        membershipRepository.save(target);
      }
      case INACTIVE, BANNED -> {
        // Nothing to remove
      }
    }
  }

  /**
   * Resolves the membership of a user in a gym, used as the requester context of the management
   * rules.
   *
   * @param user The user.
   * @param gymId The gym.
   * @return The membership, or null if the user has none there.
   */
  @Transactional(readOnly = true)
  public Membership requesterMembership(User user, Long gymId) {
    return membershipRepository.findByUserIdAndGymId(user.getId(), gymId).orElse(null);
  }

  /**
   * Transfers the holder role of a gym to another active member.
   *
   * <p>The target becomes OWNER (physical gym) or PROGRAMMER (virtual gym); the former holder, if
   * any, becomes COACH (physical) or ATHLETE (virtual). Both lose their explicit permissions.
   *
   * @param gymId The gym whose holder changes.
   * @param membershipId The membership of the new holder.
   * @return The updated memberships (former holder first, when there is one; then the new holder).
   * @throws ResourceNotFoundException If the gym or the membership does not exist.
   * @throws BusinessRuleException If the target is not an active member of this gym, or already
   *     holds it.
   */
  @Transactional
  public List<MembershipResponse> transferOwnership(Long gymId, Long membershipId) {
    Gym gym =
        gymRepository
            .findById(gymId)
            .orElseThrow(() -> new ResourceNotFoundException("error.gym.not.found", gymId));
    Membership target =
        membershipRepository
            .findById(membershipId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.membership.not.found", membershipId));

    if (!gymId.equals(target.getGymId()) || target.getStatus() != MembershipStatus.ACTIVE) {
      throw new BusinessRuleException("error.membership.transfer.target.invalid");
    }
    if (target.getGymRole().isHolder()) {
      throw new BusinessRuleException("error.membership.transfer.target.holder");
    }

    List<Membership> updated = new ArrayList<>();
    for (Membership former : membershipRepository.findHoldersByGymId(gymId)) {
      former.setGymRole(gym.formerHolderRole());
      former.setPermissions(new HashSet<>());
      // Flush before promoting: the unique holder index allows one holder per gym at any time
      updated.add(membershipRepository.saveAndFlush(former));
    }

    target.setGymRole(gym.holderRole());
    target.setPermissions(new HashSet<>());
    updated.add(membershipRepository.saveAndFlush(target));

    log.info(
        "Gym {} ownership transferred to membership {} (userId={})",
        gymId,
        membershipId,
        target.getUserId());

    return updated.stream().map(membershipMapper::toResponse).toList();
  }

  // --- Helpers ---

  private Membership findMembership(Long id) {
    return membershipRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("error.membership.not.found", id));
  }

  /** Compares enrollment codes ignoring case and surrounding spaces, in constant time. */
  private static boolean codeMatches(String expected, String given) {
    if (expected == null || given == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
        given.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
  }
}
