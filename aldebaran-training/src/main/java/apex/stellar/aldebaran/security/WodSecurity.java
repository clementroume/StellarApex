package apex.stellar.aldebaran.security;

import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.repository.WodRepository;
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

  private final WodRepository wodRepository;
  private final SecurityService securityService;

  /**
   * Evaluates if the authenticated user is authorized to view a specific WOD.
   *
   * @param wodId The unique identifier of the WOD.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if WOD is not found (deferring to 404).
   */
  @Transactional(readOnly = true)
  public boolean canRead(Long wodId, AldebaranUserPrincipal principal) {
    if (securityService.isAdmin(principal)) {
      return true;
    }

    return wodRepository
        .findById(wodId)
        .map(
            wod -> {
              // 1. Public: Open to everyone
              if (wod.isPublic()) {
                return true;
              }

              // 2. Gym WOD: Must be in the same gym context
              if (wod.getGymId() != null) {
                return Objects.equals(wod.getGymId(), principal.getGymId());
              }

              // 3. Private WOD: Must be the author
              return Objects.equals(wod.getAuthorId(), principal.getId());
            })
        .orElse(true); // Allow service to handle 404
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

    return wodRepository
        .findById(wodId)
        .map(
            wod -> {
              // Gym WOD: Same Gym + Staff Rights
              if (wod.getGymId() != null) {
                return Objects.equals(principal.getGymId(), wod.getGymId())
                    && securityService.hasWodWriteAccess(principal);
              }

              // Personal WOD: Author only
              return Objects.equals(wod.getAuthorId(), principal.getId());
            })
        .orElse(true); // Allow service to handle 404
  }
}
