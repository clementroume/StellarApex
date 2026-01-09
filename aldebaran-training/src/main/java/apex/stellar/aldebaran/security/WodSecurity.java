package apex.stellar.aldebaran.security;

import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.WodRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Security bean responsible for evaluating authorization logic for WOD resources.
 *
 * <p>This component is designed to be used within SpEL (Spring Expression Language) expressions in
 * {@code @PreAuthorize} annotations (e.g., {@code @PreAuthorize("@wodSecurity.canCreate(...)")}).
 * It enforces business rules regarding Gym ownership, Staff roles, and Athlete permissions.
 */
@Component("wodSecurity")
@RequiredArgsConstructor
public class WodSecurity {

  // Role Constants matching Antares Enums
  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String ROLE_OWNER = "ROLE_OWNER";
  private static final String ROLE_PROGRAMMER = "ROLE_PROGRAMMER";
  private static final String ROLE_COACH = "ROLE_COACH";
  // Permission Constants
  private static final String PERM_WOD_WRITE = "WOD_WRITE";
  private final WodRepository wodRepository;

  /**
   * Evaluates if the authenticated user is authorized to create the specified WOD.
   *
   * @param request The WOD creation payload.
   * @param principal The authenticated user context.
   * @return true if the operation is authorized.
   */
  public boolean canCreate(WodRequest request, AldebaranUserPrincipal principal) {
    if (isAdmin(principal)) {
      return true;
    }

    // 1. Staff Logic: Must create for their own gym
    if (isStaff(principal)) {
      if (!Objects.equals(request.gymId(), principal.getGymId())) {
        return false;
      }
      // Coaches specifically need the WRITE permission
      if (ROLE_COACH.equals(principal.getRole())) {
        return principal.hasPermission(PERM_WOD_WRITE);
      }
      return true; // Owners and Programmers are implicitly allowed
    }

    // 2. Athlete Logic: Personal WODs only (no gym association)
    if (request.gymId() == null) {
      // The author ID in the request must match the principal ID
      return Objects.equals(request.authorId(), principal.getId());
    }

    return false;
  }

  /**
   * Evaluates if the authenticated user is authorized to update the specified WOD.
   *
   * @param wodId The ID of the target WOD.
   * @param principal The authenticated user context.
   * @return true if the operation is authorized.
   */
  public boolean canUpdate(Long wodId, AldebaranUserPrincipal principal) {
    return wodRepository
        .findById(wodId)
        .map(wod -> checkAccess(wod, principal))
        // Deny access if WOD is not found (Controller will likely handle 404 separately,
        // but returning false ensures security by default)
        .orElse(false);
  }

  /**
   * Evaluates if the authenticated user is authorized to delete the specified WOD.
   *
   * @param wodId The ID of the target WOD.
   * @param principal The authenticated user context.
   * @return true if the operation is authorized.
   */
  public boolean canDelete(Long wodId, AldebaranUserPrincipal principal) {
    return canUpdate(wodId, principal); // Delete rules mirror Update rules
  }

  /** Internal logic to verify access against an existing WOD entity. */
  private boolean checkAccess(Wod wod, AldebaranUserPrincipal principal) {
    if (isAdmin(principal)) {
      return true;
    }

    // Staff Logic: Must be staff AND accessing a WOD from their own gym
    if (isStaff(principal) && Objects.equals(wod.getGymId(), principal.getGymId())) {
      // Coaches need specific permission
      if (ROLE_COACH.equals(principal.getRole())) {
        return principal.hasPermission(PERM_WOD_WRITE);
      }
      // Owners/Programmers have implicit access
      return true;
    }

    // Athlete Logic (Personal WODs)
    // Must be a global/personal WOD (gymId is null) AND belong to the user
    return wod.getGymId() == null && Objects.equals(wod.getAuthorId(), principal.getId());
  }

  private boolean isAdmin(AldebaranUserPrincipal principal) {
    return ROLE_ADMIN.equals(principal.getRole());
  }

  private boolean isStaff(AldebaranUserPrincipal principal) {
    String role = principal.getRole();
    return ROLE_OWNER.equals(role) || ROLE_PROGRAMMER.equals(role) || ROLE_COACH.equals(role);
  }
}
