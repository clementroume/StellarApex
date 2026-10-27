package apex.stellar.aldebaran.security;

import static apex.stellar.aldebaran.security.SecurityService.SCORE_VERIFY;
import static apex.stellar.aldebaran.security.SecurityService.WOD_WRITE;

import apex.stellar.aldebaran.dto.WodCopyRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.service.WodService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security bean responsible for evaluating authorization logic for WOD resources.
 *
 * <p>This component guards the creation, modification, and deletion of Workouts of the Day (WODs).
 * It acts as a gatekeeper before the service layer.
 *
 * <p><b>Strategy regarding "Not Found":</b> Read/Update/Delete methods return {@code true} if the
 * WOD ID is not found. This delegates the responsibility to the Service layer to throw a proper
 * {@code ResourceNotFoundException} (HTTP 404), rather than triggering a generic {@code
 * AccessDeniedException} (HTTP 403) which would hide the resource state.
 */
@Component("wodSecurity")
@RequiredArgsConstructor
public class WodSecurity {

  private final SecurityService securityService;
  private final WodService wodService;

  /**
   * Determines whether the authenticated user is authorized to read the specified WOD (Workout of
   * the Day).
   *
   * <p>The method evaluates the user's privileges based on the WOD's visibility (public,
   * gym-specific, or private), the user's role (e.g., administrator), and the context of the user's
   * gym and authored content.
   *
   * @param wodId The WOD to be evaluated.
   * @param principal The authenticated user context, represented as an {@link
   *     AldebaranUserPrincipal}. Contains RBAC (Role-Based Access Control) and tenant-specific
   *     information.
   * @return {@code true} if the user is authorized to read the specified WOD, otherwise {@code
   *     false}.
   */
  @Transactional(readOnly = true)
  public boolean canRead(Long wodId, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
      return true;
    }

    WodResponse wod = wodService.getWodDetail(wodId);

    // 1. Public: Open to everyone
    if (wod.isPublic()) {
      return true;
    }

    // 2. Gym WOD: Must be a member of that gym
    if (principal.isMemberOf(wod.gymId())) {
      return true;
    }

    // 3. Author: always reads their own WODs
    return Objects.equals(wod.authorId(), principal.getId());
  }

  /**
   * Evaluates if the authenticated user is authorized to create a new WOD.
   *
   * @param request The WOD creation DTO.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized.
   */
  public boolean canCreate(WodRequest request, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
      return true;
    }

    // Gym WOD Creation: WOD_WRITE in the target gym
    if (request.gymId() != null) {
      return securityService.hasGymPermission(principal, request.gymId(), WOD_WRITE);
    }

    // Personal WOD Creation (Self-service)
    return Objects.equals(request.authorId(), principal.getId());
  }

  /**
   * Evaluates if the authenticated user judges a competition WOD: logs scores for the members of
   * its gym and verifies scores. The per-score rules stay in {@code ScoreSecurity}; this flag only
   * tells clients which tools to offer.
   *
   * @param wod The WOD to evaluate.
   * @param principal The authenticated user context.
   * @return {@code true} for a competition WOD and SCORE_VERIFY in its gym (or an admin).
   */
  public boolean canJudge(WodResponse wod, AldebaranUserPrincipal principal) {
    return wod.competition()
        && (securityService.isAdmin(principal)
            || securityService.hasGymPermission(principal, wod.gymId(), SCORE_VERIFY));
  }

  /**
   * Evaluates if the authenticated user may copy a WOD to the requested target (reading the source
   * is checked apart with {@link #canRead}): WOD_WRITE in the target gym, anyone for a personal
   * copy.
   *
   * @param request The copy target.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized.
   */
  public boolean canCopy(WodCopyRequest request, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal) || request.gymId() == null) {
      return true;
    }
    return securityService.hasGymPermission(principal, request.gymId(), WOD_WRITE);
  }

  /**
   * Evaluates if the authenticated user is authorized to update OR delete a WOD. (Unified Logic).
   *
   * @param wodId The unique identifier of the WOD.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if WOD is not found (deferring to 404).
   */
  @Transactional(readOnly = true)
  public boolean canModify(Long wodId, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
      return true;
    }

    return canModify(wodService.getWodDetail(wodId), principal);
  }

  /**
   * Evaluates if the authenticated user is authorized to update OR delete an already loaded WOD.
   * For a gym WOD, the user needs WOD_WRITE in the gym owning the WOD.
   *
   * @param wod The WOD to evaluate.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized.
   */
  public boolean canModify(WodResponse wod, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
      return true;
    }

    // Gym WOD: WOD_WRITE in the WOD's gym
    if (wod.gymId() != null) {
      return securityService.hasGymPermission(principal, wod.gymId(), WOD_WRITE);
    }

    // Personal WOD: Author only
    return Objects.equals(wod.authorId(), principal.getId());
  }
}
