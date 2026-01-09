package apex.stellar.aldebaran.security;

import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security bean responsible for evaluating authorization logic for WodScore resources.
 *
 * <p>Enforces the strict authorization matrix for creating, reading, updating, and deleting scores
 * based on roles (Admin, Coach, User) and context (Gym ownership, Data ownership).
 */
@Component("wodScoreSecurity")
@RequiredArgsConstructor
public class WodScoreSecurity {

  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String ROLE_COACH = "ROLE_COACH";
  private static final String ROLE_OWNER = "ROLE_OWNER";
  private static final String ROLE_PROGRAMMER = "ROLE_PROGRAMMER";

  private final WodScoreRepository scoreRepository;
  private final WodRepository wodRepository;

  /**
   * Evaluates if the user can create a score.
   *
   * <ul>
   *   <li>ADMIN: Always allowed.
   *   <li>USER: Allowed if creating for themselves.
   *   <li>COACH: Allowed if creating for an athlete, provided the WOD belongs to the Coach's gym.
   * </ul>
   */
  @Transactional(readOnly = true)
  public boolean canCreate(WodScoreRequest request, AldebaranUserPrincipal principal) {
    if (isAdmin(principal)) {
      return true;
    }

    Long targetUserId = request.userId() != null ? request.userId() : principal.getId();

    // Case: User creating for themselves
    if (Objects.equals(targetUserId, principal.getId())) {
      return true;
    }

    // Case: Coach/Staff creating for someone else
    // Must verify that the WOD belongs to the Coach's gym context
    if (isStaff(principal)) {
      return wodRepository
          .findById(request.wodId())
          .map(wod -> Objects.equals(wod.getGymId(), principal.getGymId()))
          .orElse(false);
    }

    return false;
  }

  /**
   * Evaluates if the user can update a specific score.
   *
   * <ul>
   *   <li>ADMIN: Always allowed.
   *   <li>OWNER: Allowed if the score belongs to them.
   *   <li>STAFF: Allowed if the WOD belongs to their gym (Moderation).
   * </ul>
   */
  @Transactional(readOnly = true)
  public boolean canUpdate(Long scoreId, AldebaranUserPrincipal principal) {
    return scoreRepository
        .findById(scoreId)
        .map(score -> checkWriteAccess(score, principal))
        .orElse(false);
  }

  /** Evaluates if the user can delete a specific score. Rules mirror Update rules. */
  @Transactional(readOnly = true)
  public boolean canDelete(Long scoreId, AldebaranUserPrincipal principal) {
    return scoreRepository
        .findById(scoreId)
        .map(score -> checkWriteAccess(score, principal))
        .orElse(false);
  }

  /** Internal logic to verify write access (Update/Delete) against an existing Score entity. */
  private boolean checkWriteAccess(WodScore score, AldebaranUserPrincipal principal) {
    if (isAdmin(principal)) {
      return true;
    }

    // Owner: The user who performed the workout
    if (Objects.equals(score.getUserId(), principal.getId())) {
      return true;
    }

    // Staff (Moderation): Can edit scores linked to WODs of their gym
    if (isStaff(principal)) {
      Wod wod = score.getWod(); // Eagerly loaded in most cases or lazily fetched here
      return Objects.equals(wod.getGymId(), principal.getGymId());
    }

    return false;
  }

  private boolean isAdmin(AldebaranUserPrincipal principal) {
    return ROLE_ADMIN.equals(principal.getRole());
  }

  private boolean isStaff(AldebaranUserPrincipal principal) {
    String role = principal.getRole();
    return ROLE_OWNER.equals(role) || ROLE_PROGRAMMER.equals(role) || ROLE_COACH.equals(role);
  }
}
