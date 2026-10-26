package apex.stellar.aldebaran.security;

import static apex.stellar.aldebaran.security.SecurityService.SCORE_VERIFY;

import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security bean guarding WOD Scores (Participation & Moderation).
 *
 * <p>This component enforces authorization rules for logging and managing scores:
 *
 * <ul>
 *   <li><b>Participation (Self):</b> Athletes can log scores for Public WODs, the WODs of any of
 *       their member Gyms, or their Private WODs.
 *   <li><b>Moderation (Staff):</b> users holding SCORE_VERIFY in the WOD's gym can correct or
 *       delete the scores logged on that gym's WODs (errors, cheating).
 *   <li><b>Logging for someone else:</b> admins only, until Antares can confirm that the target
 *       athlete belongs to the gym (Aldebaran only knows the caller's memberships).
 * </ul>
 *
 * <p><b>Strategy regarding "Not Found":</b> Methods return {@code true} if the resource ID is not
 * found. This delegates the responsibility to the Service layer to throw a proper {@code
 * ResourceNotFoundException} (HTTP 404), rather than triggering a generic {@code
 * AccessDeniedException} (HTTP 403).
 */
@Component("scoreSecurity")
@RequiredArgsConstructor
public class ScoreSecurity {

  private final ScoreRepository scoreRepository;
  private final WodRepository wodRepository;
  private final SecurityService securityService;

  /**
   * Evaluates if the authenticated user is authorized to view a specific score.
   *
   * @param scoreId The unique identifier of the score.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the score is not found (deferring to 404).
   */
  @Transactional(readOnly = true)
  public boolean canView(Long scoreId, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
      return true;
    }

    return scoreRepository
        .findById(scoreId)
        .map(
            score -> {
              // 1. My Score -> Always visible
              if (Objects.equals(score.getUserId(), principal.getId())) {
                return true;
              }

              Wod wod = score.getWod();

              // 2. Public WOD -> Visible to everyone (Global Leaderboard)
              if (wod.isPublic()) {
                return true;
              }

              // 3. Gym WOD -> Visible to all members of that gym (Gym Leaderboard)
              if (wod.getGymId() != null) {
                return principal.isMemberOf(wod.getGymId());
              }

              // 4. Private WOD -> Visible only to the owner (already covered by step 1)
              return false;
            })
        .orElse(true); // Allow service to handle 404
  }

  /**
   * Evaluates if the authenticated user is authorized to create (log) a score. Non-admin users
   * can only log their own scores: Aldebaran cannot verify that another user is a member of the
   * WOD's gym.
   *
   * @param request The score creation payload.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the WOD is not found (deferring to 404).
   */
  public boolean canCreate(ScoreRequest request, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
      return true;
    }

    // Determine intent: Self-logging vs Logging for others
    Long targetUserId = request.userId() != null ? request.userId() : principal.getId();
    boolean isSelf = Objects.equals(targetUserId, principal.getId());

    return wodRepository
        .findById(request.wodId())
        .map(
            wod -> {
              // CASE A: Self-Participation
              if (isSelf) {
                // 1. Public: Open to all
                if (wod.isPublic()) {
                  return true;
                }
                // 2. Gym: Must be a member of that gym
                if (wod.getGymId() != null) {
                  return principal.isMemberOf(wod.getGymId());
                }
                // 3. Private: Author only
                return Objects.equals(wod.getAuthorId(), principal.getId());
              }

              // CASE B: Logging for someone else (admins only, handled above)
              return false;
            })
        .orElse(true); // Allow service to handle 404
  }

  /**
   * Evaluates if the authenticated user is authorized to update OR delete a score.
   *
   * @param scoreId The unique identifier of the score.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the score is not found (deferring to 404).
   */
  @Transactional(readOnly = true)
  public boolean canModify(Long scoreId, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
      return true;
    }

    return scoreRepository
        .findById(scoreId)
        .map(
            score -> canModify(score.getUserId(), score.getWod().getGymId(), principal))
        .orElse(true); // Allow service to handle 404
  }

  /**
   * Evaluates if the authenticated user may update or delete an already loaded score: its athlete,
   * a staff member holding SCORE_VERIFY in the WOD's gym, or an admin.
   *
   * @param score The score to evaluate.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized.
   */
  public boolean canModify(ScoreResponse score, AldebaranUserPrincipal principal) {
    return canModify(score.userId(), score.wodSummary().gymId(), principal);
  }

  private boolean canModify(Long athleteId, Long wodGymId, AldebaranUserPrincipal principal) {
    if (securityService.isAdmin(principal)) {
      return true;
    }
    // 1. Self-Correction: Users can always modify their own scores
    if (Objects.equals(athleteId, principal.getId())) {
      return true;
    }
    // 2. Staff Moderation: Gym WOD + SCORE_VERIFY in that gym
    return securityService.hasGymPermission(principal, wodGymId, SCORE_VERIFY);
  }
}
