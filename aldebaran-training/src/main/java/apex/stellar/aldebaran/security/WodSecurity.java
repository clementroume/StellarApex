package apex.stellar.aldebaran.security;

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

    // 2. Gym WOD: Must be in the same gym context
    if (wod.gymId() != null) {
      return Objects.equals(wod.gymId(), principal.getGymId());
    }

    // 3. Private WOD: Must be the author
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

    // Gym WOD Creation
    if (request.gymId() != null) {
      // Must be in the target gym + Have Staff Write Rights
      return Objects.equals(principal.getGymId(), request.gymId())
          && securityService.hasWodWriteAccess(principal);
    }

    // Personal WOD Creation (Self-service)
    return Objects.equals(request.authorId(), principal.getId());
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

    WodResponse wod = wodService.getWodDetail(wodId);

    // Gym WOD: Same Gym + Staff Rights
    if (wod.gymId() != null) {
      return Objects.equals(principal.getGymId(), wod.gymId())
          && securityService.hasWodWriteAccess(principal);
    }

    // Personal WOD: Author only
    return Objects.equals(wod.authorId(), principal.getId());
  }
}
