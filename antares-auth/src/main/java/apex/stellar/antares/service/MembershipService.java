package apex.stellar.antares.service;

import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.dto.MembershipUpdateRequest;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class MembershipService {

  private final MembershipRepository membershipRepository;
  private final GymRepository gymRepository;
  private final MembershipMapper membershipMapper;

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
    }
    return isGlobalAdmin;
  }

  /**
   * Facilitates a user joining a specific Gym using a secure enrollment code.
   *
   * <p>This method enforces several checks:
   *
   * <ol>
   *   <li>Verifies the Gym exists.
   *   <li>Validates the provided enrollment code against the Gym's current code.
   *   <li>Ensures the user is not already a member (Duplicate check).
   * </ol>
   *
   * @param request The {@link JoinGymRequest} containing the target Gym ID and the enrollment code.
   * @param user The authenticated {@link User} attempting to join.
   * @return A {@link MembershipResponse} detailing the newly created membership.
   * @throws ResourceNotFoundException If the Gym with the specified ID does not exist.
   * @throws AccessDeniedException If the provided enrollment code is invalid.
   * @throws DataConflictException If the user already holds a membership in this Gym.
   */
  @Transactional
  public MembershipResponse joinGym(JoinGymRequest request, User user) {
    Gym gym =
        gymRepository
            .findById(request.gymId())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.gym.not.found", request.gymId()));

    // 1. Validate Enrollment Code
    if (!gym.getEnrollmentCode().equals(request.enrollmentCode())) {
      throw new AccessDeniedException("error.enrollment.code.invalid");
    }

    // 2. Check for existing membership (Performance optimized: existsBy... instead of findBy...)
    if (membershipRepository.existsByUserIdAndGymId(user.getId(), gym.getId())) {
      throw new DataConflictException("error.membership.exists");
    }

    // 3. Create Membership
    Membership membership =
        Membership.builder()
            .user(user)
            .gym(gym)
            .gymRole(GymRole.ATHLETE)
            .status(gym.isAutoSubscription() ? MembershipStatus.ACTIVE : MembershipStatus.PENDING)
            .build();

    return membershipMapper.toResponse(membershipRepository.save(membership));
  }

  /**
   * Retrieves a list of memberships for a specific Gym, optionally filtered by status.
   *
   * <p>This method is typically used by Gym Owners or Coaches to view their member base.
   *
   * @param gymId The unique identifier of the Gym.
   * @param status An optional {@link MembershipStatus} filter. If null, all memberships are
   *     returned.
   * @return A list of {@link MembershipResponse} objects matching the criteria.
   */
  @Transactional(readOnly = true)
  public List<MembershipResponse> getMemberships(Long gymId, MembershipStatus status) {
    List<Membership> memberships =
        (status != null)
            ? membershipRepository.findByGymIdAndStatus(gymId, status)
            : membershipRepository.findByGymId(gymId);

    return memberships.stream().map(membershipMapper::toResponse).toList();
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
   * Updates the status, role, and permissions of an existing membership.
   *
   * <p><b>Security Note:</b> This method implements a strict Role-Based Access Control (RBAC)
   * hierarchy:
   *
   * <ul>
   *   <li><b>Global Admin:</b> Can perform any update, including assigning the ADMIN role.
   *   <li><b>Cross-Tenant Protection:</b> Requesters cannot modify memberships in gyms they do not
   *       belong to.
   *   <li><b>Coach:</b> Can only validate members (PENDING -> ACTIVE). Cannot change Roles or
   *       Permissions.
   *   <li><b>Owner/Programmer:</b> Can manage all aspects except assigning the Global ADMIN role.
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
   */
  @Transactional
  public MembershipResponse updateMembership(
      Long targetMembershipId,
      MembershipUpdateRequest request,
      User requester,
      Membership requesterMembership) {

    Membership target =
        membershipRepository
            .findById(targetMembershipId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "error.membership.not.found", targetMembershipId));

    // --- 1. Validate Context (Tenant Security) ---
    boolean isGlobalAdmin = validateTenantAccess(requester, requesterMembership, target);

    GymRole requesterRole =
        requesterMembership != null ? requesterMembership.getGymRole() : GymRole.ATHLETE;

    // --- 2. Global Admin Protection Logic (Updated) ---
    // Prevent tampering: Lower roles cannot modify global Admins
    if (target.getUser().getPlatformRole() == PlatformRole.ADMIN && !isGlobalAdmin) {
      throw new AccessDeniedException("Cannot modify a Global Admin.");
    }

    // --- 3. Coach Logic (Delegated Administration) ---
    if (!isGlobalAdmin && requesterRole == GymRole.COACH) {
      // Must have the specific delegation permission
      if (!requesterMembership.getPermissions().contains(Permission.MANAGE_MEMBERSHIPS)) {
        throw new AccessDeniedException("Missing MANAGE_MEMBERSHIPS permission.");
      }
      // Coaches can only manage Athletes (cannot manage Owners or other Coaches)
      if (target.getGymRole() != GymRole.ATHLETE) {
        throw new AccessDeniedException("Coaches can only manage Athletes.");
      }
      // Coaches are restricted to Status updates only (e.g., Validation)
      if (request.gymRole() != target.getGymRole()
          || !request.permissions().equals(target.getPermissions())) {
        throw new AccessDeniedException("Coaches can only update Status.");
      }
    }

    // --- 4. Apply Updates ---
    // Owners/Programmers fall through here with full access (except the Admin checks above)
    target.setStatus(request.status());
    target.setGymRole(request.gymRole());
    target.setPermissions(request.permissions());

    return membershipMapper.toResponse(membershipRepository.save(target));
  }

  /**
   * Permanently deletes a membership from the system.
   *
   * @param id The unique identifier of the membership to delete.
   * @throws ResourceNotFoundException If the membership does not exist.
   */
  @Transactional
  public void deleteMembership(Long id) {
    if (!membershipRepository.existsById(id)) {
      throw new ResourceNotFoundException("error.membership.not.found", id);
    }
    membershipRepository.deleteById(id);
  }
}
